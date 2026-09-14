package fr.ascendant.lunar.encounter;

import java.io.*;
import java.util.*;
import java.util.function.Supplier;

public final class JoinAdmissionTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Join check " + checks); }
    private static void scenario(boolean marked, UUID marker, boolean active, boolean home, boolean readable,
            UUID owner, boolean io, boolean cancelExpected, boolean discardExpected, boolean closeExpected) {
        boolean[] canceled={false}, discarded={false}, closed={false}; int[] reads={0};
        Supplier<UUID> lookup=()->{reads[0]++;if(io)throw new UncheckedIOException(new IOException("lookup I/O"));return owner;};
        EncounterSafety.contain(()->EncounterSafety.recoverEntityJoin(marked,marker,active,home,readable,
                lookup,()->canceled[0]=true,()->discarded[0]=true),e->closed[0]=true);
        check(canceled[0]==cancelExpected);check(discarded[0]==discardExpected);check(closed[0]==closeExpected);
        check(reads[0]==(readable?1:0));
    }
    public static void main(String[] args) {
        UUID run=UUID.randomUUID();
        scenario(true,run,false,true,true,null,true,true,false,true);
        scenario(false,null,false,true,true,null,true,false,false,true);
        scenario(false,null,true,true,true,null,true,true,false,true);
        scenario(true,run,false,true,false,null,false,true,false,false);
        scenario(false,null,false,true,false,null,false,false,false,false);
        scenario(false,null,true,true,false,null,false,true,false,false);
        scenario(true,run,false,true,true,null,false,true,false,true);
        scenario(true,null,false,true,true,null,false,true,false,true);
        scenario(false,null,false,true,true,null,false,false,false,false);
        scenario(true,run,false,true,true,run,false,true,true,false);
        scenario(true,run,true,true,true,run,false,false,false,false);
        scenario(true,run,true,false,true,run,false,true,true,false);
        scenario(true,UUID.randomUUID(),false,true,true,run,false,true,false,true);
        scenario(false,null,false,true,true,run,false,true,false,true);
        System.out.println("PASS join admission: "+checks+" assertions; lookup I/O, unknown/malformed marker, ordinary entities, active and late recovery");
    }
}
