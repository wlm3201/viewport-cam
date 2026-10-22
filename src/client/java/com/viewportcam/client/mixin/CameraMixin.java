package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import com.viewportcam.client.ViewportZoom;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("RETURN"))
    private void viewportcam$applyViewport(float partialTicks, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        ViewportCam.recordVanillaPose(self.position(), self.yRot(), self.xRot());
        ViewportCam.update();
        if (!ViewportCam.isActive()) {
            return;
        }

        ViewportCam.CameraPose pose = ViewportCam.computePose();
        if (pose == null) {
            return;
        }

        this.setRotation(pose.yaw(), pose.pitch());
        this.setPosition(pose.position());
        ViewportCam.syncPlayerRotation(pose.yaw(), pose.pitch());
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void viewportcam$applyZoomFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        ViewportZoom.noteVanillaFov(cir.getReturnValue());
        if (!ViewportCam.isEngaged()) {
            return;
        }
        double scale = ViewportZoom.zoomFovScale();
        if (scale != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValue() * scale));
        }
    }

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void viewportcam$hideFluidFog(CallbackInfoReturnable<FogType> cir) {
        if (ViewportCam.isEngaged()) {
            cir.setReturnValue(FogType.NONE);
        }
    }
}
