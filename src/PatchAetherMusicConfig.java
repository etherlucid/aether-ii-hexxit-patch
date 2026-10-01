import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;

/**
 * Patches JukeboxPlayer.run() to skip music playback when:
 *   - JukeboxData.onlyPlayInAether == true  (new flag)
 *   - AND the player's current dimension is not the Aether (dimension ID 3)
 *
 * Also patches JukeboxData's static initializer to load the new flag
 * from the "Aether II.cfg" ForgeConfig file.
 */
public class PatchAetherMusicConfig {
    public static void main(String[] args) throws Exception {
        patchJukeboxData();
        patchJukeboxPlayer();
    }

    // -----------------------------------------------------------------------
    // Patch 1: JukeboxData — add static boolean onlyPlayInAether
    //          and load it from Aether II.cfg in loadConfig()
    // -----------------------------------------------------------------------
    static void patchJukeboxData() throws Exception {
        File input = new File("/tmp/cmp_patched/net/aetherteam/aether/sound/JukeboxData.class");
        ClassReader cr = new ClassReader(new FileInputStream(input));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Add the new static boolean field
        boolean hasField = false;
        for (Object f : cn.fields) {
            if (((FieldNode)f).name.equals("onlyPlayInAether")) { hasField = true; break; }
        }
        if (!hasField) {
            FieldNode field = new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "onlyPlayInAether", "Z", null, Boolean.FALSE);
            cn.fields.add(field);
            System.out.println("JukeboxData: added field onlyPlayInAether");
        }

