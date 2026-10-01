import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;

/**
 * Patches MainMenuAPI's MenuBaseConfig and JukeboxPlayer to support the "onlyPlayInAether" config option.
 *
 * Checks:
 *   - MenuAPI.properties ("onlyPlayInAether=true")
 *   - config/Aether II.cfg ("general { B:onlyPlayInAether=true }")
 *
 * When onlyPlayInAether is true and the player is in-game outside of the Aether dimension (ID 3),
 * JukeboxPlayer suppresses music playback.
 */
public class PatchAetherMusicConfig {
    public static void main(String[] args) throws Exception {
        patchMenuBaseConfig();
        patchMainMenuJukeboxPlayer();
        
        // Also patch Aether legacy copies if present
        patchLegacyJukeboxData();
        patchLegacyJukeboxPlayer();
    }

    // -----------------------------------------------------------------------
    // Patch 1: net/aetherteam/mainmenu_api/MenuBaseConfig
    // -----------------------------------------------------------------------
    static void patchMenuBaseConfig() throws Exception {
        File input = new File("/tmp/cmp_patched/net/aetherteam/mainmenu_api/MenuBaseConfig.class");
        ClassReader cr = new ClassReader(new FileInputStream(input));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Add static boolean field onlyPlayInAether
        boolean hasField = false;
        for (Object f : cn.fields) {
            if (((FieldNode)f).name.equals("onlyPlayInAether")) { hasField = true; break; }
        }
        if (!hasField) {
            FieldNode field = new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "onlyPlayInAether", "Z", null, Boolean.FALSE);
            cn.fields.add(field);
            System.out.println("MenuBaseConfig: added field onlyPlayInAether");
        }

