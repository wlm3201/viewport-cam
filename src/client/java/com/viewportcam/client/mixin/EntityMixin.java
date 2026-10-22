package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(
            method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true)
    private void viewportcam$eyeAtCamera(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this != mc.player) {
            return;
        }
        Vec3 eye = ViewportCam.cameraEye();
        if (eye != null) {
            cir.setReturnValue(eye);
        }
    }

    @Inject(
            method = "getViewVector(F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"),
            cancellable = true)
    private void viewportcam$lookAtPointer(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this != mc.player) {
            return;
        }
        Vec3 ray = ViewportCam.pointerViewRay();
        if (ray != null) {
            cir.setReturnValue(ray);
        }
    }
}
