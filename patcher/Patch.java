import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/** Adds the Toji hooks to the newer jar's JjkMod, Balance, JjkHud and SelectorScreen (same hooks the Toji jar had). */
public class Patch implements Opcodes {
    static final String S = "com/jjk/mod/Sorcerer", H = "com/toji/mod/Hooks";

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        patch(root, "com/jjk/mod/JjkMod", Patch::jjkMod);
        patch(root, "com/jjk/mod/Balance", Patch::balance);
        patch(root, "com/jjk/mod/client/JjkHud", Patch::hud);
        patch(root, "com/jjk/mod/client/SelectorScreen", Patch::selector);
    }

    interface Fix { int apply(ClassNode c); }

    static void patch(Path root, String name, Fix f) throws Exception {
        Path p = root.resolve(name + ".class");
        ClassNode c = new ClassNode();
        new ClassReader(Files.readAllBytes(p)).accept(c, 0);
        int n = f.apply(c);
        if (n != 1) throw new IllegalStateException(name + ": expected exactly one patch site, found " + n);
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        c.accept(w);
        Files.write(p, w.toByteArray());
        System.out.println("patched " + name);
    }

    /** new YagaMod();  ->  new YagaMod(); new TojiMod(); */
    static int jjkMod(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i instanceof MethodInsnNode mi && mi.getOpcode() == INVOKESPECIAL && mi.owner.equals("com/yaga/mod/YagaMod") && mi.name.equals("<init>")) {
                    AbstractInsnNode after = mi.getNext(); // POP
                    if (after.getOpcode() != POP) throw new IllegalStateException("JjkMod: no POP after new YagaMod()");
                    InsnList l = new InsnList();
                    l.add(new TypeInsnNode(NEW, "com/toji/mod/TojiMod"));
                    l.add(new InsnNode(DUP));
                    l.add(new MethodInsnNode(INVOKESPECIAL, "com/toji/mod/TojiMod", "<init>", "()V", false));
                    l.add(new InsnNode(POP));
                    m.instructions.insert(after, l);
                    n++;
                }
            }
        }
        return n;
    }

    /** Balance.used(p, slot): if (Hooks.tojiKey(p, slot)) return false; first. */
    static int balance(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            if (m.name.equals("used") && m.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;I)Z")) {
                InsnList l = new InsnList();
                LabelNode go = new LabelNode();
                l.add(new VarInsnNode(ALOAD, 0));
                l.add(new VarInsnNode(ILOAD, 1));
                l.add(new MethodInsnNode(INVOKESTATIC, H, "tojiKey", "(Lnet/minecraft/server/level/ServerPlayer;I)Z", false));
                l.add(new JumpInsnNode(IFEQ, go));
                l.add(new InsnNode(ICONST_0));
                l.add(new InsnNode(IRETURN));
                l.add(go);
                // the jump target needs a stack map frame: same locals as the method's entry, empty stack
                l.add(new FrameNode(F_SAME, 0, null, 0, null));
                m.instructions.insert(l);
                n++;
            }
        }
        return n;
    }

    /** JjkHud: the energy bar's `s != NONE && s != GETO && s != NAOYA` gets `!Hooks.hideEnergy(s) &&` in front. */
    static int hud(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i instanceof FieldInsnNode f && f.getOpcode() == GETSTATIC && f.owner.equals(S) && f.name.equals("NONE")
                        && i.getPrevious() instanceof VarInsnNode load && load.getOpcode() == ALOAD
                        && i.getNext() instanceof JumpInsnNode j && j.getOpcode() == IF_ACMPEQ) {
                    // must be the NONE / GETO / NAOYA chain
                    AbstractInsnNode k = j.getNext();
                    boolean geto = false, naoya = false;
                    for (int s = 0; s < 8 && k != null; s++, k = k.getNext()) {
                        if (k instanceof FieldInsnNode g && g.owner.equals(S)) {
                            geto |= g.name.equals("GETO");
                            naoya |= g.name.equals("NAOYA");
                        }
                    }
                    if (!geto || !naoya) continue;
                    InsnList l = new InsnList();
                    l.add(new VarInsnNode(ALOAD, load.var));
                    l.add(new MethodInsnNode(INVOKESTATIC, H, "hideEnergy", "(L" + S + ";)Z", false));
                    l.add(new JumpInsnNode(IFNE, j.label));
                    m.instructions.insertBefore(load, l);
                    n++;
                }
            }
        }
        return n;
    }

    /** SelectorScreen.<clinit>: JJK = Hooks.withToji(new Sorcerer[]{...}); */
    static int selector(ClassNode c) {
        int n = 0;
        for (MethodNode m : c.methods) {
            if (!m.name.equals("<clinit>")) continue;
            for (AbstractInsnNode i : m.instructions.toArray()) {
                if (i instanceof FieldInsnNode f && f.getOpcode() == PUTSTATIC && f.name.equals("JJK") && f.desc.equals("[L" + S + ";")) {
                    m.instructions.insertBefore(i, new MethodInsnNode(INVOKESTATIC, H, "withToji", "([L" + S + ";)[L" + S + ";", false));
                    n++;
                }
            }
        }
        return n;
    }
}
