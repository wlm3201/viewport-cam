package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportHud;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.1.2 的界面注入点：这一版职责还在 {@code Gui} 里，实现委托给 {@link ViewportHud} */
@Mixin(Gui.class)
public class GuiMixin {

	/**
	 * 整体包一层而不是 HEAD/RETURN 配对：第三方 mod 若在 HEAD 无条件 {@code ci.cancel()}，
	 * 方法体走不到 RETURN，还原逻辑就不会执行，push 的位姿会泄漏给后续整个 HUD。
	 * 详见 {@code src/client/java/com/viewportcam/client/ViewportHud.java} 的说明。
	 */
	@WrapMethod(method = "extractCrosshair")
	private void viewportcam$shiftCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
			Operation<Void> original) {
		boolean skip = ViewportHud.beginCrosshair(graphics);
		try {
			if (!skip) {
				original.call(graphics, deltaTracker);
			}
		} finally {
			ViewportHud.endCrosshair(graphics);
		}
	}

	@Redirect(method = "extractCrosshair",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getCameraType()Lnet/minecraft/client/CameraType;"))
	private CameraType viewportcam$crosshairPerspective(Options options) {
		return ViewportHud.crosshairPerspective(options);
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void viewportcam$statusHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		ViewportHud.submitStatusText(graphics);
	}
}
