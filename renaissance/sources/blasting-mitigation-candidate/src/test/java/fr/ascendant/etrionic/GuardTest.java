package fr.ascendant.etrionic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class GuardTest {
    private static int assertions;
    private static final String TARGET="earth/terrarium/adastra/common/blockentities/machines/EtrionicBlastFurnaceBlockEntity";
    private static final String TICK="recipeTick(Learth/terrarium/common_storage_lib/storage/base/ValueStorage;)V";
    private static final String CRAFT="craft(Lnet/minecraft/world/item/crafting/BlastingRecipe;I)V";
    private static void test(boolean b,String message) { assertions++; if(!b) throw new AssertionError(message); }
    private static ClassNode read(byte[] bytes) { ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);return n; }
    private static Object value(AnnotationNode a,String key) {
        if(a.values!=null) for(int i=0;i<a.values.size();i+=2) if(a.values.get(i).equals(key)) return a.values.get(i+1);
        return null;
    }
    public static void main(String[] args) throws Exception {
        for(boolean active:List.of(false,true)) for(boolean alloying:List.of(false,true)) {
            test(GuardPolicy.cancelTick(active,alloying)==(active&&!alloying),"tick truth table");
            test(GuardPolicy.cancelBlastingCraft(active)==active,"completion truth table");
        }
        ClassNode nativeClass;
        try(JarFile jar=new JarFile(args[1])) {
            try(var in=jar.getInputStream(jar.getJarEntry(TARGET+".class"))) { nativeClass=read(in.readAllBytes()); }
        }
        for(String signature:List.of(TICK,CRAFT)) {
            test(nativeClass.methods.stream().filter(m -> (m.name+m.desc).equals(signature)).count()==1,"exact native target "+signature);
        }
        int blastingCallSites=0,alloyingCallSites=0;
        for(MethodNode m:nativeClass.methods) for(var i:m.instructions) if(i instanceof MethodInsnNode call && call.owner.equals(TARGET)) {
            if(call.name.equals("craft") && (call.name+call.desc).equals(CRAFT)) {
                blastingCallSites++;
                test((m.name+m.desc).equals(TICK),"all native blasting completion calls behind guarded recipeTick");
            }
            if(call.name.equals("alloyingRecipeTick")) {
                alloyingCallSites++;
                test((m.name+m.desc).equals(TICK),"native separate alloy branch");
            }
        }
        test(blastingCallSites==1,"pinned native blasting call site count");
        test(alloyingCallSites==1,"pinned native alloy branch count");
        ClassNode mixin=read(Files.readAllBytes(Path.of(args[0],"fr/ascendant/etrionic/mixin/EtrionicBlastingMixin.class")));
        Set<String> seen=new HashSet<>();
        int cancels=0;
        for(MethodNode m:mixin.methods) {
            List<AnnotationNode> annotations=new ArrayList<>();
            if(m.visibleAnnotations!=null) annotations.addAll(m.visibleAnnotations);
            if(m.invisibleAnnotations!=null) annotations.addAll(m.invisibleAnnotations);
            for(AnnotationNode a:annotations) if(a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                var methods=(List<?>)value(a,"method");
                test(methods.size()==1,"one exact descriptor per hook");
                String signature=(String)methods.getFirst();
                test(Set.of(TICK,CRAFT).contains(signature),"no alloying or unrelated injection");seen.add(signature);
                test(Boolean.TRUE.equals(value(a,"cancellable")),"cancellable");
                test(Integer.valueOf(1).equals(value(a,"require")),"required exact site");
                test(Integer.valueOf(1).equals(value(a,"allow")),"maximum one site");
                var at=(List<?>)value(a,"at");
                test(at.size()==1 && "HEAD".equals(value((AnnotationNode)at.getFirst(),"value")),"HEAD before native effects");
            }
            for(var i:m.instructions) {
                test(!(i instanceof FieldInsnNode f && (f.getOpcode()==Opcodes.PUTFIELD || f.getOpcode()==Opcodes.PUTSTATIC)),"no fixture/cache/inventory fields written by mixin");
                if(i instanceof MethodInsnNode call) {
                    if(call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") && call.name.equals("cancel")) cancels++;
                    boolean allowed=call.owner.equals("java/lang/Object") || call.owner.equals(TARGET)&&call.name.equals("mode")
                        || call.owner.equals("fr/ascendant/etrionic/EtrionicGuard")&&Set.of("active","recordTickVeto","recordCraftVeto").contains(call.name)
                        || call.owner.equals("fr/ascendant/etrionic/GuardPolicy")&&Set.of("cancelTick","cancelBlastingCraft").contains(call.name)
                        || call.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")&&call.name.equals("cancel");
                    test(allowed,"no input/output/FE/craft/air side effect call: "+call.owner+"."+call.name);
                }
            }
        }
        test(seen.equals(Set.of(TICK,CRAFT)),"exactly two intended targets");
        test(cancels==2,"two guarded cancellation sites");
        System.out.println("PASS "+assertions+" policy/native-shape/mixin-bytecode assertions; no Minecraft boot or Mixin application claimed.");
    }
}
