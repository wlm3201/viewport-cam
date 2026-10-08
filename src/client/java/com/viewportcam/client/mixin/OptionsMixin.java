package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCamKeys;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code Options.load()} 之前把本模组的按键注册进去，
 * 这样读档时能一并加载玩家改过的按键，也不会被存档覆盖掉。
 */
@Mixin(Options.class)
public class OptionsMixin {

	@Inject(method = "load", at = @At("HEAD"))
	private void viewportcam$registerBeforeLoad(CallbackInfo ci) {
		ViewportCamKeys.register((Options) (Object) this);
	}
}
