import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;

public class PatchInvTweaksCreative {
    public static void main(String[] args) throws Exception {
        File inputFile = new File("/tmp/it_extract/invtweaks/InvTweaksObfuscation.class");
        FileInputStream fis = new FileInputStream(inputFile);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;

            // Patch 1: isGuiInventoryCreative — also accept GuiAetherContainerCreative
            if (mn.name.equals("isGuiInventoryCreative")) {
                System.out.println("Patching isGuiInventoryCreative...");
                InsnList newInsn = new InsnList();
                LabelNode falseLabel = new LabelNode();
                LabelNode trueLabel  = new LabelNode();
                LabelNode endLabel   = new LabelNode();

                // if (arg1 == null) goto false
                newInsn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                newInsn.add(new JumpInsnNode(Opcodes.IFNULL, falseLabel));

                // Class c = arg1.getClass()
                newInsn.add(new VarInsnNode(Opcodes.ALOAD, 1));
                newInsn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;"));
                newInsn.add(new VarInsnNode(Opcodes.ASTORE, 2));

                // if c.equals(GuiContainerCreative.class) goto true
                newInsn.add(new VarInsnNode(Opcodes.ALOAD, 2));
                newInsn.add(new LdcInsnNode(Type.getType("Lnet/minecraft/client/gui/inventory/GuiContainerCreative;")));
                newInsn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z"));
                newInsn.add(new JumpInsnNode(Opcodes.IFNE, trueLabel));

                // if c.getName().equals("net.aetherteam.aether.client.gui.GuiAetherContainerCreative") goto true
                newInsn.add(new VarInsnNode(Opcodes.ALOAD, 2));
                newInsn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;"));
                newInsn.add(new LdcInsnNode("net.aetherteam.aether.client.gui.GuiAetherContainerCreative"));
                newInsn.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z"));
                newInsn.add(new JumpInsnNode(Opcodes.IFNE, trueLabel));

                newInsn.add(falseLabel);
                newInsn.add(new InsnNode(Opcodes.ICONST_0));
                newInsn.add(new JumpInsnNode(Opcodes.GOTO, endLabel));

                newInsn.add(trueLabel);
                newInsn.add(new InsnNode(Opcodes.ICONST_1));

                newInsn.add(endLabel);
                newInsn.add(new InsnNode(Opcodes.IRETURN));

                mn.instructions = newInsn;
                if (mn.localVariables != null) mn.localVariables.clear();
                if (mn.tryCatchBlocks != null) mn.tryCatchBlocks.clear();
                mn.maxStack = 2;
                mn.maxLocals = 3;
                System.out.println("  -> isGuiInventoryCreative patched");
            }

            // Patch 2: isStandardInventory — change creative slot count check from == to >=
            // Original: if (isGuiInventoryCreative(gui)) { if (slots.size() == CREATIVE_MAIN_INVENTORY_SIZE) return true; }
            // New:      if (isGuiInventoryCreative(gui)) { if (slots.size() >= CREATIVE_MAIN_INVENTORY_SIZE) return true; }
            // We do this by replacing IF_ICMPEQ with IF_ICMPGE in the relevant instruction sequence.
            if (mn.name.equals("isStandardInventory")) {
                System.out.println("Patching isStandardInventory...");
                AbstractInsnNode[] insns = mn.instructions.toArray();
                for (int i = 0; i < insns.length; i++) {
                    AbstractInsnNode insn = insns[i];
                    // Find the IF_ICMPEQ that follows GETSTATIC CREATIVE_MAIN_INVENTORY_SIZE
                    if (insn.getType() == AbstractInsnNode.FIELD_INSN) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if (fin.owner.equals("invtweaks/InvTweaksObfuscation")
                                && fin.name.equals("CREATIVE_MAIN_INVENTORY_SIZE")) {
                            // Next instruction should be IF_ICMPEQ — replace with IF_ICMPGE
                            AbstractInsnNode next = insn.getNext();
                            if (next instanceof JumpInsnNode && ((JumpInsnNode) next).getOpcode() == Opcodes.IF_ICMPEQ) {
                                LabelNode target = ((JumpInsnNode) next).label;
                                mn.instructions.set(next, new JumpInsnNode(Opcodes.IF_ICMPGE, target));
                                System.out.println("  -> Changed IF_ICMPEQ to IF_ICMPGE for creative slot count check");
                            }
                        }
                    }
                }
            }
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);

        File outFile = new File("/tmp/it_patch_out2/invtweaks/InvTweaksObfuscation.class");
        outFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(cw.toByteArray());
        fos.close();
        System.out.println("Written: " + outFile.getPath());
    }
}
