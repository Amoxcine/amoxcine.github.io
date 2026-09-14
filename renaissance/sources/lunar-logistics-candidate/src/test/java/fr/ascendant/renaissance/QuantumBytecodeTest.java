package fr.ascendant.renaissance;

import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Structural contract test of the exact upstream bytecode, NOT Mixin application. */
public final class QuantumBytecodeTest {
    public static void main(String[] args) throws Exception {
        var node = new ClassNode();
        try (var jar = new JarFile(args[0]); var stream = jar.getInputStream(
                jar.getJarEntry("appeng/me/cluster/implementations/QuantumCluster.class"))) {
            new ClassReader(stream).accept(node, 0);
        }
        var method = node.methods.stream().filter(m -> m.name.equals("updateStatus") && m.desc.equals("(Z)V"))
            .findFirst().orElseThrow();
        int lookups = 0, lookupIndex = -1, createIndex = -1, firstReturn = -1, destroyIndex = -1, clearIndex = -1;
        int index = 0;
        for (var insn : method.instructions) {
            if (insn.getOpcode() == Opcodes.RETURN && firstReturn == -1) firstReturn = index;
            if (insn instanceof MethodInsnNode call) {
                if (call.owner.equals("appeng/api/features/Locatables$Type") && call.name.equals("get")
                        && call.desc.equals("(Lnet/minecraft/world/level/Level;J)Ljava/lang/Object;")) {
                    lookups++; lookupIndex = index;
                }
                if (call.owner.equals("appeng/api/networking/GridHelper") && call.name.equals("createConnection")) createIndex = index;
                if (call.owner.equals("appeng/api/networking/IGridConnection") && call.name.equals("destroy")) destroyIndex = index;
                if (call.owner.equals("appeng/me/service/helpers/ConnectionWrapper") && call.name.equals("setConnection")) {
                    if (insn.getPrevious().getOpcode() != Opcodes.ACONST_NULL) throw new AssertionError("Native clear no longer null");
                    clearIndex = index;
                }
            }
            index++;
        }
        if (lookups != 1 || !(lookupIndex < firstReturn && firstReturn < createIndex
                && createIndex < destroyIndex && destroyIndex < clearIndex))
            throw new AssertionError("AE2 hook shape changed");
        System.out.println("AE2 bytecode shape passed: unique lookup before existing-link return; native destroy/null clear retained");
    }
}
