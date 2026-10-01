import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;

/**
 * Patches AetherPlayerTracker to add null-guards around all
 * Aether.getServerPlayer() usages in onPlayerLogout and updatePlayerClientInfo.
 *
 * On MCPC+, getServerPlayer() returns null (player is EntityPlayerMP but not
 * PlayerCoreServer). The original code called getServerPlayer() without null
 * checks, causing NullPointerException on logout and client-info update.
 *
 * Strategy: before each method's first getServerPlayer call site, insert:
 *   PlayerBaseAetherServer _base = Aether.getServerPlayer(player);
 *   if (_base == null) goto earlyReturn;
 * Then replace every subsequent ALOAD_player, [CHECKCAST jc], INVOKESTATIC getServerPlayer
 * with just ALOAD _base.
 * Finally add earlyReturn: RETURN after the method's existing instructions.
 *
 * Party/dungeon cleanup (which runs before the first getServerPlayer call in
 * onPlayerLogout) is preserved. On MCPC+ where there's no Aether player state,
 * the accessory/health/cooldown packet sends are simply skipped.
 */
public class PatchAetherPlayerTracker {
    private static final String OWNER = "net/aetherteam/aether/Aether";
    private static final String METHOD = "getServerPlayer";
    private static final String DESC   = "(Lsq;)Lnet/aetherteam/aether/PlayerBaseAetherServer;";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java PatchAetherPlayerTracker <input_jar> <output_jar>");
            System.exit(1);
        }

        File inputJar  = new File(args[0]);
        File outputJar = new File(args[1]);

        System.out.println("=== Patching AetherPlayerTracker null-guards ===");

        File tmpDir = new File("/tmp/tracker_patch_work");
        deleteDir(tmpDir);
        tmpDir.mkdirs();

        unzip(inputJar, tmpDir);

        File trackerFile = new File(tmpDir,
            "net/aetherteam/aether/AetherPlayerTracker.class");
        if (!trackerFile.exists()) {
            System.err.println("AetherPlayerTracker.class not found in JAR — skipping.");
        } else {
            patchTracker(trackerFile);
        }

        zip(tmpDir, outputJar);
        System.out.println("Done: " + outputJar.getAbsolutePath());
    }

    private static void patchTracker(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("onPlayerLogout") || mn.name.equals("updatePlayerClientInfo")) {
                patchMethod(cn.name, mn);
            }
        }

        // Use COMPUTE_FRAMES but provide a safe getCommonSuperClass
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                // Return Object for any unknown superclass — safe fallback
                return "java/lang/Object";
            }
        };
        cn.accept(cw);
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(cw.toByteArray());
        fos.close();
    }

    /**
     * Patches a method by:
     * 1. Finding the first getServerPlayer call
     * 2. Finding the ALOAD of the player parameter that feeds into it
     * 3. Inserting a null-guard prologue before that ALOAD:
     *      ALOAD playerParam
     *      INVOKESTATIC getServerPlayer
     *      ASTORE baseLocal
     *      ALOAD baseLocal
     *      IFNULL earlyReturn
     * 4. Removing the original ALOAD[+CHECKCAST]+INVOKESTATIC for every getServerPlayer call
     *    and replacing with ALOAD baseLocal
     * 5. Appending earlyReturn label + RETURN at method end
     */
    private static void patchMethod(String className, MethodNode mn) {
        System.out.println("[AetherPlayerTracker] Patching " + mn.name + mn.desc);

        // Collect all getServerPlayer call nodes
        List<MethodInsnNode> calls = new ArrayList<MethodInsnNode>();
        for (AbstractInsnNode insn : mn.instructions) {
            if (isGetServerPlayer(insn)) {
                calls.add((MethodInsnNode) insn);
            }
        }

        if (calls.isEmpty()) {
            System.out.println("  -> No getServerPlayer calls found, skipping.");
            return;
        }

        // Determine which parameter slot holds the player (sq) argument.
        // onPlayerLogout(sq) — slot 1
        // updatePlayerClientInfo(jc, boolean) — slot 1 (jc extends sq)
        int playerSlot = 1;

        // Allocate a new local for the cached base
        int baseLocal = mn.maxLocals;
        mn.maxLocals = baseLocal + 1;

        // Create the early-return label (fresh, standalone RETURN at end)
        LabelNode earlyReturn = new LabelNode();

        // --- Step 1: Insert null-guard before the first call ---
        MethodInsnNode firstCall = calls.get(0);

        // Walk backward from firstCall to find the start of:
        //   ALOAD playerSlot  [CHECKCAST jc]  INVOKESTATIC getServerPlayer
        // Collect these nodes to remove
        List<AbstractInsnNode> firstCallPrefix = collectPrefix(firstCall);

        // Build guard: ALOAD playerSlot, INVOKESTATIC getServerPlayer, ASTORE baseLocal,
        //              ALOAD baseLocal, IFNULL earlyReturn
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, playerSlot));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, METHOD, DESC));
        guard.add(new VarInsnNode(Opcodes.ASTORE, baseLocal));
        guard.add(new VarInsnNode(Opcodes.ALOAD, baseLocal));
        guard.add(new JumpInsnNode(Opcodes.IFNULL, earlyReturn));

        // Insert before the prefix of the first call
        AbstractInsnNode insertionPoint = firstCallPrefix.isEmpty()
            ? firstCall
            : firstCallPrefix.get(firstCallPrefix.size() - 1);
        mn.instructions.insertBefore(insertionPoint, guard);

        // Remove prefix nodes and replace the call itself with ALOAD baseLocal
        for (AbstractInsnNode n : firstCallPrefix) {
            mn.instructions.remove(n);
        }
        mn.instructions.set(firstCall, new VarInsnNode(Opcodes.ALOAD, baseLocal));

        // --- Step 2: Replace remaining calls with ALOAD baseLocal ---
        for (int i = 1; i < calls.size(); i++) {
            MethodInsnNode call = calls.get(i);
            List<AbstractInsnNode> prefix = collectPrefix(call);
            for (AbstractInsnNode n : prefix) {
                mn.instructions.remove(n);
            }
            mn.instructions.set(call, new VarInsnNode(Opcodes.ALOAD, baseLocal));
        }

        // --- Step 3: Append earlyReturn + RETURN at the end ---
        mn.instructions.add(earlyReturn);
        mn.instructions.add(new InsnNode(Opcodes.RETURN));

        // Clear local variable debug info to avoid stale entries confusing the verifier
        if (mn.localVariables != null) {
            mn.localVariables.clear();
        }
        // Clear try-catch blocks for simplicity (none expected in these methods)
        if (mn.tryCatchBlocks != null) {
            mn.tryCatchBlocks.clear();
        }

        System.out.println("  -> Inserted null-guard in " + mn.name +
            " (base in local " + baseLocal + ", " + calls.size() + " call(s) replaced)");
    }

    /**
     * Walks backward from a getServerPlayer MethodInsnNode to collect the
     * preceding ALOAD[+CHECKCAST] nodes that load the player arg onto the stack.
     * Returns them in reverse order (closest-to-call first).
     */
    private static List<AbstractInsnNode> collectPrefix(MethodInsnNode call) {
        List<AbstractInsnNode> prefix = new ArrayList<AbstractInsnNode>();
        AbstractInsnNode cur = call.getPrevious();
        // Skip labels/frames
        while (cur != null && cur.getOpcode() == -1) {
            cur = cur.getPrevious();
        }
        // Collect CHECKCAST if present
        if (cur != null && cur.getOpcode() == Opcodes.CHECKCAST) {
            prefix.add(cur);
            cur = cur.getPrevious();
            while (cur != null && cur.getOpcode() == -1) {
                cur = cur.getPrevious();
            }
        }
        // Collect ALOAD
        if (cur != null && cur.getOpcode() == Opcodes.ALOAD) {
            prefix.add(cur);
        }
        return prefix;
    }

    private static boolean isGetServerPlayer(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode min = (MethodInsnNode) insn;
            return min.getOpcode() == Opcodes.INVOKESTATIC
                && min.owner.equals(OWNER)
                && min.name.equals(METHOD);
        }
        return false;
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
