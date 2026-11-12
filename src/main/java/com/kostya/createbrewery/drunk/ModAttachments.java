package com.kostya.createbrewery.drunk;

import com.kostya.createbrewery.CreateBrewery;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The player attachment that stores the drunk counter, persisted across sessions and
 * synced to the owning client so it can distort the screen.
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, CreateBrewery.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DrunkLevel>> DRUNK_LEVEL =
            ATTACHMENTS.register("drunk_level", () -> AttachmentType.builder(() -> new DrunkLevel(0))
                    .serialize(Codec.INT.xmap(DrunkLevel::new, DrunkLevel::get))
                    .sync(StreamCodec.of(
                            (buf, level) -> ByteBufCodecs.VAR_INT.encode(buf, level.get()),
                            buf -> new DrunkLevel(ByteBufCodecs.VAR_INT.decode(buf))))
                    .build());

    /** Ticks of blackout left. Saved, so logging out does not sober a player up. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> KNOCKOUT_TICKS =
            ATTACHMENTS.register("knockout_ticks", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .sync(ByteBufCodecs.VAR_INT)
                    .build());

    /** Hangover debt built up by drinking, settled when the player sobers up. Saved, not synced. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> HANGOVER_DEBT =
            ATTACHMENTS.register("hangover_debt", () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .build());

    private ModAttachments() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
