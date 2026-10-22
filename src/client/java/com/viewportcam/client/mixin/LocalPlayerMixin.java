package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "applyInput", at = @At("HEAD"), cancellable = true)
    private void viewportcam$freezeBody(CallbackInfo ci) {
        if (ViewportCam.isEngaged()) {
            ci.cancel();
        }
    }

    @Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
    private void viewportcam$pickFromCamera(
            float partialTicks, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this != mc.player) {
            return;
        }
        HitResult hit = ViewportCam.overrideHitResult(mc);
        if (hit != null) {
            cir.setReturnValue(hit);
        }
    }
}
