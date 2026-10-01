import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Patches Aether.class, CommonProxy.class, and AetherPlayerTracker.class to:
 * 1. Support dual-check (PlayerCoreServer for vanilla Forge, AetherMcpcAdapter for MCPC+) in Aether.getServerPlayer.
 * 2. Delegate CommonProxy.getPlayerHandler safely to Aether.getServerPlayer.
 * 3. Hook AetherPlayerTracker.onPlayerLogout to trigger AetherMcpcAdapter.onPlayerLogout on MCPC+.
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

            if ("net/aetherteam/aether/Aether.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/Aether.class...");
                bytes = patchAether(bytes);
            } else if ("net/aetherteam/aether/CommonProxy.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/CommonProxy.class...");
                bytes = patchCommonProxy(bytes);
            } else if ("net/aetherteam/aether/AetherPlayerTracker.class".equals(name)) {
                System.out.println("Patching net/aetherteam/aether/AetherPlayerTracker.class...");
                bytes = patchAetherPlayerTracker(bytes);
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

    private static byte[] patchAether(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if ("getServerPlayer".equals(mn.name) && "(Lsq;)Lnet/aetherteam/aether/PlayerBaseAetherServer;".equals(mn.desc)) {
                System.out.println("  Found Aether.getServerPlayer(sq): replacing bytecode with dual-check logic");

                LabelNode lMcpc = new LabelNode();
                LabelNode lNull = new LabelNode();

                InsnList il = new InsnList();

                // if (player == null) return null;
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new JumpInsnNode(Opcodes.IFNULL, lNull));

                // if (player instanceof PlayerCoreServer)
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new TypeInsnNode(Opcodes.INSTANCEOF, "net/aetherteam/playercore_api/cores/PlayerCoreServer"));
                il.add(new JumpInsnNode(Opcodes.IFEQ, lMcpc));

                // Vanilla Forge path: ((PlayerCoreServer) player).getPlayerCoreObject(PlayerBaseAetherServer.class)
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/aetherteam/playercore_api/cores/PlayerCoreServer"));
                il.add(new LdcInsnNode(Type.getType("Lnet/aetherteam/aether/PlayerBaseAetherServer;")));
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "net/aetherteam/playercore_api/cores/PlayerCoreServer",
                    "getPlayerCoreObject",
                    "(Ljava/lang/Class;)Lnet/aetherteam/playercore_api/cores/PlayerCoreServer;"));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/aetherteam/aether/PlayerBaseAetherServer"));
                il.add(new InsnNode(Opcodes.ARETURN));

                // MCPC+ path:
                il.add(lMcpc);
                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new TypeInsnNode(Opcodes.INSTANCEOF, "jc"));
                il.add(new JumpInsnNode(Opcodes.IFEQ, lNull));

                il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                il.add(new TypeInsnNode(Opcodes.CHECKCAST, "jc"));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "AetherMcpcAdapter",
                    "getServerPlayer",
                    "(Ljc;)Lnet/aetherteam/aether/PlayerBaseAetherServer;"));
                il.add(new InsnNode(Opcodes.ARETURN));

                // Null return
                il.add(lNull);
                il.add(new InsnNode(Opcodes.ACONST_NULL));
                il.add(new InsnNode(Opcodes.ARETURN));

                mn.instructions = il;
                mn.tryCatchBlocks.clear();
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

    private static byte[] patchCommonProxy(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if ("getPlayerHandler".equals(mn.name) && "(Lsq;)Lnet/aetherteam/aether/AetherCommonPlayerHandler;".equals(mn.desc)) {
                System.out.println("  Found CommonProxy.getPlayerHandler(sq): replacing bytecode with safe delegation");

                LabelNode lNull = new LabelNode();
                InsnList il = new InsnList();

                // PlayerBaseAetherServer base = Aether.getServerPlayer(player);
                il.add(new VarInsnNode(Opcodes.ALOAD, 1));
                il.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/aetherteam/aether/Aether",
                    "getServerPlayer",
                    "(Lsq;)Lnet/aetherteam/aether/PlayerBaseAetherServer;"));
                il.add(new InsnNode(Opcodes.DUP));
                il.add(new JumpInsnNode(Opcodes.IFNULL, lNull));

                // return base.getPlayerHandler();
                il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                    "net/aetherteam/aether/PlayerBaseAetherServer",
                    "getPlayerHandler",
                    "()Lnet/aetherteam/aether/AetherCommonPlayerHandler;"));
                il.add(new InsnNode(Opcodes.ARETURN));

                // lNull:
                il.add(lNull);
                il.add(new InsnNode(Opcodes.POP));
                il.add(new InsnNode(Opcodes.ACONST_NULL));
                il.add(new InsnNode(Opcodes.ARETURN));

                mn.instructions = il;
                mn.tryCatchBlocks.clear();
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

    private static byte[] patchAetherPlayerTracker(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if ("onPlayerLogout".equals(mn.name) && "(Lsq;)V".equals(mn.desc)) {
                System.out.println("  Found AetherPlayerTracker.onPlayerLogout(sq): inserting MCPC+ logout hook and null-guard");

                // 1. Hook at the very beginning of onPlayerLogout
                LabelNode lSkip = new LabelNode();
                InsnList hook = new InsnList();

                // if (player instanceof jc && !(player instanceof PlayerCoreServer))
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new TypeInsnNode(Opcodes.INSTANCEOF, "jc"));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, lSkip));

                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new TypeInsnNode(Opcodes.INSTANCEOF, "net/aetherteam/playercore_api/cores/PlayerCoreServer"));
                hook.add(new JumpInsnNode(Opcodes.IFNE, lSkip));

                // AetherMcpcAdapter.onPlayerLogout((jc) player);
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new TypeInsnNode(Opcodes.CHECKCAST, "jc"));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "AetherMcpcAdapter",
                    "onPlayerLogout",
                    "(Ljc;)V"));

                hook.add(lSkip);
                mn.instructions.insertBefore(mn.instructions.getFirst(), hook);

                // 2. Insert null-guard before the first Aether.getServerPlayer call (instruction ~148)
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode min = (MethodInsnNode) insn;
                        if ("net/aetherteam/aether/Aether".equals(min.owner) && "getServerPlayer".equals(min.name)) {
                            // Find the ALOAD_1 instruction immediately preceding this sequence
                            AbstractInsnNode prev = insn.getPrevious();
                            while (prev != null && prev.getOpcode() != Opcodes.ALOAD) {
                                prev = prev.getPrevious();
                            }
                            if (prev != null) {
                                LabelNode lReturn = new LabelNode();
                                InsnList guard = new InsnList();
                                guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "net/aetherteam/aether/Aether",
                                    "getServerPlayer",
                                    "(Lsq;)Lnet/aetherteam/aether/PlayerBaseAetherServer;"));
                                guard.add(new JumpInsnNode(Opcodes.IFNULL, lReturn));

                                mn.instructions.insertBefore(prev, guard);
                                mn.instructions.add(lReturn);
                                mn.instructions.add(new InsnNode(Opcodes.RETURN));
                                System.out.println("  Inserted null-guard in onPlayerLogout before getServerPlayer");
                            }
                            break;
                        }
                    }
                }
            } else if ("updatePlayerClientInfo".equals(mn.name) && "(Ljc;Z)V".equals(mn.desc)) {
                System.out.println("  Found AetherPlayerTracker.updatePlayerClientInfo: inserting null-guard");
                LabelNode lReturn = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "net/aetherteam/aether/Aether",
                    "getServerPlayer",
                    "(Lsq;)Lnet/aetherteam/aether/PlayerBaseAetherServer;"));
                guard.add(new JumpInsnNode(Opcodes.IFNULL, lReturn));

                mn.instructions.insertBefore(mn.instructions.getFirst(), guard);
                mn.instructions.add(lReturn);
                mn.instructions.add(new InsnNode(Opcodes.RETURN));
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
