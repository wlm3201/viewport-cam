package com.viewportcam.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.viewportcam.client.ViewportCam;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void viewportcam$hideInWallOverlay(
            Identifier atlasLocation,
            float u0,
            float v0,
            float u1,
            float v1,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int color,
            CallbackInfo ci) {
        if (ViewportCam.shouldHideInWallOverlay()) {
            ci.cancel();
        }
    }
}
