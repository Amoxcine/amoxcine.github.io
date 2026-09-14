package fr.ascendant.lunar.travel;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Pins actual registration -> supplier -> constructor -> immutable fluid. No game initialization. */
public final class FuelBucketContractTest {
    private static int checks;
    private static void check(boolean ok, String reason) { checks++; if (!ok) throw new AssertionError(reason); }
    private static ClassNode read(String name) throws Exception {
        try (var in = FuelBucketContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (in == null) throw new AssertionError("Missing pinned class: " + name);
            var node = new ClassNode(); new ClassReader(in.readAllBytes()).accept(node, 0); return node;
        }
    }
    public static void main(String[] args) throws Exception {
        String owner = "earth/terrarium/adastra/common/registry/ModItems";
        String bucket = "com/teamresourceful/resourcefullib/common/fluid/ResourcefulBucketItem";
        var items = read(owner);
        var init = items.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow();
        Handle supplier = null;
        for (var instruction : init.instructions) if (instruction instanceof LdcInsnNode literal && "fuel_bucket".equals(literal.cst)) {
            var next = instruction.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            check(next instanceof InvokeDynamicInsnNode, "Fuel registration supplier changed");
            for (Object arg : ((InvokeDynamicInsnNode) next).bsmArgs) if (arg instanceof Handle handle) supplier = handle;
        }
        check(supplier != null && supplier.getOwner().equals(owner), "Missing local native fuel supplier");
        String supplierName = supplier.getName(), supplierDesc = supplier.getDesc();
        var factory = items.methods.stream().filter(m -> m.name.equals(supplierName) && m.desc.equals(supplierDesc)).findFirst().orElseThrow();
        var allocations = new ArrayList<String>();
        boolean nativeFuel = false, remainder = false, onePerStack = false;
        for (var instruction : factory.instructions) {
            if (instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) allocations.add(type.desc);
            if (instruction instanceof FieldInsnNode field) {
                nativeFuel |= field.owner.equals("earth/terrarium/adastra/common/registry/ModFluids") && field.name.equals("FUEL_FLUID_TYPE");
                remainder |= field.owner.equals("net/minecraft/world/item/Items") && field.name.equals("BUCKET");
            }
            if (instruction instanceof MethodInsnNode call && call.name.equals("stacksTo")) {
                var previous = instruction.getPrevious();
                while (previous.getOpcode() < 0) previous = previous.getPrevious();
                onePerStack = previous.getOpcode() == Opcodes.ICONST_1;
            }
        }
        check(allocations.equals(List.of(bucket, "net/minecraft/world/item/Item$Properties")), "Fuel bucket exact class changed");
        check(nativeFuel && remainder && onePerStack, "Fuel type/remainder/native stack size changed");
        var nativeBucket = read(bucket);
        check(nativeBucket.superName.equals("net/minecraft/world/item/BucketItem"), "Bucket inheritance changed");
        var constructor = nativeBucket.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElseThrow();
        boolean still = false, nativeConstructor = false;
        for (var instruction : constructor.instructions) if (instruction instanceof MethodInsnNode call) {
            still |= call.owner.equals("com/teamresourceful/resourcefullib/common/fluid/data/FluidData") && call.name.equals("still");
            nativeConstructor |= call.owner.equals("net/minecraft/world/item/BucketItem") && call.name.equals("<init>")
                && call.desc.equals("(Lnet/minecraft/world/level/material/Fluid;Lnet/minecraft/world/item/Item$Properties;)V");
        }
        check(still && nativeConstructor, "Resourceful bucket no longer initializes immutable native fluid");
        check(read("net/minecraft/world/item/BucketItem").fields.stream().anyMatch(f -> f.name.equals("content")
            && f.desc.equals("Lnet/minecraft/world/level/material/Fluid;")
            && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)) == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)),
            "NeoForge immutable fluid read field missing");
        System.out.println("PASS exact native fuel bucket: " + checks + " registration/class/fluid contracts; no game boot.");
    }
}
