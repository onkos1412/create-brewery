package com.kostya.createbrewery;

/**
 * The bridge between the mod's millibuckets and Fabric's droplets.
 *
 * <p>Rules, recipes and config count in mB, as on Forge, so every number the player sees is unchanged. Tanks and
 * the Transfer API (and so Create Fabric's pipes, spouts and basins) count in droplets, 81000 to the bucket.
 * Conversion happens only at that boundary, with the exact factor of 81 droplets per mB.
 */
public final class FluidUnits {
    /** 81000 droplets to the bucket, 1000 mB to the bucket. */
    public static final long DROPLETS_PER_MB = 81;

    private FluidUnits() {
    }

    /** Exact: every whole mB is a whole number of droplets. */
    public static long toDroplets(int mb) {
        return mb * DROPLETS_PER_MB;
    }

    /** Whole mB in {@code droplets}, rounded down: a part-filled mB does not count as one. */
    public static long toMb(long droplets) {
        return Math.floorDiv(droplets, DROPLETS_PER_MB);
    }
}
