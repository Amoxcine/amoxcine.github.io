package fr.ascendant.renaissance.mekanism;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Static contract checks. This does not load Minecraft or claim a live Mixin/runtime test. */
public final class MekBytecodeTest {
    private static final String QE = "mekanism/common/tile/TileEntityQuantumEntangloporter";
    private static final String FREQ = "mekanism/common/content/entangloporter/InventoryFrequency";
    private static final String SIDE = "(Lnet/minecraft/core/Direction;)Ljava/util/List;";
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        check(hash.equals("004dbc9f3106f4d192aeaa1ee1190dd16ec9ca8059ed3d093b80034f4c574f43"), "wrong Mekanism binary");
        try (var zip = new ZipFile(jar.toFile())) {
            var qe = read(zip, QE);
            var freq = read(zip, FREQ);
            var getFreq = method(qe, "getFreq", "()L" + FREQ + ";");
            check(returns(getFreq, Opcodes.ARETURN) == 1, "getFreq return shape changed");
            check(calls(method(qe, "hasFrequency", "()Z"), QE, "getFreq") == 1, "hasFrequency no longer dynamic");
            var update = method(qe, "onUpdateServer", "()Z");
            check(calls(update, QE, "getFreq") == 1, "local tick frequency lookup changed");
            check(calls(update, FREQ, "handleEject") == 1, "local/global eject path changed");
            check(calls(update, QE, "updateHeatCapacitors") == 1 && calls(update, QE, "simulate") == 1,
                "native heat update path changed");
            check(calls(method(qe, "getAdjacent", "(Lnet/minecraft/core/Direction;)Lmekanism/api/heat/IHeatHandler;"),
                QE, "hasFrequency") == 1, "heat adjacency no longer gated by hasFrequency");
            check(calls(method(freq, "handleEject", "(J)V"), QE, "canFunction") == 1,
                "critical global eject redirect cardinality changed");
            for (int i = 0; i < 5; i++) {
                var lambda = method(qe, "lambda$new$" + i, "()Ljava/util/List;");
                check(calls(lambda, QE, "hasFrequency") == 1 && calls(lambda, QE, "getFreq") == 1,
                    "proxied slot supplier no longer dynamic: " + i);
            }
            check(calls(method(qe, "lambda$new$5", "(Lmekanism/common/lib/transmitter/TransmissionType;)Z"),
                QE, "hasFrequency") == 1, "item ejection no longer checks hasFrequency");
            String[] kinds = {"slot", "fluid", "chemical", "energy", "heat"};
            String[] holders = {"InventorySlot", "FluidTank", "ChemicalTank", "EnergyContainer", "HeatCapacitor"};
            String[] getters = {"getInventorySlots", "getTanks", "getTanks", "getEnergyContainers", "getHeatCapacitors"};
            for (int i = 0; i < kinds.length; i++) {
                var holder = read(zip, "mekanism/common/capabilities/holder/" + kinds[i]
                    + "/QuantumEntangloporter" + holders[i] + "Holder");
                var get = method(holder, getters[i], SIDE);
                check(calls(get, QE, "hasFrequency") == 1 && calls(get, QE, "getFreq") == 1,
                    "holder no longer reads endpoint: " + kinds[i]);
            }
            var manager = read(zip, "mekanism/common/capabilities/resolver/manager/CapabilityHandlerManager");
            check(calls(method(manager, "getContainers", SIDE), "java/util/function/BiFunction", "apply") == 1,
                "manager caches containers instead of calling holder");
            var energyManager = read(zip, "mekanism/common/capabilities/resolver/manager/EnergyHandlerManager");
            check(calls(method(energyManager, "getContainers", SIDE),
                "mekanism/common/capabilities/holder/energy/IEnergyContainerHolder", "getEnergyContainers") == 1,
                "energy manager no longer reads current holder");
            for (String proxy : List.of("ProxyItemHandler", "ProxyFluidHandler", "ProxyChemicalHandler",
                    "ProxyStrictEnergyHandler", "ProxyHeatHandler")) {
                var node = read(zip, "mekanism/common/capabilities/proxy/" + proxy);
                check(node.fields.stream().noneMatch(field -> field.desc.equals("Ljava/util/List;")
                    || field.desc.equals("L" + FREQ + ";")), "proxy now caches raw list/frequency: " + proxy);
                check(node.fields.stream().anyMatch(field -> field.desc.startsWith("Lmekanism/api/")
                    && field.desc.contains("ISided")), "proxy lost sided endpoint reference: " + proxy);
            }
            var heat = read(zip, "mekanism/common/capabilities/proxy/ProxyHeatHandler");
            check(heat.fields.stream().anyMatch(field -> field.name.equals("heatHandler")
                && field.desc.equals("Lmekanism/api/heat/ISidedHeatHandler;") && (field.access & Opcodes.ACC_FINAL) != 0),
                "cached heat shadow target changed");
            method(heat, "getTotalInverseConduction", "()D");
            method(heat, "getInverseConduction", "(I)D");
            var heatTile = read(zip, "mekanism/common/capabilities/heat/ITileHeatHandler");
            check(calls(method(heatTile, "simulateAdjacent", "()D"), "mekanism/api/heat/IHeatHandler",
                "getTotalInverseConduction") == 1, "neighbor thermal resistance formula path changed");
            var chemical = read(zip, "mekanism/common/capabilities/proxy/ProxyChemicalHandler");
            check(calls(method(chemical, "getTanksIfMekanism", "()Ljava/util/List;"),
                "mekanism/api/chemical/IMekanismChemicalHandler", "getChemicalTanks") == 1,
                "nested chemical escape proof changed; re-audit instead of silently discarding the blocker");
            System.out.println("MEK STATIC PASS " + assertions + " contracts; live transformation and reservoir tests NOT run");
            System.out.println("Chemical facade added; runtime coverage and other nested container types remain unqualified");
        }
    }

    private static ClassNode read(ZipFile zip, String name) throws Exception {
        var entry = zip.getEntry(name + ".class");
        check(entry != null, "missing class " + name);
        var node = new ClassNode();
        try (var stream = zip.getInputStream(entry)) { new ClassReader(stream).accept(node, 0); }
        return node;
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        var found = owner.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).toList();
        check(found.size() == 1, "missing/ambiguous method " + owner.name + "." + name + descriptor);
        return found.getFirst();
    }

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var instruction : method.instructions)
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        return count;
    }

    private static long returns(MethodNode method, int opcode) {
        long count = 0;
        for (var instruction : method.instructions) if (instruction.getOpcode() == opcode) count++;
        return count;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
