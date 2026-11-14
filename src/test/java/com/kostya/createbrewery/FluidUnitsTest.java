package com.kostya.createbrewery;

import com.kostya.createbrewery.distillery.DistilleryLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Logic and config count in mB, tanks and the Transfer API in droplets: the bridge must be exact. */
class FluidUnitsTest {

    @Test
    void eightyOneDropletsPerMillibucket() {
        assertEquals(81, FluidUnits.DROPLETS_PER_MB);
        assertEquals(81000, FluidUnits.toDroplets(1000));
        assertEquals(324000, FluidUnits.toDroplets(4000));
    }

    @Test
    void dropletsToMillibucketsRoundDown() {
        assertEquals(1000, FluidUnits.toMb(81000));
        assertEquals(999, FluidUnits.toMb(80999));
        assertEquals(0, FluidUnits.toMb(80));
    }

    /** Kettle tank 4000 mB; wort per brew and bottle, muddy and distillate 1000 mB each. */
    @Test
    void everyVolumeTheModUsesRoundTrips() {
        int[] volumes = {4000, 1000, DistilleryLogic.WORT_REQUIRED, DistilleryLogic.DISTILLATE_AMOUNT};
        for (int mb : volumes) {
            assertEquals(mb, FluidUnits.toMb(FluidUnits.toDroplets(mb)), mb + " mB");
        }
    }
}
