import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Teen Gojo's hooks (Balance.used, SelectorScreen) and the Heaven's Pierce fix (Toji.perform / Toji.targets). */
public class Patch2 implements Opcodes {
    static final String S = "com/jjk/mod/Sorcerer", T = "com/teengojo/TeenHooks", F = "com/teengojo/TojiFix";
    static final String ATK = "com/toji/mod/Toji$Atk", SHAPE = "com/toji/mod/Toji$Shape";

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        Patch.patch(root, "com/jjk/mod/Balance", Patch2::balance);
        Patch.patch(root, "com/jjk/mod/client/SelectorScreen", Patch2::selector);
        Patch.patch(root, "com/toji/mod/Toji", Patch2::toji);
    }

    /** Balance.used(p, slot): if (TeenHooks.key(p, slot)) return false; before everything (Toji's hook included). */
    static int balance(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            if (m.name.equals("used") && m.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;I)Z")) {
                InsnList l = new InsnList();
                LabelNode go = new LabelNode();
                l.add(new VarInsnNode(ALOAD, 0));
                l.add(new VarInsnNode(ILOAD, 1));
                l.add(new MethodInsnNode(INVOKESTATIC, T, "key", "(Lnet/minecraft/server/level/ServerPlayer;I)Z", false));
                l.add(new JumpInsnNode(IFEQ, go));
                l.add(new InsnNode(ICONST_0));
                l.add(new InsnNode(IRETURN));
                l.add(go);
                l.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(l);
                n++;
            }
        }
        return n;
    }

    /** SelectorScreen.<clinit>: JJK = TeenHooks.withTeen(Hooks.withToji(...)). */
    static int selector(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            if (!m.name.equals("<clinit>")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i instanceof FieldInsnNode f && f.getOpcode() == PUTSTATIC && f.name.equals("JJK") && f.desc.equals("[L" + S + ";")) {
                    m.instructions.insertBefore(i, new MethodInsnNode(INVOKESTATIC, T, "withTeen", "([L" + S + ";)[L" + S + ";", false));
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Toji.perform(p, st, atk, w): a dashing (PATH) attack notes where the dash began;
     * Toji.targets(p, atk, w): a dashing attack's targets are everyone along the dash.
     */
    static int toji(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            if (m.name.equals("perform") && m.desc.startsWith("(Lnet/minecraft/server/level/ServerPlayer;Lcom/toji/mod/Toji$St;L" + ATK + ";")) {
                InsnList l = new InsnList();
                LabelNode skip = new LabelNode();
                l.add(new VarInsnNode(ALOAD, 2));
                l.add(new FieldInsnNode(GETFIELD, ATK, "shape", "L" + SHAPE + ";"));
                l.add(new FieldInsnNode(GETSTATIC, SHAPE, "PATH", "L" + SHAPE + ";"));
                l.add(new JumpInsnNode(IF_ACMPNE, skip));
                l.add(new VarInsnNode(ALOAD, 0));
                l.add(new MethodInsnNode(INVOKESTATIC, F, "start", "(Lnet/minecraft/server/level/ServerPlayer;)V", false));
                l.add(skip);
                l.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(l);
                n++;
            }
            if (m.name.equals("targets") && m.desc.startsWith("(Lnet/minecraft/server/level/ServerPlayer;L" + ATK + ";")) {
                InsnList l = new InsnList();
                LabelNode skip = new LabelNode();
                l.add(new VarInsnNode(ALOAD, 1));
                l.add(new FieldInsnNode(GETFIELD, ATK, "shape", "L" + SHAPE + ";"));
                l.add(new FieldInsnNode(GETSTATIC, SHAPE, "PATH", "L" + SHAPE + ";"));
                l.add(new JumpInsnNode(IF_ACMPNE, skip));
                l.add(new VarInsnNode(ALOAD, 0));
                l.add(new VarInsnNode(ALOAD, 1));
                l.add(new FieldInsnNode(GETFIELD, ATK, "reach", "D"));
                l.add(new MethodInsnNode(INVOKESTATIC, F, "pathTargets", "(Lnet/minecraft/server/level/ServerPlayer;D)Ljava/util/List;", false));
                l.add(new InsnNode(ARETURN));
                l.add(skip);
                l.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(l);
                n++;
            }
        }
        return n == 2 ? 1 : -n;
    }
}
