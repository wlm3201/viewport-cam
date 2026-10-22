package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportZoom;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void viewportcam$captureZoomKey(
            final long handle, final int action, final KeyEvent event, CallbackInfo ci) {
        if (ViewportZoom.consumeZoomKey(action, event)) {
            ci.cancel();
        }
    }
}
