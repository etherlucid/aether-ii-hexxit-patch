import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;

public class BuildFinalPatchedGui {
    public static void main(String[] args) throws Exception {
        File inputFile = new File("/tmp/aether_jar_extracted/net/aetherteam/aether/client/gui/GuiAetherContainerCreative.class");
        FileInputStream fis = new FileInputStream(inputFile);
        ClassReader cr = new ClassReader(fis);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        fis.close();

        for (Object mnObj : cn.methods) {
            MethodNode mn = (MethodNode) mnObj;

            // 1. Insert removeAetherButtons call at index 0 of drawScreen (a(IIF)V or drawScreen)
            if (mn.name.equals("drawScreen") || (mn.name.equals("a") && mn.desc.equals("(IIF)V"))) {
                InsnList callList = new InsnList();
                callList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                callList.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "AetherInventoryAdapter",
                    "removeAetherButtons",
                    "(Ljava/lang/Object;)V"));
                mn.instructions.insert(callList);
                System.out.println("Inserted removeAetherButtons into " + mn.name + mn.desc);
            }

            // 2. Insert removeAetherButtons call at end of initGui (A_()V)
            if (mn.name.equals("initGui") || mn.name.equals("A_")) {
                InsnList callList = new InsnList();
                callList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                callList.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                    "AetherInventoryAdapter",
                    "removeAetherButtons",
                    "(Ljava/lang/Object;)V"));
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        mn.instructions.insertBefore(insn, callList);
                        System.out.println("Inserted removeAetherButtons before RETURN in " + mn.name + mn.desc);
                        break;
                    }
                }
            }

            // 3. Patch drawGuiContainerBackgroundLayer (a(FII)V): replace bipush 25 with bipush 46 for player render X pos
            if (mn.name.equals("drawGuiContainerBackgroundLayer") || (mn.name.equals("a") && mn.desc.equals("(FII)V"))) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof IntInsnNode) {
                        IntInsnNode iin = (IntInsnNode) insn;
                        if (iin.getOpcode() == Opcodes.BIPUSH && iin.operand == 25) {
                            // Check if followed by IADD and another BIPUSH 46
                            if (i + 5 < mn.instructions.size()) {
                                AbstractInsnNode next = mn.instructions.get(i + 1);
                                if (next.getOpcode() == Opcodes.IADD) {
                                    iin.operand = 46;
                                    System.out.println("Patched player render X pos BIPUSH 25 -> 46 in " + mn.name + mn.desc + " at insn " + i);
                                }
                            }
                        }
                    }
                }
            }
            
            // 4. Patch all reference comparisons on ve.m and ve.g across all methods in GuiAetherContainerCreative
            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if (fin.owner.equals("ve") && (fin.name.equals("m") || fin.name.equals("g"))) {
                        AbstractInsnNode next = mn.instructions.get(i + 1);
                        if (next.getOpcode() == Opcodes.IF_ACMPEQ || next.getOpcode() == Opcodes.IF_ACMPNE) {
                            LabelNode target = ((JumpInsnNode) next).label;
                            int newJumpOpcode = (next.getOpcode() == Opcodes.IF_ACMPEQ) ? Opcodes.IFNE : Opcodes.IFEQ;

                            InsnList patch = new InsnList();
                            patch.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "AetherInventoryAdapter",
                                "isSameTab",
                                "(Ljava/lang/Object;Ljava/lang/Object;)Z"));
                            patch.add(new JumpInsnNode(newJumpOpcode, target));

                            mn.instructions.insert(next, patch);
                            mn.instructions.remove(next);
                            System.out.println("Patched ve." + fin.name + " comparison in method " + mn.name + mn.desc + " at insn " + i);
                        }
                    }
                }
            }

            // 5. In setCurrentCreativeTab (or b(Lve;)V), insert fixCreativeSurvivalLayout call and jump to end of tab setup
            if (mn.name.equals("setCurrentCreativeTab") || (mn.name.equals("b") && mn.desc.equals("(Lve;)V"))) {
                System.out.println("Found setCurrentCreativeTab method: " + mn.name + mn.desc);
                int astore4Idx = -1;
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode insn = mn.instructions.get(i);
                    if (insn instanceof VarInsnNode) {
                        VarInsnNode vin = (VarInsnNode) insn;
                        if (vin.getOpcode() == Opcodes.ASTORE && vin.var == 4) {
                            astore4Idx = i;
                            System.out.println("  Found ASTORE 4 at insn " + i);
                            break;
                        }
                    }
                }

                LabelNode target463 = null;
                for (int k = 0; k < mn.instructions.size(); k++) {
                    AbstractInsnNode kInsn = mn.instructions.get(k);
                    if (kInsn instanceof FieldInsnNode) {
                        FieldInsnNode fin = (FieldInsnNode) kInsn;
                        if (fin.name.equals("searchField") || fin.name.equals("Laws;")) {
                            for (int j = k - 1; j >= 0; j--) {
                                if (mn.instructions.get(j) instanceof LabelNode) {
                                    target463 = (LabelNode) mn.instructions.get(j);
                                    System.out.println("  Found target463 label before searchField IN setCurrentCreativeTab at insn " + j);
                                    break;
                                }
                            }
                            if (target463 != null) break;
                        }
                    }
                }

                if (astore4Idx != -1 && target463 != null) {
                    VarInsnNode vin = (VarInsnNode) mn.instructions.get(astore4Idx);
                    InsnList callList = new InsnList();
                    callList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    callList.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    callList.add(new VarInsnNode(Opcodes.ALOAD, 4));
                    callList.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        "AetherInventoryAdapter",
                        "fixCreativeSurvivalLayout",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V"));
                    callList.add(new JumpInsnNode(Opcodes.GOTO, target463));
                    mn.instructions.insert(vin, callList);
                    System.out.println("  SUCCESSFULLY inserted adapter call AND intra-method GOTO target463 inside setCurrentCreativeTab!");
                } else {
                    System.out.println("  ERROR: Could not find ASTORE 4 or target463 in setCurrentCreativeTab!");
                }
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);

        File outFile = new File("/tmp/aether_repack_final/net/aetherteam/aether/client/gui/GuiAetherContainerCreative.class");
        outFile.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(cw.toByteArray());
        fos.close();
        System.out.println("Saved patched GuiAetherContainerCreative.class to /tmp/aether_repack_final!");
    }
}
