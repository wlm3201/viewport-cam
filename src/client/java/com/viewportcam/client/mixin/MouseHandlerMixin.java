package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import com.viewportcam.client.ViewportZoom;
import com.viewportcam.client.compat.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 3000)
public class MouseHandlerMixin {

    @Shadow private double accumulatedDX;

    @Shadow private double accumulatedDY;

    @Inject(
            method = "onButton",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            shift = At.Shift.BEFORE,
                            target =
                                    "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"))
    private void viewportcam$onButton(
            long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || handle != mc.getWindow().handle()) {
            return;
        }
        if (Platform.hasScreen(mc) || Platform.hasOverlay(mc)) {
            return;
        }

        ViewportCam.refreshModifier();

        int button = buttonInfo.button();
        if (action == Platform.pressAction()) {
            if (ViewportCam.shouldCaptureMousePress(button)) {
                ViewportCam.markCameraHeld(button);
                ViewportCam.noteHybridPress(button);
                ci.cancel();
            }
        } else if (action == Platform.releaseAction()) {
            ViewportCam.clearCameraHeld(button);
            if (ViewportCam.consumeHybridClick(button)) {
                ViewportCam.replayVanillaClick(button);
            }
        }
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void viewportcam$keepCursorFree(CallbackInfo ci) {
        if (ViewportCam.isEngaged()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onScroll",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            shift = At.Shift.AFTER,
                            target =
                                    "Lnet/minecraft/client/ScrollWheelHandler;onMouseScroll(DD)Lorg/joml/Vector2i;"))
    private void viewportcam$onScroll(
            long handle, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || handle != mc.getWindow().handle()) {
            return;
        }
        if (Platform.hasScreen(mc) || Platform.hasOverlay(mc)) {
            return;
        }
        ViewportCam.refreshModifier();
        if (ViewportCam.onScroll(yOffset)) {
            ci.cancel();
        }
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void viewportcam$turnPlayer(double mousea, CallbackInfo ci) {
        if (!ViewportCam.isEngaged()) {
            if (accumulatedDX != 0.0 || accumulatedDY != 0.0) {
                ViewportZoom.notePlayerTurn();
            }
            return;
        }
        ci.cancel();
        ViewportCam.applyLookDelta(accumulatedDX, accumulatedDY);
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void viewportcam$captureFreeCursor(CallbackInfo ci) {
        MouseHandler self = (MouseHandler) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (Platform.hasScreen(mc) || Platform.hasOverlay(mc)) {
            return;
        }
        if (ViewportCam.isEngaged() && !self.isMouseGrabbed()) {
            ViewportCam.applyLookDelta(accumulatedDX, accumulatedDY);
        }
    }
}
