package com.butchercraft.world.identity;

public final class WorldIdentityDeterminism {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private WorldIdentityDeterminism() {
    }

    public static long stableScore(long seed, long salt, String... stableParts) {
        long value = seed ^ salt;
        for (String part : stableParts) {
            value ^= hashString(part);
            value = mix64(value);
        }
        return mix64(value);
    }

    public static int stableIndex(long seed, long salt, int bound, String... stableParts) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }
        return (int) Long.remainderUnsigned(stableScore(seed, salt, stableParts), bound);
    }

    public static long mix64(long value) {
        long mixed = value + 0x9e3779b97f4a7c15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private static long hashString(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
