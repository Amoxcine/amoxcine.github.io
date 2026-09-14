package fr.ascendant.lunar.travel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static java.nio.file.StandardOpenOption.*;

/** Append-only player records. Pending survives errors/crashes; no reset/grant API. */
public final class ArrivalLedger {
    private final Path root;
    public ArrivalLedger(Path root) { this.root = root.toAbsolutePath().normalize(); }
    private Path path(UUID player, String state) { return root.resolve(player + "." + state); }
    private byte[] content(UUID player, String state) {
        return ("lunar-arrival-v1\n" + player + "\n" + state + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    private boolean exists(UUID player, String state) throws IOException {
        Path path = path(player, state);
        if (Files.notExists(path)) return false;
        byte[] expected = content(player, state);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != expected.length
                || !java.util.Arrays.equals(Files.readAllBytes(path), expected))
            throw new IOException("Invalid lunar arrival record: " + player);
        return true;
    }
    public synchronized boolean fresh(UUID player) throws IOException {
        return !exists(player, "pending") && !exists(player, "arrived");
    }
    private void write(UUID player, String state) throws IOException {
        Files.createDirectories(root);
        try (var channel = FileChannel.open(path(player, state), CREATE_NEW, WRITE, SYNC)) {
            var buffer = ByteBuffer.wrap(content(player, state));
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }
    public synchronized boolean begin(UUID player) throws IOException {
        if (!fresh(player)) return false;
        write(player, "pending");
        return true;
    }
    public synchronized boolean completed(UUID player) throws IOException { return exists(player, "arrived"); }
    /** Only a proved untouched native veto may release a reservation; never an arrived record. */
    synchronized void refusedIntact(UUID player) throws IOException {
        if (exists(player, "arrived")) throw new IOException("Cannot release a completed arrival");
        if (exists(player, "pending")) Files.delete(path(player, "pending"));
    }
    /** Called only after a successful native surface landing, never at launch/quest completion. */
    public synchronized void arrived(UUID player) throws IOException {
        if (exists(player, "arrived")) return;
        if (!exists(player, "pending")) throw new IOException("No first-arrival reservation");
        write(player, "arrived");
    }
}
