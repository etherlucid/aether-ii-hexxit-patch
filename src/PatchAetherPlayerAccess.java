import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;

public class PatchAetherPlayerAccess {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java PatchAetherPlayerAccess <input_jar> <output_jar>");
            System.exit(1);
        }

        File inputJar = new File(args[0]);
        File outputJar = new File(args[1]);

        System.out.println("=== Patching Aether II Player Access ===");
        System.out.println("Input JAR: " + inputJar.getAbsolutePath());
        System.out.println("Output JAR: " + outputJar.getAbsolutePath());

        File tmpDir = new File("/tmp/aether_patch_work");
        deleteDir(tmpDir);
        tmpDir.mkdirs();

        unzip(inputJar, tmpDir);

        // 1. Patch CommonProxy.class
        patchCommonProxy(new File(tmpDir, "net/aetherteam/aether/CommonProxy.class"));

        // 2. Patch ClientProxy.class
        patchClientProxy(new File(tmpDir, "net/aetherteam/aether/client/ClientProxy.class"));

        // 3. Patch Aether.class
        patchAether(new File(tmpDir, "net/aetherteam/aether/Aether.class"));

        // 4. Patch AetherHooks.class
        patchAetherHooks(new File(tmpDir, "net/aetherteam/aether/AetherHooks.class"));

        // Re-pack output JAR
        zip(tmpDir, outputJar);
        System.out.println("Successfully patched player access in: " + outputJar.getAbsolutePath());
    }

    private static void patchCommonProxy(File file) throws Exception {
        if (!file.exists()) return;
        ClassNode cn = readClass(file);
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("getPlayerHandler")) {
                System.out.println("[CommonProxy] Patching getPlayerHandler...");
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == Opcodes.INSTANCEOF) {
                        TypeInsnNode tin = (TypeInsnNode) insn;
                        if (tin.desc.equals("jc") || tin.desc.equals("net/minecraft/entity/player/EntityPlayerMP")) {
                            tin.desc = "net/aetherteam/playercore_api/cores/PlayerCoreServer";
                            System.out.println("  -> Replaced INSTANCEOF " + tin.desc);
                        }
                    }
                }
            }
        }
        writeClass(cn, file);
    }

    private static void patchClientProxy(File file) throws Exception {
        if (!file.exists()) return;
        ClassNode cn = readClass(file);
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("getPlayerHandler")) {
                System.out.println("[ClientProxy] Patching getPlayerHandler...");
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == Opcodes.INSTANCEOF) {
                        TypeInsnNode tin = (TypeInsnNode) insn;
                        if (tin.desc.equals("bfj") || tin.desc.equals("net/minecraft/client/entity/EntityPlayerSP")) {
                            tin.desc = "net/aetherteam/playercore_api/cores/PlayerCoreClient";
                            System.out.println("  -> Replaced INSTANCEOF " + tin.desc);
                        }
                    }
                }
            }
        }
        writeClass(cn, file);
    }

    private static void patchAether(File file) throws Exception {
        if (!file.exists()) return;
        ClassNode cn = readClass(file);
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("getServerPlayer")) {
                System.out.println("[Aether] Patching getServerPlayer...");
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == Opcodes.INSTANCEOF) {
                        TypeInsnNode tin = (TypeInsnNode) insn;
                        if (tin.desc.equals("jc") || tin.desc.equals("net/minecraft/entity/player/EntityPlayerMP")) {
                            tin.desc = "net/aetherteam/playercore_api/cores/PlayerCoreServer";
                            System.out.println("  -> Replaced INSTANCEOF " + tin.desc);
                        }
                    }
                }
            } else if (mn.name.equals("getClientPlayer")) {
                System.out.println("[Aether] Patching getClientPlayer...");
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == Opcodes.INSTANCEOF) {
                        TypeInsnNode tin = (TypeInsnNode) insn;
                        if (tin.desc.equals("bfj") || tin.desc.equals("net/minecraft/client/entity/EntityPlayerSP")) {
                            tin.desc = "net/aetherteam/playercore_api/cores/PlayerCoreClient";
                            System.out.println("  -> Replaced INSTANCEOF " + tin.desc);
                        }
                    }
                }
            }
        }
        writeClass(cn, file);
    }

    private static void patchAetherHooks(File file) throws Exception {
        if (!file.exists()) return;
        ClassNode cn = readClass(file);
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("handleAetherMountInteraction")) {
                System.out.println("[AetherHooks] Patching handleAetherMountInteraction...");
                InsnList newInstructions = new InsnList();
                
                LabelNode endLabel = new LabelNode();
                LabelNode nextLabel = new LabelNode();

                // 1. Check if action == RIGHT_CLICK_BLOCK
                newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                newInstructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraftforge/event/entity/player/PlayerInteractEvent", "action", "Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$Action;"));
                newInstructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraftforge/event/entity/player/PlayerInteractEvent$Action", "RIGHT_CLICK_BLOCK", "Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$Action;"));
                newInstructions.add(new JumpInsnNode(Opcodes.IF_ACMPNE, endLabel));

                // 2. Safe player base check & unmount
                newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                newInstructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraftforge/event/entity/player/PlayerInteractEvent", "entityPlayer", "Lsq;"));
                newInstructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/aetherteam/aether/Aether", "getPlayerBase", "(Lsq;)Lnet/aetherteam/aether/AetherCommonPlayerHandler;"));
                newInstructions.add(new VarInsnNode(Opcodes.ASTORE, 2));
                newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                newInstructions.add(new JumpInsnNode(Opcodes.IFNULL, endLabel));

                newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                newInstructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/aetherteam/aether/AetherCommonPlayerHandler", "riddenBy", "Lnet/aetherteam/aether/entities/mounts_old/RidingHandler;"));
                newInstructions.add(new JumpInsnNode(Opcodes.IFNULL, endLabel));

                newInstructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
                newInstructions.add(new FieldInsnNode(Opcodes.GETFIELD, "net/aetherteam/aether/AetherCommonPlayerHandler", "riddenBy", "Lnet/aetherteam/aether/entities/mounts_old/RidingHandler;"));
                newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/aetherteam/aether/entities/mounts_old/RidingHandler", "onUnMount", "()V"));

                newInstructions.add(endLabel);
                newInstructions.add(new InsnNode(Opcodes.RETURN));

                mn.instructions = newInstructions;
                if (mn.localVariables != null) mn.localVariables.clear();
                if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
                System.out.println("  -> Successfully replaced handleAetherMountInteraction with safe null-checked implementation!");
            }
        }
        writeClass(cn, file);
    }

    private static ClassNode readClass(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();
        return cn;
    }

    private static void writeClass(ClassNode cn, File file) throws Exception {
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
