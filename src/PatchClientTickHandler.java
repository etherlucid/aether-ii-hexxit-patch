import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;

public class PatchClientTickHandler {
    public static void main(String[] args) throws Exception {
        File inputFile = new File("/tmp/bak_extract/net/aetherteam/aether/client/ClientTickHandler.class");
        FileInputStream fis = new FileInputStream(inputFile);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;
            if (mn.name.equals("tickEnd") || mn.name.equals("tick") || mn.name.equals("a") || mn.desc.contains("TickType")) {
                System.out.println("Found tick method in ClientTickHandler: " + mn.name + mn.desc);
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof TypeInsnNode) {
                        TypeInsnNode tin = (TypeInsnNode) insn;
                        if (tin.getOpcode() == Opcodes.INSTANCEOF) {
                            System.out.println("  Found INSTANCEOF " + tin.desc + " at insn " + i);
                            if ("azg".equals(tin.desc) || tin.desc.contains("GuiInventory")) {
                                mn.instructions.set(tin, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "AetherInventoryAdapter",
                                    "shouldReplaceSurvivalGui",
                                    "(Ljava/lang/Object;)Z"));
                                System.out.println("    -> Patched Survival GUI check!");
                            } else if ("ayy".equals(tin.desc) || tin.desc.contains("GuiContainerCreative")) {
                                mn.instructions.set(tin, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                    "AetherInventoryAdapter",
                                    "shouldReplaceCreativeGui",
                                    "(Ljava/lang/Object;)Z"));
                                System.out.println("    -> Patched Creative GUI check!");
                            }
                        }
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);

        File outFile = new File("/tmp/aether_repack_final/net/aetherteam/aether/client/ClientTickHandler.class");
        outFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(cw.toByteArray());
        fos.close();
        System.out.println("Saved patched ClientTickHandler.class to /tmp/aether_repack_final!");
    }
}
