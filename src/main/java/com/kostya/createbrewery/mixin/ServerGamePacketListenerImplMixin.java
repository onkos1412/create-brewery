package com.kostya.createbrewery.mixin;

import com.kostya.createbrewery.drunk.DrunkEvents;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forge's {@code ServerChatEvent#setMessage} for {@link DrunkEvents#slurChat}: a drunk player's plain chat message
 * is slurred, and nothing else is. Fabric's {@code ServerMessageDecoratorEvent} would also reach {@code /me},
 * {@code /say}, {@code /msg} and {@code /teammsg}; this private method is the plain-chat path only (called once
 * {@code handleChat} has decorated and filtered the message), before the player list broadcasts it.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @ModifyVariable(method = "broadcastChatMessage", at = @At("HEAD"), argsOnly = true)
    private PlayerChatMessage brewery$slurChat(PlayerChatMessage message) {
        return DrunkEvents.slurChat(player, message);
    }
}
