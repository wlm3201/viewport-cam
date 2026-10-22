package com.viewportcam.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.viewportcam.client.PivotOutline;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "submitBlockDestroyAnimation", at = @At("RETURN"))
    private void viewportcam$submitPivotOutline(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LevelRenderState levelRenderState,
            CallbackInfo ci) {
        PivotOutline.submit(levelRenderState);
    }
}
