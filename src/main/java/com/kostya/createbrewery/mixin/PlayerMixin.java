package com.kostya.createbrewery.mixin;

import com.kostya.createbrewery.drunk.DrunkData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every player its {@link DrunkData} (Forge's {@code DrunkData} capability) and saves the saved part of it
 * with the player under {@link DrunkData#NBT_KEY}. Neither Fabric API nor Porting Lib saves custom player data
 * without a library this mod may not depend on, so this is the one place that does.
 */
@Mixin(Player.class)
public abstract class PlayerMixin implements DrunkData.Holder {

    @Unique
    private final DrunkData brewery$drunk = new DrunkData();

    @Override
    public DrunkData brewery$drunkData() {
        return brewery$drunk;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void brewery$saveDrunk(CompoundTag tag, CallbackInfo ci) {
        tag.put(DrunkData.NBT_KEY, brewery$drunk.save());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void brewery$loadDrunk(CompoundTag tag, CallbackInfo ci) {
        // A player saved before the mod was added (or by another loader) starts sober, as an absent capability did.
        if (tag.contains(DrunkData.NBT_KEY, CompoundTag.TAG_COMPOUND)) {
            brewery$drunk.load(tag.getCompound(DrunkData.NBT_KEY));
        }
    }
}
