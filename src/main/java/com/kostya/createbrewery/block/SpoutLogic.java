package com.kostya.createbrewery.block;

/**
 * The pure rule behind the spout: a bottle can only be filled when there is both wort
 * to pour and an empty bottle to pour it into.
 */
public final class SpoutLogic {

    private SpoutLogic() {
    }

    /**
     * Whether the spout may fill a bottle.
     *
     * @param hasWort        the kettle (or spout tank) holds enough wort for a bottle
     * @param hasEmptyBottle the player is holding an empty glass bottle
     */
    public static boolean fillBottle(boolean hasWort, boolean hasEmptyBottle) {
        return hasWort && hasEmptyBottle;
    }
}
