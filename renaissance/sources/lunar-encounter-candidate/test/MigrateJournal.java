package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Explicit offline, resumable import. Never opens the v1 source for writing. */
// Retained only for journal regression fixtures; not shipped in the lunar runtime.
public final class MigrateJournal {
    private MigrateJournal() {}
    static String hash(Path path)throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var in=new DigestInputStream(Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS),digest)){byte[] buffer=new byte[65536];while(in.read(buffer)>=0){}}
        return HexFormat.of().formatHex(digest.digest());
    }
    public static void migrate(Path source,Path target)throws Exception {
        migrate(source,target,point->{});
    }
    static void migrate(Path source,Path target,SqliteRaidJournal.Fault fault)throws Exception {
        try(var legacy=DurableRaidJournal.readOnly(source)) {
            String hash=hash(source);
            try(var next=new SqliteRaidJournal(target,hash)) {
                next.setFaultForTests(fault);
                for(var entry:legacy.entries())next.importEntry(entry);
                for(var actor:legacy.actors().entrySet())next.importActor(actor.getKey(),actor.getValue());
                next.finishMigration(legacy.entries().size(),legacy.actors().size());
            }
            if(!hash.equals(hash(source)))throw new IOException("Legacy source changed while locked");
        }
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=5||!args[0].equals("--from-v1")||!args[2].equals("--to-v3")||!args[4].equals("--confirm-offline"))
            throw new IllegalArgumentException("--from-v1 <file> --to-v3 <new-directory> --confirm-offline; server must be stopped");
        migrate(Path.of(args[1]),Path.of(args[3]));
        System.out.println("Migration v1 -> v3 complete. Source preserved. No automatic downgrade; back up world, players and store together.");
    }
}
