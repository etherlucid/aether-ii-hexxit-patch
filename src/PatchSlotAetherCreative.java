import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;

public class PatchSlotAetherCreative {
    public static void main(String[] args) throws Exception {
        File inputFile = new File("/tmp/aether_jar_extracted/net/aetherteam/aether/containers/SlotAetherCreativeInventory.class");
        FileInputStream fis = new FileInputStream(inputFile);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("b") || mn.name.equals("getBackgroundIconIndex") || mn.desc.equals("()Llx;")) {
                System.out.println("Found method b() in SlotAetherCreativeInventory: " + mn.name + mn.desc);
                InsnList newInsn = new InsnList();
                newInsn.add(new InsnNode(Opcodes.ACONST_NULL));
                newInsn.add(new InsnNode(Opcodes.ARETURN));
                mn.instructions = newInsn;
                if (mn.localVariables != null) mn.localVariables.clear();
                if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);

        File outFile = new File("/tmp/aether_repack_final/net/aetherteam/aether/containers/SlotAetherCreativeInventory.class");
        outFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(cw.toByteArray());
        fos.close();
        System.out.println("SlotAetherCreativeInventory patched successfully to /tmp/aether_repack_final!");
    }
}
