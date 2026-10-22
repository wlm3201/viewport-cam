package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCamKeys;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public class OptionsMixin {

    @Inject(method = "load", at = @At("HEAD"))
    private void viewportcam$registerBeforeLoad(CallbackInfo ci) {
        ViewportCamKeys.register((Options) (Object) this);
    }
}
