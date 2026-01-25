package com.stepcraft.mixin;

import com.stepcraft.StepCraftSignSearch;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class StepCraftSignUpdateMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onUpdateSign", at = @At("HEAD"), cancellable = true)
    private void onUpdateSign(UpdateSignC2SPacket packet, CallbackInfo ci) {
        if (StepCraftSignSearch.handleSignUpdate(player, packet)) {
            ci.cancel();
        }
    }
}
