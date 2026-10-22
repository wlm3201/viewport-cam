package com.viewportcam.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.viewportcam.client.ViewportCam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Redirect(
            method = "handleKeybinds",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean viewportcam$allowHoldMining(MouseHandler handler) {
        if (handler.isMouseGrabbed()) {
            return true;
        }
        return ViewportCam.isEngaged()
                && !ViewportCam.isCameraHeld(InputConstants.MOUSE_BUTTON_LEFT);
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void viewportcam$stopHoldPlacing(CallbackInfo ci) {
        if (ViewportCam.isCameraHeld(InputConstants.MOUSE_BUTTON_RIGHT)) {
            ci.cancel();
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void viewportcam$stopOnLevelChange(ClientLevel level, CallbackInfo ci) {
        ViewportCam.stopForWorldChange("切换世界/维度");
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void viewportcam$stopOnDisconnect(
            Screen screen, boolean pauseScreenShown, boolean isRealDisconnect, CallbackInfo ci) {
        ViewportCam.stopForWorldChange("退出存档/断线");
    }
}
