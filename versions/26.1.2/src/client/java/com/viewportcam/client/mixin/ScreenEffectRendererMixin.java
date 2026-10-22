package com.viewportcam.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.viewportcam.client.ViewportCam;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void viewportcam$hideInWallOverlay(
            TextureAtlasSprite sprite,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            CallbackInfo ci) {
        if (ViewportCam.shouldHideInWallOverlay()) {
            ci.cancel();
        }
    }
}
