import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import java.nio.file.*;

/** The selector's Stand tab gets Johnny and Gyro: JOJO = TeenHooks.withStands(new Sorcerer[]{GER}). */
public class Patch3 implements Opcodes {
    static final String S = "com/jjk/mod/Sorcerer";

    public static void main(String[] a) throws Exception {
        Patch.patch(Paths.get(a[0]), "com/jjk/mod/client/SelectorScreen", c -> {
            int n = 0;
            for (MethodNode m : c.methods) {
                if (!m.name.equals("<clinit>")) continue;
                for (AbstractInsnNode i : m.instructions.toArray()) {
                    if (i instanceof FieldInsnNode f && f.getOpcode() == PUTSTATIC && f.name.equals("JOJO") && f.desc.equals("[L" + S + ";")) {
                        m.instructions.insertBefore(i, new MethodInsnNode(INVOKESTATIC, "com/teengojo/TeenHooks", "withStands", "([L" + S + ";)[L" + S + ";", false));
                        n++;
                    }
                }
            }
            return n;
        });
    }
}
