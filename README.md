# JJK jar add-ons (Toji merge, Teen Gojo, Johnny & Gyro)

`jjk-1.0.0.jar` here is the current full jar: the Uro + Yaga jar, plus Toji, Teen Gojo, and Johnny (Tusk) and Gyro (Spin).

How it is made (the base jar's own source isn't on GitHub, so it is patched):

1. Start from the Uro + Yaga jar; copy in Toji's `com/toji/**`, `assets/toji/**` and `data/toji/**` from the Toji jar.
2. Add the compiled `com/teengojo/**`, `com/sbr/**`, `assets/teengojo/**` and `assets/sbr/**`. Their source is on branch `sukuna` (`src/main/java/com/teengojo`, `src/main/java/com/sbr`); CI there compiles them.
3. Replace `com/jjk/mod/Sorcerer.class` with `sorcerer/Sorcerer.java` compiled (`javac --release 17`); it adds TOJI, TEEN_GOJO, JOHNNY and GYRO at the end of the list.
4. Run the bytecode patchers (JDK's internal ASM): `Patch` (Toji's hooks), `Patch2` (Teen Gojo's key hook, selector, Heaven's Pierce fix), `Patch3` (Stand tab). Compile and run with
   `--add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED --add-exports java.base/jdk.internal.org.objectweb.asm.tree=ALL-UNNAMED`
   (and `...asm.tree.analysis` for `Verify`), passing the unpacked jar folder.
