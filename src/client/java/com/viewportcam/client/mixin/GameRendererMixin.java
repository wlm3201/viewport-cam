package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 渲染侧的细节处理：屏蔽方块高亮框、屏蔽走路与受伤的画面晃动 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

	/** 交互被屏蔽时不显示方块描边（否则准星与实际可交互目标不一致） */
	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void viewportcam$hideOutline(CallbackInfoReturnable<Boolean> cir) {
		if (ViewportCam.isInteractionBlocked()) {
			cir.setReturnValue(false);
		}
	}

	/** 轨道相机下取消走路晃动，避免画面“飘” */
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void viewportcam$noViewBob(CallbackInfo ci) {
		if (ViewportCam.isActive()) {
			ci.cancel();
		}
	}

	/** 同上：取消受伤晃动 */
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void viewportcam$noHurtBob(CallbackInfo ci) {
		if (ViewportCam.isActive()) {
			ci.cancel();
		}
	}
}
