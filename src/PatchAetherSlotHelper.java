import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;

public class PatchAetherSlotHelper {
    public static void main(String[] args) throws Exception {
        File inputFile = new File("/tmp/aether_jar_extracted/net/aetherteam/aether/client/gui/AetherSlotHelper.class");
        FileInputStream fis = new FileInputStream(inputFile);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("applyVanillaSlots")) {
                System.out.println("Found applyVanillaSlots method in AetherSlotHelper: " + mn.name + mn.desc);
                InsnList newInsn = new InsnList();
                newInsn.add(new VarInsnNode(Opcodes.ALOAD, 0));
                newInsn.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "AetherInventoryAdapter",
                    "applyVanillaSlots",
                    "(Ljava/lang/Object;)V"));
                newInsn.add(new InsnNode(Opcodes.RETURN));
                mn.instructions = newInsn;
                if (mn.localVariables != null) mn.localVariables.clear();
                if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
                System.out.println("  -> Successfully redirected applyVanillaSlots to AetherInventoryAdapter!");
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);

        File outFile = new File("/tmp/aether_repack_final/net/aetherteam/aether/client/gui/AetherSlotHelper.class");
        outFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(cw.toByteArray());
        fos.close();
        System.out.println("Saved patched AetherSlotHelper.class to /tmp/aether_repack_final!");
    }
}
