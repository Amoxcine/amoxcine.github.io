package fr.ascendant.quarryguard.core;

/** Closed chunk rectangle. Both axes include their minimum and maximum. */
public record ChunkRect(int minX, int minZ, int maxX, int maxZ) {
    public ChunkRect {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted rectangle");
        }
        try {
            Math.multiplyExact((long) maxX - minX + 1, (long) maxZ - minZ + 1);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Chunk area exceeds Long.MAX_VALUE", overflow);
        }
    }

    /** Converts closed block bounds, rejecting inversions before rounding. */
    public static ChunkRect fromBlocks(int minX, int minZ, int maxX, int maxZ) {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted block rectangle");
        }
        return new ChunkRect(Math.floorDiv(minX, 16), Math.floorDiv(minZ, 16),
                Math.floorDiv(maxX, 16), Math.floorDiv(maxZ, 16));
    }

    public long width() {
        return (long) maxX - minX + 1;
    }

    public long depth() {
        return (long) maxZ - minZ + 1;
    }

    public long area() {
        return Math.multiplyExact(width(), depth());
    }

    public boolean contains(int x, int z) {
        return minX <= x && x <= maxX && minZ <= z && z <= maxZ;
    }
}
