package fr.ascendant.lunar.encounter;

import java.io.*;
import java.security.*;
import java.util.*;

/** Fixed physical slots: all receipt fields are paid for at reservation, not at withdrawal. */
final class JournalRecords {
    static final int RUN_BYTES=2048, ACTOR_BYTES=128;
    record Actor(UUID id, DurableRaidJournal.OwnedActor owner) {}
    private JournalRecords() {}
    static byte[] hash(byte[] data) {
        try{return MessageDigest.getInstance("SHA-256").digest(data);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    static void uuid(DataOutputStream out,UUID id)throws IOException {out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    static UUID uuid(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    static byte[] seal(byte[] content,int size)throws IOException {
        if(content.length>size-32)throw new IOException("Record capacity invalid");
        byte[] result=Arrays.copyOf(content,size),digest=hash(Arrays.copyOf(result,size-32));
        System.arraycopy(digest,0,result,size-32,32);return result;
    }
    static DataInputStream open(byte[] data,int size)throws IOException {
        if(data==null||data.length!=size||!MessageDigest.isEqual(hash(Arrays.copyOf(data,size-32)),Arrays.copyOfRange(data,size-32,size)))
            throw new IOException("Record size/checksum invalid");
        return new DataInputStream(new ByteArrayInputStream(data,0,size-32));
    }
    static void padding(DataInputStream in)throws IOException {while(in.available()>0)if(in.readByte()!=0)throw new IOException("Nonzero record padding");}
    static byte[] run(RewardLedger.Entry e)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(3);uuid(out,e.run());out.writeUTF(e.identity().name());uuid(out,e.group().beneficiary());
            var delegates=e.group().delegates().stream().sorted().toList();out.writeInt(delegates.size());for(UUID id:delegates)uuid(out,id);
            out.writeInt(e.group().roster().size());for(UUID id:e.group().roster())uuid(out,id);
            out.writeInt(e.reservedCoolant());out.writeUTF(e.status().name());out.writeBoolean(e.operation()!=null);
            if(e.operation()!=null){uuid(out,e.operation());uuid(out,e.claimant());}
        }return seal(bytes.toByteArray(),RUN_BYTES);
    }
    static RewardLedger.Entry run(byte[] data)throws IOException {
        try(var in=open(data,RUN_BYTES)){
            if(in.readInt()!=3)throw new IOException("Unknown run record version");
            UUID id=uuid(in);var identity=RaidMachine.Identity.valueOf(in.readUTF());UUID team=uuid(in);
            int nd=count(in,64);Set<UUID> delegates=new HashSet<>();for(int i=0;i<nd;i++)if(!delegates.add(uuid(in)))throw new IOException("Duplicate delegate");
            int np=count(in,8);List<UUID> roster=new ArrayList<>();for(int i=0;i<np;i++)roster.add(uuid(in));
            int coolant=in.readInt();var status=RewardLedger.Status.valueOf(in.readUTF());
            if(identity==RaidMachine.Identity.REGULATOR&&coolant<=0)throw new IOException("Regulator without reserved escrow");
            int flag=in.readUnsignedByte();if(flag>1)throw new IOException("Invalid receipt flag");
            UUID operation=flag==1?uuid(in):null,claimant=flag==1?uuid(in):null;padding(in);
            return new RewardLedger.Entry(id,identity,new RaidMachine.Group(team,delegates,roster),coolant,status,operation,claimant);
        }catch(RuntimeException ex){throw new IOException("Invalid run record",ex);}
    }
    static byte[] actor(UUID id,DurableRaidJournal.OwnedActor actor)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(3);uuid(out,id);uuid(out,actor.run());var p=actor.position();out.writeInt(p.x());out.writeInt(p.y());out.writeInt(p.z());
        }return seal(bytes.toByteArray(),ACTOR_BYTES);
    }
    static Actor actor(byte[] data)throws IOException {
        try(var in=open(data,ACTOR_BYTES)){
            if(in.readInt()!=3)throw new IOException("Unknown actor record version");
            UUID id=uuid(in),run=uuid(in);var p=new DurableRaidJournal.Position(in.readInt(),in.readInt(),in.readInt());padding(in);
            return new Actor(id,new DurableRaidJournal.OwnedActor(run,p));
        }catch(RuntimeException ex){throw new IOException("Invalid actor record",ex);}
    }
    private static int count(DataInputStream in,int max)throws IOException {int n=in.readInt();if(n<1||n>max)throw new IOException("Invalid roster/delegate count");return n;}
}
