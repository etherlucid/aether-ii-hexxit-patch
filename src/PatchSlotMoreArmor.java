import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;

/**
 * Restores SlotMoreArmor.b() (getBackgroundIconIndex) to return the correct
 * IIcon for each accessory slot type.
 *
 * This was previously patched to return null (as part of removing icons from
 * the Creative Survival Inventory), but SlotMoreArmor is shared with the normal
 * Aether Survival Inventory where the icons should display. The Creative inventory
 * uses SlotAetherCreativeInventory (which correctly returns null) — so
 * SlotMoreArmor.b() can safely be restored without affecting the creative view.
 *
 * Original b() logic (armorType -> IIcon):
 *   4  -> ItemAccessory.pendantSlot
 *   5  -> ItemAccessory.capeSlot
 *   6  -> ItemAccessory.shieldSlot
 *   7  -> ItemAccessory.miscSlot
 *   8  -> ItemAccessory.ringSlot
 *   9  -> ItemAccessory.ringSlot
 *   10 -> ItemAccessory.gloveSlot
 *   11 -> ItemAccessory.miscSlot
 *   default -> super.b()
 */
public class PatchSlotMoreArmor {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java PatchSlotMoreArmor <input_jar> <output_jar>");
            System.exit(1);
        }

        File inputJar  = new File(args[0]);
        File outputJar = new File(args[1]);

        System.out.println("=== Restoring SlotMoreArmor accessory slot icons ===");

        File tmpDir = new File("/tmp/slotarmor_patch_work");
        deleteDir(tmpDir);
        tmpDir.mkdirs();

        unzip(inputJar, tmpDir);

        File slotFile = new File(tmpDir, "net/aetherteam/aether/containers/SlotMoreArmor.class");
        if (!slotFile.exists()) {
            System.err.println("SlotMoreArmor.class not found in JAR — skipping.");
        } else {
            patchSlotMoreArmor(slotFile);
        }

        zip(tmpDir, outputJar);
        System.out.println("Done: " + outputJar.getAbsolutePath());
    }

    private static void patchSlotMoreArmor(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (MethodNode mn : cn.methods) {
            // b() is getBackgroundIconIndex — returns Llx; (IIcon)
            if (mn.name.equals("b") && mn.desc.equals("()Llx;")) {
                System.out.println("[SlotMoreArmor] Restoring b() (getBackgroundIconIndex)...");

                // Check if it currently returns null (the broken patch)
                boolean returnsNull = false;
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == Opcodes.ACONST_NULL) {
                        returnsNull = true;
                        break;
                    }
                }
                if (!returnsNull) {
                    System.out.println("  -> b() does not return null — already correct, skipping.");
                    return;
                }

                /*
                 * Rebuild the original tableswitch:
                 *
                 * switch (this.armorType) {
                 *   case 4:  return ItemAccessory.pendantSlot;
                 *   case 5:  return ItemAccessory.capeSlot;
                 *   case 6:  return ItemAccessory.shieldSlot;
                 *   case 7:  return ItemAccessory.miscSlot;
                 *   case 8:  return ItemAccessory.ringSlot;
                 *   case 9:  return ItemAccessory.ringSlot;
                 *   case 10: return ItemAccessory.gloveSlot;
                 *   case 11: return ItemAccessory.miscSlot;
                 *   default: return super.b();
                 * }
                 */
                InsnList insns = new InsnList();

                // Load this.armorType
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new FieldInsnNode(Opcodes.GETFIELD,
                    "net/aetherteam/aether/containers/SlotMoreArmor",
                    "armorType",
                    "I"));

                // Case labels
                LabelNode lCase4  = new LabelNode();
                LabelNode lCase5  = new LabelNode();
                LabelNode lCase6  = new LabelNode();
                LabelNode lCase7  = new LabelNode();
                LabelNode lCase8  = new LabelNode();
                LabelNode lCase9  = new LabelNode();
                LabelNode lCase10 = new LabelNode();
                LabelNode lCase11 = new LabelNode();
                LabelNode lDefault = new LabelNode();

                insns.add(new TableSwitchInsnNode(4, 11,
                    lDefault,
                    new LabelNode[]{ lCase4, lCase5, lCase6, lCase7, lCase8, lCase9, lCase10, lCase11 }
                ));

                // case 4: pendantSlot
                insns.add(lCase4);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "pendantSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 5: capeSlot
                insns.add(lCase5);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "capeSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 6: shieldSlot
                insns.add(lCase6);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "shieldSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 7: miscSlot
                insns.add(lCase7);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "miscSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 8: ringSlot
                insns.add(lCase8);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "ringSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 9: ringSlot
                insns.add(lCase9);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "ringSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 10: gloveSlot
                insns.add(lCase10);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "gloveSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // case 11: miscSlot
                insns.add(lCase11);
                insns.add(new FieldInsnNode(Opcodes.GETSTATIC,
                    "net/aetherteam/aether/items/ItemAccessory",
                    "miscSlot",
                    "Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                // default: super.b()
                insns.add(lDefault);
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                    "ul", // obfuscated Slot superclass
                    "b",
                    "()Llx;"));
                insns.add(new InsnNode(Opcodes.ARETURN));

                mn.instructions = insns;
                if (mn.localVariables != null) mn.localVariables.clear();
                if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
                System.out.println("  -> Restored b() with full armorType tableswitch (pendant/cape/shield/misc/ring/glove)");
                break;
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return "java/lang/Object";
            }
        };
        cn.accept(cw);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(cw.toByteArray());
        fos.close();
    }

    private static void deleteDir(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDir(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    private static void unzip(File zipFile, File destDir) throws Exception {
        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(zipFile));
        java.util.zip.ZipEntry entry;
        byte[] buffer = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            File newFile = new File(destDir, entry.getName());
            if (entry.isDirectory()) {
                newFile.mkdirs();
            } else {
                newFile.getParentFile().mkdirs();
                FileOutputStream fos = new FileOutputStream(newFile);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }
            zis.closeEntry();
        }
        zis.close();
    }

    private static void zip(File srcDir, File zipFile) throws Exception {
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFile));
        byte[] buffer = new byte[8192];
        zipSub(srcDir, srcDir, zos, buffer);
        zos.close();
    }

    private static void zipSub(File baseDir, File currentDir, java.util.zip.ZipOutputStream zos, byte[] buffer) throws Exception {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                zipSub(baseDir, f, zos, buffer);
            } else {
                String relPath = baseDir.toURI().relativize(f.toURI()).getPath();
                zos.putNextEntry(new java.util.zip.ZipEntry(relPath));
                FileInputStream fis = new FileInputStream(f);
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                fis.close();
                zos.closeEntry();
            }
        }
    }
}
