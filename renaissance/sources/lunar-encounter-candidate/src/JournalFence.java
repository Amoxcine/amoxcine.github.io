package fr.ascendant.lunar.encounter;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Independent durable acknowledgement: a corrupt WAL must never silently undo a granted receipt. */
final class JournalFence implements AutoCloseable {
    static final int SIZE=160;
    record Stamp(long sequence,byte[] root) {
        Stamp {if(sequence<0||root.length!=32)throw new IllegalArgumentException("Invalid fence stamp");root=root.clone();}
        boolean matches(Stamp other){return sequence==other.sequence&&Arrays.equals(root,other.root);}
    }
    record State(boolean pending,Stamp before,Stamp after) {
        boolean accepts(Stamp actual){return after.matches(actual)||(pending&&before.matches(actual));}
    }
    private final FileChannel file;
    JournalFence(Path path,boolean create)throws IOException {
        file=FileChannel.open(path,create?Set.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
                :Set.of(StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS));
    }
    static State read(Path path)throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=SIZE)throw new IOException("Missing/invalid acknowledgement fence");
        try(var channel=FileChannel.open(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer b=ByteBuffer.allocate(SIZE);while(b.hasRemaining())if(channel.read(b)<0)throw new EOFException();
            try(var in=JournalRecords.open(b.array(),SIZE)) {
                if(in.readInt()!=0x41534633||in.readInt()!=3)throw new IOException("Unknown fence format");
                int flag=in.readUnsignedByte();if(flag>1)throw new IOException("Invalid fence state");
                Stamp before=new Stamp(in.readLong(),in.readNBytes(32)),after=new Stamp(in.readLong(),in.readNBytes(32));JournalRecords.padding(in);
                if(flag==0&&!before.matches(after)||flag==1&&after.sequence()!=before.sequence()+1)throw new IOException("Invalid fence transition");
                return new State(flag==1,before,after);
            }catch(RuntimeException ex){throw new IOException("Invalid fence",ex);}
        }
    }
    void write(State state)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){
            out.writeInt(0x41534633);out.writeInt(3);out.writeBoolean(state.pending());
            out.writeLong(state.before().sequence());out.write(state.before().root());out.writeLong(state.after().sequence());out.write(state.after().root());
        }
        ByteBuffer b=ByteBuffer.wrap(JournalRecords.seal(bytes.toByteArray(),SIZE));file.position(0);while(b.hasRemaining())file.write(b);file.force(true);
    }
    void committed(Stamp stamp)throws IOException {write(new State(false,stamp,stamp));}
    public void close()throws IOException {file.close();}
}
