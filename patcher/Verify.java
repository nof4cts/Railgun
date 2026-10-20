import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.tree.*;
import jdk.internal.org.objectweb.asm.tree.analysis.*;
import java.nio.file.*;
public class Verify {
    public static void main(String[] a) throws Exception {
        int bad = 0;
        for (int k = 1; k < a.length; k++) {
            ClassNode c = new ClassNode();
            new ClassReader(Files.readAllBytes(Paths.get(a[0], a[k] + ".class"))).accept(c, 0);
            for (MethodNode m : c.methods) {
                try { new Analyzer<>(new BasicVerifier()).analyze(c.name, m); }
                catch (AnalyzerException e) { bad++; System.out.println("BAD " + c.name + "." + m.name + ": " + e.getMessage()); }
            }
            System.out.println("ok " + c.name + " (" + c.methods.size() + " methods)");
        }
        System.exit(bad == 0 ? 0 : 1);
    }
}
