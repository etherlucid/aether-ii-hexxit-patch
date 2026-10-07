import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Patches Aether II classes for Hexxit Remix:
 * 1. SlotMoreArmor: context-aware accessory background icon display.
 * 2. RenderPlayerBaseAether: Smart Moving animation sync, cape rotation, lighting, depth mask fixes, and accessory color tinting.
 * 3. GuiInventoryAether: extends GuiInventory (azg) for proper inventory preview handling.
 */
public class PatchAetherPlayerAccess {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: PatchAetherPlayerAccess <input_jar> <output_jar>");
            System.exit(1);
        }

        File inputJar = new File(args[0]);
        File outputJar = new File(args[1]);

        File tempJar = new File(outputJar.getAbsolutePath() + ".tmp");

        ZipFile zipFile = new ZipFile(inputJar);
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempJar));

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            InputStream is = zipFile.getInputStream(entry);
            byte[] bytes = readAllBytes(is);
            is.close();

            if ("net/aetherteam/aether/containers/SlotMoreArmor.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/containers/SlotMoreArmor.class...");
                bytes = patchSlotMoreArmor(bytes);
            } else if ("net/aetherteam/aether/client/RenderPlayerBaseAether.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/client/RenderPlayerBaseAether.class...");
                bytes = patchRenderPlayerBaseAether(bytes);
            } else if ("net/aetherteam/aether/client/gui/GuiInventoryAether.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/client/gui/GuiInventoryAether.class...");
                bytes = patchGuiInventoryAether(bytes);
            } else if ("net/aetherteam/aether/client/ClientTickHandler.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/client/ClientTickHandler.class...");
                bytes = patchClientTickHandler(bytes);
            }

            ZipEntry newEntry = new ZipEntry(name);
            zos.putNextEntry(newEntry);
            zos.write(bytes);
            zos.closeEntry();
        }

        zipFile.close();
        zos.close();

        if (outputJar.exists()) {
            outputJar.delete();
        }
        if (!tempJar.renameTo(outputJar)) {
            // Fallback copy if rename fails across filesystems
            copyFile(tempJar, outputJar);
            tempJar.delete();
        }

        System.out.println(">>> PatchAetherPlayerAccess successfully applied to " + outputJar.getPath());
    }

    private static byte[] patchSlotMoreArmor(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (("b".equals(mn.name) || "getBackgroundIconIndex".equals(mn.name)) &&
                ("()Llx;".equals(mn.desc) || "()Lnet/minecraft/util/Icon;".equals(mn.desc))) {
                System.out.println("  Found SlotMoreArmor." + mn.name + mn.desc + ": inserting context-aware icon check");

                LabelNode lContinue = new LabelNode();
                InsnList prefix = new InsnList();

                // if (!AetherClientHelper.shouldShowAccessoryIcons()) return null;
                prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/aetherteam/aether/client/AetherClientHelper",
                    "shouldShowAccessoryIcons",
                    "()Z"));
                prefix.add(new JumpInsnNode(Opcodes.IFNE, lContinue));
                prefix.add(new InsnNode(Opcodes.ACONST_NULL));
                prefix.add(new InsnNode(Opcodes.ARETURN));
                prefix.add(lContinue);

                mn.instructions.insertBefore(mn.instructions.getFirst(), prefix);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchRenderPlayerBaseAether(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;

            // 1. Pass actual yaw (fload 8) instead of ldc -90.0F to renderPlayer.a
            // This enables Smart Moving to recognize GUI previews (yaw == 0.0F && partialTicks == 1.0F)
            // so the player model does not get turned around in the inventory GUI.
            if ("a".equals(mn.name) || "renderPlayer".equals(mn.name)) {
                if (mn.desc != null && (mn.desc.contains("DDDFF)") || mn.desc.startsWith("(Lsq;DDDFF)"))) {
                    for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn.getOpcode() == Opcodes.LDC) {
                            LdcInsnNode ldc = (LdcInsnNode) insn;
                            if (ldc.cst instanceof Float && Math.abs(((Float) ldc.cst) - (-90.0F)) < 0.01F) {
                                System.out.println("  Found RenderPlayerBaseAether.a ldc -90.0F: replacing with fload 8 (actual yaw)");
                                mn.instructions.set(insn, new VarInsnNode(Opcodes.FLOAD, 8));
                                break;
                            }
                        }
                    }
                }
            }

            // 2. Rewrite a(sq, float) [renderSpecials] to clean cape render sequence
            // Eliminates buggy unlit opaque duplicate cape pass 2 and glDepthMask(false) leak
            if (("a".equals(mn.name) || "renderSpecials".equals(mn.name)) && "(Lsq;F)V".equals(mn.desc)) {
                System.out.println("  Found RenderPlayerBaseAether.a(sq, float): replacing with clean cape render sequence");
                InsnList il = new InsnList();

                // super.a(player, partialTicks);
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.FLOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                    "net/aetherteam/playercore_api/cores/PlayerCoreRender",
                    "a",
                    "(Lsq;F)V"));

                // AetherClientHelper.beforeRenderCape(player, partialTicks);
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.FLOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/aetherteam/aether/client/AetherClientHelper",
                    "beforeRenderCape",
                    "(Ljava/lang/Object;F)V"));

                // this.renderCape(player, partialTicks);
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new VarInsnNode(Opcodes.FLOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "net/aetherteam/aether/client/RenderPlayerBaseAether",
                    "renderCape",
                    "(Lsq;F)V"));

                // AetherClientHelper.afterRenderCape(partialTicks);
                il.add(new VarInsnNode(Opcodes.FLOAD, 2));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/aetherteam/aether/client/AetherClientHelper",
                    "afterRenderCape",
                    "(F)V"));

                il.add(new InsnNode(Opcodes.RETURN));

                mn.instructions = il;
                mn.tryCatchBlocks.clear();
            }

            // 3. Fix glDepthMask(false) bug in renderParachute and renderFirstPersonGlow
            if ("renderParachute".equals(mn.name) || "renderFirstPersonGlow".equals(mn.name)) {
                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("glDepthMask".equals(min.name) && "(Z)V".equals(min.desc)) {
                            AbstractInsnNode prev = insn.getPrevious();
                            if (prev != null && prev.getOpcode() == Opcodes.ICONST_0) {
                                System.out.println("  Found glDepthMask(false) in " + mn.name + ": changing to glDepthMask(true)");
                                mn.instructions.set(prev, new InsnNode(Opcodes.ICONST_1));
                            }
                        }
                    }
                }
            }

            // 4. Inject prepareAccessoryColor after all texture bindings in renderCape, renderParachute, renderFirstPersonGloves
            if ("renderCape".equals(mn.name) || "renderParachute".equals(mn.name) || "renderFirstPersonGloves".equals(mn.name)) {
                AbstractInsnNode insn = mn.instructions.getFirst();
                while (insn != null) {
                    AbstractInsnNode next = insn.getNext();
                    if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("(Ljava/lang/String;)V".equals(min.desc) &&
                            "net/aetherteam/playercore_api/cores/PlayerCoreRender".equals(min.owner)) {
                            System.out.println("  Found PlayerCoreRender.a(String) in " + mn.name + ": injecting prepareAccessoryColor call");
                            InsnList colorList = new InsnList();
                            colorList.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "prepareAccessoryColor",
                                "()V"
                            ));
                            mn.instructions.insert(insn, colorList);
                        }
                    }
                    insn = next;
                }
            }

            // 4b. In renderCape: inject applyCapeRotation after GL11.glPushMatrix()
            if ("renderCape".equals(mn.name)) {
                for (AbstractInsnNode insn = mn.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("glPushMatrix".equals(min.name) && "()V".equals(min.desc) &&
                            ("org/lwjgl/opengl/GL11".equals(min.owner) || min.owner.endsWith("GL11"))) {
                            System.out.println("  Found GL11.glPushMatrix in renderCape: injecting AetherClientHelper.applyCapeRotation call");
                            InsnList capeList = new InsnList();
                            capeList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            capeList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            capeList.add(new VarInsnNode(Opcodes.FLOAD, 2));
                            capeList.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "applyCapeRotation",
                                "(Ljava/lang/Object;Ljava/lang/Object;F)V"
                            ));
                            mn.instructions.insert(insn, capeList);
                            break;
                        }
                    }
                }
            }

            // 5. In doRenderMisc: redirect rotateCorpse, inject syncModel, and inject getAccessoryColor
            if ("doRenderMisc".equals(mn.name)) {
                AbstractInsnNode insn = mn.instructions.getFirst();
                while (insn != null) {
                    AbstractInsnNode next = insn.getNext();
                    if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if (("a".equals(min.name) || "rotateCorpse".equals(min.name)) &&
                            min.desc.endsWith(";FFF)V")) {
                            System.out.println("  Found RenderPlayerBaseAether.doRenderMisc call to rotateCorpse: redirecting to AetherClientHelper.rotateCorpseDirect");
                            MethodInsnNode redirect = new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "rotateCorpseDirect",
                                "(Ljava/lang/Object;Ljava/lang/Object;FFF)V"
                            );
                            mn.instructions.set(insn, redirect);
                        } else if (("a".equals(min.name) || "setRotationAngles".equals(min.name)) &&
                                   "(FFFFFFLmp;)V".equals(min.desc) &&
                                   ("bbz".equals(min.owner) || "net/minecraft/client/model/ModelBiped".equals(min.owner))) {
                            System.out.println("  Found modelMisc.setRotationAngles: injecting AetherClientHelper.beforeRenderAccessories call");
                            InsnList syncList = new InsnList();
                            syncList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            syncList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            syncList.add(new VarInsnNode(Opcodes.FLOAD, 9));
                            syncList.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "beforeRenderAccessories",
                                "(Ljava/lang/Object;Ljava/lang/Object;F)V"
                            ));
                            mn.instructions.insert(insn, syncList);
                        } else if ("(Ljava/lang/String;)V".equals(min.desc) &&
                                   "net/aetherteam/playercore_api/cores/PlayerCoreRender".equals(min.owner)) {
                            System.out.println("  Found PlayerCoreRender.a(String) in doRenderMisc: injecting prepareAccessoryColor call");
                            InsnList colorList = new InsnList();
                            colorList.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "prepareAccessoryColor",
                                "()V"
                            ));
                            mn.instructions.insert(insn, colorList);
                        } else if ("a".equals(min.name) && "(Lwm;I)I".equals(min.desc) &&
                                   (min.owner.contains("ItemAccessory") || "wk".equals(min.owner))) {
                            System.out.println("  Found ItemAccessory.a in doRenderMisc: injecting getAccessoryColor call");
                            InsnList colorHook = new InsnList();
                            colorHook.add(new VarInsnNode(Opcodes.ALOAD, 18));
                            colorHook.add(new InsnNode(Opcodes.SWAP));
                            colorHook.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "getAccessoryColor",
                                "(Ljava/lang/Object;I)I"
                            ));
                            mn.instructions.insert(insn, colorHook);
                        }
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        if (mn.instructions.indexOf(insn) > 50) {
                            System.out.println("  Found final return in doRenderMisc: injecting afterRenderAccessories call");
                            InsnList afterList = new InsnList();
                            afterList.add(new VarInsnNode(Opcodes.FLOAD, 9));
                            afterList.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "net/aetherteam/aether/client/AetherClientHelper",
                                "afterRenderAccessories",
                                "(F)V"
                            ));
                            mn.instructions.insertBefore(insn, afterList);
                        }
                    }
                    insn = next;
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchGuiInventoryAether(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        // Change superclass from azb (GuiContainer) to azg (GuiInventory)
        // so that Smart Moving and other mods recognize GuiInventoryAether as an inventory GUI.
        System.out.println("  Changing GuiInventoryAether superclass from " + cn.superName + " to azg (GuiInventory)");
        cn.superName = "azg";

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if ("<init>".equals(mn.name) && "(Lsq;)V".equals(mn.desc)) {
                System.out.println("  Found GuiInventoryAether.<init>(sq): rewriting to invoke azg.<init>(sq)");
                InsnList newInit = new InsnList();
                newInit.add(new VarInsnNode(Opcodes.ALOAD, 0));
                newInit.add(new VarInsnNode(Opcodes.ALOAD, 1));
                newInit.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "azg", "<init>", "(Lsq;)V"));
                newInit.add(new VarInsnNode(Opcodes.ALOAD, 1));
                newInit.add(new FieldInsnNode(Opcodes.PUTSTATIC, "net/aetherteam/aether/client/gui/GuiInventoryAether", "player", "Lsq;"));
                newInit.add(new InsnNode(Opcodes.RETURN));

                mn.instructions.clear();
                mn.instructions.add(newInit);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Throwable t) {
                    return "java/lang/Object";
                }
            }
        };
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] patchClientTickHandler(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if ("tickEnd".equals(mn.name) || "tick".equals(mn.name) || mn.desc.contains("TickType")) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) insn;
                        if ("azg".equals(ldc.cst) || "net.minecraft.client.gui.inventory.GuiInventory".equals(ldc.cst)) {
                            System.out.println("  Found LDC " + ldc.cst + " in ClientTickHandler at insn " + i);
                            int ifnullIdx = -1;
                            int getClassIdx = -1;
                            int equalsIdx = -1;
                            int ifeqIdx = -1;

                            for (int k = i - 1; k >= Math.max(0, i - 10); k--) {
                                AbstractInsnNode prev = mn.instructions.get(k);
                                if (prev instanceof MethodInsnNode && "getClass".equals(((MethodInsnNode) prev).name)) {
                                    getClassIdx = k;
                                }
                                if (prev instanceof JumpInsnNode && prev.getOpcode() == Opcodes.IFNULL) {
                                    ifnullIdx = k;
                                }
                            }
                            for (int j = i + 1; j < Math.min(i + 10, mn.instructions.size()); j++) {
                                AbstractInsnNode nextInsn = mn.instructions.get(j);
                                if (nextInsn instanceof MethodInsnNode && "equals".equals(((MethodInsnNode) nextInsn).name)) {
                                    equalsIdx = j;
                                }
                                if (equalsIdx != -1 && nextInsn instanceof JumpInsnNode && nextInsn.getOpcode() == Opcodes.IFEQ) {
                                    ifeqIdx = j;
                                    break;
                                }
                            }

                            if (ifnullIdx != -1 && ifeqIdx != -1) {
                                System.out.println("  Patching ClientTickHandler instruction range " + ifnullIdx + " to " + ifeqIdx);
                                JumpInsnNode originalIfeq = (JumpInsnNode) mn.instructions.get(ifeqIdx);
                                LabelNode targetLabel = originalIfeq.label;

                                mn.instructions.set(mn.instructions.get(ifnullIdx), new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "AetherInventoryAdapter",
                                    "shouldReplaceSurvivalGui",
                                    "(Ljava/lang/Object;)Z"
                                ));
                                mn.instructions.set(mn.instructions.get(ifnullIdx + 1), new JumpInsnNode(Opcodes.IFEQ, targetLabel));

                                for (int idx = ifnullIdx + 2; idx <= ifeqIdx; idx++) {
                                    mn.instructions.set(mn.instructions.get(idx), new InsnNode(Opcodes.NOP));
                                }
                                System.out.println("  Successfully patched ClientTickHandler!");
                                break;
                            }
                        }
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }

    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream fis = new FileInputStream(src);
        FileOutputStream fos = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int r;
        while ((r = fis.read(buf)) != -1) {
            fos.write(buf, 0, r);
        }
        fis.close();
        fos.close();
    }
}
