package com.viewportcam.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.viewportcam.client.ViewportHud;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {

    @WrapMethod(method = "extractCrosshair")
    private void viewportcam$shiftCrosshair(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, Operation<Void> original) {
        boolean skip = ViewportHud.beginCrosshair(graphics);
        try {
            if (!skip) {
                original.call(graphics, deltaTracker);
            }
        } finally {
            ViewportHud.endCrosshair(graphics);
        }
    }

    @Redirect(
            method = "extractCrosshair",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"))
    private CameraType viewportcam$crosshairPerspective(Options options) {
        return ViewportHud.crosshairPerspective(options);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void viewportcam$statusHud(
            GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ViewportHud.submitStatusText(graphics);
    }
}
