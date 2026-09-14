package fr.ascendant.lunar.travel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import static java.nio.file.StandardOpenOption.*;

/** Server-owned native fuel receipt. Never contains item payloads or creates replacement vehicles. */
public final class FlightJournal {
    public enum Phase { READY, TRANSFERRING, COMPLETE }
    public record Receipt(UUID player, UUID rocket, UUID nonce, String source, Phase phase) {
        public boolean matches(UUID player, UUID rocket, String source) {
            return this.player.equals(player) && this.rocket.equals(rocket) && this.source.equals(source);
        }
    }
    private final Path root;
    public FlightJournal(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public synchronized Receipt read(UUID player) throws IOException {
        Path path = root.resolve(player + ".flight");
        if (Files.notExists(path)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 512) throw new IOException("Invalid flight receipt");
        try {
            var lines = Files.readAllLines(path, StandardCharsets.US_ASCII);
            if (lines.size() != 7 || !lines.get(0).equals("lunar-flight-v1") || !lines.get(6).equals("end")) throw new IllegalArgumentException();
            var result = new Receipt(UUID.fromString(lines.get(1)), UUID.fromString(lines.get(2)), UUID.fromString(lines.get(3)),
                lines.get(4), Phase.valueOf(lines.get(5)));
            if (!result.player.equals(player) || !TravelPolicy.launchWorld(result.source)) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException invalid) { throw new IOException("Corrupt flight receipt", invalid); }
    }
    private void write(Receipt record) throws IOException {
        Files.createDirectories(root);
        Path temp = root.resolve(record.player + "." + UUID.randomUUID() + ".tmp");
        byte[] bytes = ("lunar-flight-v1\n" + record.player + "\n" + record.rocket + "\n" + record.nonce + "\n"
            + record.source + "\n" + record.phase + "\nend\n").getBytes(StandardCharsets.US_ASCII);
        try (var channel = FileChannel.open(temp, CREATE_NEW, WRITE, SYNC)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        // No non-atomic fallback: failure retains the previous authoritative receipt.
        Files.move(temp, root.resolve(record.player + ".flight"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    synchronized Receipt paid(UUID player, UUID rocket, String source) throws IOException {
        if (!TravelPolicy.launchWorld(source)) throw new IOException("Unsupported native source");
        var receipt = new Receipt(player, rocket, UUID.randomUUID(), source, Phase.READY);
        write(receipt); return receipt;
    }
    synchronized void phase(Receipt expected, Phase phase) throws IOException {
        if (!expected.equals(read(expected.player))) throw new IOException("Stale flight receipt");
        if (expected.phase == Phase.COMPLETE) throw new IOException("Completed flight cannot be replayed");
        write(new Receipt(expected.player, expected.rocket, expected.nonce, expected.source, phase));
    }
}
