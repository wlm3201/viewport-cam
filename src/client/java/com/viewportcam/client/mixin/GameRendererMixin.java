package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void viewportcam$hideOutline(CallbackInfoReturnable<Boolean> cir) {
        if (ViewportCam.isInteractionBlocked()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void viewportcam$noViewBob(CallbackInfo ci) {
        if (ViewportCam.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void viewportcam$noHurtBob(CallbackInfo ci) {
        if (ViewportCam.isActive()) {
            ci.cancel();
        }
    }
}