        // Patch loadConfig()
        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("loadConfig") && mn.desc.equals("()V")) {
                System.out.println("MenuBaseConfig: patching loadConfig()...");
                AbstractInsnNode[] insns = mn.instructions.toArray();

                for (int i = insns.length - 1; i >= 0; i--) {
                    if (insns[i].getOpcode() == Opcodes.RETURN) {
                        InsnList inject = new InsnList();
                        LabelNode tryStart = new LabelNode();
                        LabelNode tryEnd   = new LabelNode();
                        LabelNode handler  = new LabelNode();
                        LabelNode after    = new LabelNode();

                        inject.add(tryStart);

                        // String val = menuProps.getProperty("onlyPlayInAether", "false")
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "net/aetherteam/mainmenu_api/MenuBaseConfig", "menuProps", "Ljava/util/Properties;"));
                        inject.add(new LdcInsnNode("onlyPlayInAether"));
                        inject.add(new LdcInsnNode("false"));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            "java/util/Properties", "getProperty",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"));

                        // MenuBaseConfig.onlyPlayInAether = Boolean.parseBoolean(val)
                        inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z"));
                        inject.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                            "net/aetherteam/mainmenu_api/MenuBaseConfig", "onlyPlayInAether", "Z"));

                        // Check Aether.config if available
                        LabelNode skipForgeConfig = new LabelNode();
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "net/aetherteam/aether/Aether", "config", "Lnet/minecraftforge/common/Configuration;"));
                        inject.add(new InsnNode(Opcodes.DUP));
                        inject.add(new JumpInsnNode(Opcodes.IFNULL, skipForgeConfig));
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
                            "net/aetherteam/mainmenu_api/MenuBaseConfig", "onlyPlayInAether", "Z"));
                        inject.add(notSetInForge);
                        inject.add(new InsnNode(Opcodes.POP));
                        inject.add(skipForgeConfig);

                        // System.out.println("[Aether Music Patch] Loaded config option onlyPlayInAether = " + MenuBaseConfig.onlyPlayInAether)
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
                        inject.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
                        inject.add(new InsnNode(Opcodes.DUP));
                        inject.add(new LdcInsnNode("[Aether Music Patch] Loaded config option onlyPlayInAether = "));
                        inject.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V"));
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/aetherteam/mainmenu_api/MenuBaseConfig", "onlyPlayInAether", "Z"));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Z)Ljava/lang/StringBuilder;"));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
                        inject.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V"));

                        inject.add(tryEnd);
                        inject.add(new JumpInsnNode(Opcodes.GOTO, after));

                        inject.add(handler);
                        inject.add(new InsnNode(Opcodes.POP));

                        inject.add(after);

                        mn.tryCatchBlocks.add(new TryCatchBlockNode(tryStart, tryEnd, handler, "java/lang/Exception"));
                        mn.instructions.insertBefore(insns[i], inject);
                        mn.maxStack = Math.max(mn.maxStack, 4);
                        mn.maxLocals = Math.max(mn.maxLocals, 2);
                        System.out.println("  -> Injected onlyPlayInAether read into MenuBaseConfig.loadConfig()");
                        break;
                    }
                }
            }
        }

        writeClass(cn, "/tmp/music_patch_out/net/aetherteam/mainmenu_api/MenuBaseConfig.class");
    }

    // -----------------------------------------------------------------------
    // Patch 2: net/aetherteam/mainmenu_api/JukeboxPlayer
    // -----------------------------------------------------------------------
    static void patchMainMenuJukeboxPlayer() throws Exception {
        File input = new File("/tmp/cmp_patched/net/aetherteam/mainmenu_api/JukeboxPlayer.class");
        ClassReader cr = new ClassReader(new FileInputStream(input));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("run") && mn.desc.equals("()V")) {
                System.out.println("MainMenuAPI JukeboxPlayer: patching run()...");

                LabelNode skipLabel = new LabelNode();

                InsnList inject = new InsnList();

                // if (!MenuBaseConfig.onlyPlayInAether) goto skipLabel
                inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/mainmenu_api/MenuBaseConfig", "onlyPlayInAether", "Z"));
                inject.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));

                // Minecraft mc = Minecraft.getMinecraft()  [static method x()]
                inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/minecraft/client/Minecraft", "x", "()Lnet/minecraft/client/Minecraft;"));
                inject.add(new FieldInsnNode(Opcodes.GETFIELD,
                    "net/minecraft/client/Minecraft", "g", "Lbdv;"));
                LabelNode noPlayer = new LabelNode();

                // if (thePlayer == null) goto skipLabel
                inject.add(new InsnNode(Opcodes.DUP));
                inject.add(new JumpInsnNode(Opcodes.IFNULL, noPlayer));

                // thePlayer.q = worldObj (aab)
                inject.add(new FieldInsnNode(Opcodes.GETFIELD, "bdv", "q", "Laab;"));
                inject.add(new InsnNode(Opcodes.DUP));
                LabelNode noWorld = new LabelNode();
                inject.add(new JumpInsnNode(Opcodes.IFNULL, noWorld));

                // world.r = dimensionId (int)
                inject.add(new FieldInsnNode(Opcodes.GETFIELD, "aab", "r", "I"));

                // if (dimensionId == 3) goto skipLabel (we're in Aether)
                LabelNode suppressLabel = new LabelNode();
                inject.add(new IntInsnNode(Opcodes.BIPUSH, 3));
                inject.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipLabel));
                inject.add(new JumpInsnNode(Opcodes.GOTO, suppressLabel));

                // noWorld
                inject.add(noWorld);
                inject.add(new InsnNode(Opcodes.POP));
                inject.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));

                // noPlayer
                inject.add(noPlayer);
                inject.add(new InsnNode(Opcodes.POP));
                inject.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));

                // suppressLabel: reset musicInterval to 0 and return
                inject.add(suppressLabel);
                inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                inject.add(new InsnNode(Opcodes.ICONST_0));
                inject.add(new FieldInsnNode(Opcodes.PUTFIELD,
                    "net/aetherteam/mainmenu_api/JukeboxPlayer", "musicInterval", "I"));
                inject.add(new InsnNode(Opcodes.RETURN));

                inject.add(skipLabel);

                mn.instructions.insert(inject);
                mn.maxStack = Math.max(mn.maxStack, 3);
                System.out.println("  -> Injected dimension check into MainMenuAPI JukeboxPlayer.run()");
                break;
            }
        }

        writeClass(cn, "/tmp/music_patch_out/net/aetherteam/mainmenu_api/JukeboxPlayer.class");
    }

    // -----------------------------------------------------------------------
    // Legacy patches (net/aetherteam/aether/sound)
    // -----------------------------------------------------------------------
    static void patchLegacyJukeboxData() throws Exception {
        File input = new File("/tmp/cmp_patched/net/aetherteam/aether/sound/JukeboxData.class");
        if (!input.exists()) return;
        ClassReader cr = new ClassReader(new FileInputStream(input));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean hasField = false;
        for (Object f : cn.fields) {
            if (((FieldNode)f).name.equals("onlyPlayInAether")) { hasField = true; break; }
        }
        if (!hasField) {
            FieldNode field = new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "onlyPlayInAether", "Z", null, Boolean.FALSE);
            cn.fields.add(field);
        }

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("loadConfig") && mn.desc.equals("()V")) {
                AbstractInsnNode[] insns = mn.instructions.toArray();
                for (int i = insns.length - 1; i >= 0; i--) {
                    if (insns[i].getOpcode() == Opcodes.RETURN) {
                        InsnList inject = new InsnList();
                        inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                            "net/aetherteam/mainmenu_api/MenuBaseConfig", "onlyPlayInAether", "Z"));
                        inject.add(new FieldInsnNode(Opcodes.PUTSTATIC,
                            "net/aetherteam/aether/sound/JukeboxData", "onlyPlayInAether", "Z"));
                        mn.instructions.insertBefore(insns[i], inject);
                        break;
                    }
                }
            }
        }

        writeClass(cn, "/tmp/music_patch_out/net/aetherteam/aether/sound/JukeboxData.class");
    }

    static void patchLegacyJukeboxPlayer() throws Exception {
        File input = new File("/tmp/cmp_patched/net/aetherteam/aether/sound/JukeboxPlayer.class");
        if (!input.exists()) return;
        ClassReader cr = new ClassReader(new FileInputStream(input));
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("run") && mn.desc.equals("()V")) {
                LabelNode skipLabel = new LabelNode();
                InsnList inject = new InsnList();
                inject.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/mainmenu_api/MenuBaseConfig", "onlyPlayInAether", "Z"));
                inject.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
                inject.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/minecraft/client/Minecraft", "x", "()Lnet/minecraft/client/Minecraft;"));
                inject.add(new FieldInsnNode(Opcodes.GETFIELD,
                    "net/minecraft/client/Minecraft", "g", "Lbdv;"));
                LabelNode noPlayer = new LabelNode();
                inject.add(new InsnNode(Opcodes.DUP));
                inject.add(new JumpInsnNode(Opcodes.IFNULL, noPlayer));
                inject.add(new FieldInsnNode(Opcodes.GETFIELD, "bdv", "q", "Laab;"));
                inject.add(new InsnNode(Opcodes.DUP));
                LabelNode noWorld = new LabelNode();
                inject.add(new JumpInsnNode(Opcodes.IFNULL, noWorld));
                inject.add(new FieldInsnNode(Opcodes.GETFIELD, "aab", "r", "I"));
                LabelNode suppressLabel = new LabelNode();
                inject.add(new IntInsnNode(Opcodes.BIPUSH, 3));
                inject.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, skipLabel));
                inject.add(new JumpInsnNode(Opcodes.GOTO, suppressLabel));
                inject.add(noWorld);
                inject.add(new InsnNode(Opcodes.POP));
                inject.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));
                inject.add(noPlayer);
                inject.add(new InsnNode(Opcodes.POP));
                inject.add(new JumpInsnNode(Opcodes.GOTO, skipLabel));
                inject.add(suppressLabel);
                inject.add(new VarInsnNode(Opcodes.ALOAD, 0));
                inject.add(new InsnNode(Opcodes.ICONST_0));
                inject.add(new FieldInsnNode(Opcodes.PUTFIELD,
                    "net/aetherteam/aether/sound/JukeboxPlayer", "musicInterval", "I"));
                inject.add(new InsnNode(Opcodes.RETURN));
                inject.add(skipLabel);
                mn.instructions.insert(inject);
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