        // Patch loadConfig() — append logic to read "onlyPlayInAether" from Aether II.cfg
        // We find the method and append before its RETURN instruction.
        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("loadConfig") && mn.desc.equals("()V")) {
                System.out.println("JukeboxData: patching loadConfig()...");
                AbstractInsnNode[] insns = mn.instructions.toArray();

                // Find the last RETURN and insert our config-reading code before it
                for (int i = insns.length - 1; i >= 0; i--) {
                    if (insns[i].getOpcode() == Opcodes.RETURN) {
                        /*
                         * Inject (pseudo-Java):
                         *   try {
                         *     String val = menuProps.getProperty("onlyPlayInAether", "false");
                         *     JukeboxData.onlyPlayInAether = Boolean.parseBoolean(val);
                         *   } catch (Exception e) { }
                         */
                        InsnList inject = new InsnList();
                        LabelNode tryStart = new LabelNode();
                        LabelNode tryEnd   = new LabelNode();
                        LabelNode handler  = new LabelNode();
                        LabelNode after    = new LabelNode();

                        inject.add(tryStart);

                        // String val = menuProps.getProperty("onlyPlayInAether", "false")
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "net/aetherteam/aether/sound/JukeboxData", "menuProps", "Ljava/util/Properties;"));
                        inject.add(new LdcInsnNode("onlyPlayInAether"));
                        inject.add(new LdcInsnNode("false"));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            "java/util/Properties", "getProperty",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));

                        // JukeboxData.onlyPlayInAether = Boolean.parseBoolean(val)
                        inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z"));
                        inject.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                            "net/aetherteam/aether/sound/JukeboxData", "onlyPlayInAether", "Z"));

                        // Also check Aether.config (Aether II.cfg under "general" category) if available
                        LabelNode skipForgeConfig = new LabelNode();
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "net/aetherteam/aether/Aether", "config", "Lnet/minecraftforge/common/Configuration;"));
                        inject.add(new InsnNode(Opcodes.DUP));
                        inject.add(new JumpInsnNode(Opcodes.IFNULL, skipForgeConfig));
                        // Configuration.get("general", "onlyPlayInAether", false)
                        inject.add(new LdcInsnNode("general"));
                        inject.add(new LdcInsnNode("onlyPlayInAether"));
                        inject.add(new InsnNode(Opcodes.ICONST_0));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            "net/minecraftforge/common/Configuration", "get",
                            "(Ljava/lang/String;Ljava/lang/String;Z)Lnet/minecraftforge/common/Property;"));
                        inject.add(new InsnNode(Opcodes.ICONST_0));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            "net/minecraftforge/common/Property", "getBoolean", "(Z)Z"));
                        LabelNode notSetInForge = new LabelNode();
                        inject.add(new JumpInsnNode(Opcodes.IFEQ, notSetInForge));
                        inject.add(new InsnNode(Opcodes.ICONST_1));
                        inject.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                            "net/aetherteam/aether/sound/JukeboxData", "onlyPlayInAether", "Z"));
                        inject.add(notSetInForge);
                        inject.add(new InsnNode(Opcodes.POP)); // pop configuration if not null
                        inject.add(skipForgeConfig);

                        inject.add(tryEnd);
                        inject.add(new JumpInsnNode(Opcodes.GOTO, after));

                        // catch (Exception e) { /* ignore */ }
                        inject.add(handler);
                        inject.add(new InsnNode(Opcodes.POP));

                        inject.add(after);

                        mn.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, handler, "java/lang/Exception"));
                        mn.instructions.insertBefore(insns[i], inject);
                        mn.maxStack = Math.max(mn.maxStack, 3);
                        mn.maxLocals = Math.max(mn.maxLocals, 2);
                        System.out.println("  -> Injected onlyPlayInAether read into loadConfig()");
                        break;
                    }
                }
            }

            // Also patch setProperty() so saving "onlyPlayInAether" is persisted
            // (the existing setProperty writes all menuProps back to file, so
            // no extra change needed — we just need to call setProperty when toggling)
        }

        writeClass(cn, "/tmp/music_patch_out/net/aetherteam/aether/sound/JukeboxData.class");
    }

    // -----------------------------------------------------------------------
    // Patch 2: JukeboxPlayer.run() — at the very top, add:
    //   if (JukeboxData.onlyPlayInAether) {
    //     Minecraft mc = Minecraft.getMinecraft();
    //     if (mc.theWorld != null && mc.theWorld.provider.dimensionId != 3) {
    //       musicInterval = 0;
    //       return;
    //     }
    //   }
    // -----------------------------------------------------------------------
    static void patchJukeboxPlayer() throws Exception {
        File input = new File("/tmp/cmp_patched/net/aetherteam/aether/sound/JukeboxPlayer.class");
        ClassReader cr = new ClassReader(new FileInputStream(input));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("run") && mn.desc.equals("()V")) {
                System.out.println("JukeboxPlayer: patching run()...");

                LabelNode skipLabel = new LabelNode(); // jump here if we should NOT suppress music

                InsnList inject = new InsnList();

                // if (!JukeboxData.onlyPlayInAether) goto skipLabel
                inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/sound/JukeboxData", "onlyPlayInAether", "Z"));
                inject.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));

                // Minecraft mc = Minecraft.getMinecraft()  [static method x()]
                inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/minecraft/client/Minecraft", "x", "()Lnet/minecraft/client/Minecraft;"));
                // mc.g = thePlayer (bdv = EntityClientPlayerMP)
                inject.add(new FieldInsnNode(Opcodes.GETFIELD,
                    "net/minecraft/client/Minecraft", "g", "Lbdv;"));
                LabelNode noPlayer = new LabelNode();

                // if (thePlayer == null) goto skipLabel (not in-game yet)
                inject.add(new InsnNode(Opcodes.DUP));
                inject.add(new JumpInsnNode(Opcodes.IFNULL, noPlayer));

                // thePlayer.q = worldObj (aab = World)
                inject.add(new FieldInsnNode(Opcodes.GETFIELD, "bdv", "q", "Laab;"));
                inject.add(new InsnNode(Opcodes.DUP));
                LabelNode noWorld = new LabelNode();
                inject.add(new JumpInsnNode(Opcodes.IFNULL, noWorld));

                // world.r = dimensionId (int)
                inject.add(new FieldInsnNode(Opcodes.GETFIELD, "aab", "r", "I"));

                // if (dimensionId == 3) goto skipLabel (we're in Aether, play normally)
                LabelNode suppressLabel = new LabelNode();
                inject.add(new IntInsnNode(Opcodes.BIPUSH, 3));
                inject.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipLabel));
                inject.add(new JumpInsnNode(Opcodes.GOTO, suppressLabel));

                // noWorld: pop null world ref, fall through to suppress
                inject.add(noWorld);
                inject.add(new InsnNode(Opcodes.POP));
                inject.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));

                // noPlayer: pop null player ref, skip suppression (not in-game)
                inject.add(noPlayer);
                inject.add(new InsnNode(Opcodes.POP));
                inject.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));

                // suppressLabel: not in Aether -> reset interval and return
                inject.add(suppressLabel);
                inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                inject.add(new InsnNode(Opcodes.ICONST_0));
                inject.add(new FieldInsnNode(Opcodes.PUTFIELD,
                    "net/aetherteam/aether/sound/JukeboxPlayer", "musicInterval", "I"));
                inject.add(new InsnNode(Opcodes.RETURN));

                inject.add(skipLabel);

                mn.instructions.insert(inject);
                mn.maxStack = Math.max(mn.maxStack, 3);
                System.out.println("  -> Injected dimension check into run()");
                break;
            }
        }

        writeClass(cn, "/tmp/music_patch_out/net/aetherteam/aether/sound/JukeboxPlayer.class");
    }

    static void writeClass(ClassNode cn, String outputPath) throws Exception {
        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        File out = new File(outputPath);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(cw.toByteArray());
        }
        System.out.println("Written: " + outputPath);
    }
}
