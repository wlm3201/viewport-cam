package com.viewportcam.client.mixin;

import com.viewportcam.client.ViewportCam;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void viewportcam$blockRange(CallbackInfoReturnable<Double> cir) {
        double reach = ViewportCam.blockRange((Player) (Object) this);
        if (reach > 0.0) {
            cir.setReturnValue(reach);
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void viewportcam$entityRange(CallbackInfoReturnable<Double> cir) {
        double reach = ViewportCam.entityRange((Player) (Object) this);
        if (reach > 0.0) {
            cir.setReturnValue(reach);
        }
    }

    @Redirect(
            method = "isWithinAttackRange",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/entity/player/Player;getAttackRangeWith("
                                            + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/component/AttackRange;"))
    private AttackRange viewportcam$serverAttackRange(Player self, ItemStack weaponItem) {
        AttackRange vanilla = self.getAttackRangeWith(weaponItem);
        double reach = ViewportCam.serverEntityReach(self);
        if (reach <= 0.0) {
            return vanilla;
        }
        float expanded = (float) reach;
        return new AttackRange(
                vanilla.minReach(),
                expanded,
                vanilla.minCreativeReach(),
                expanded,
                vanilla.hitboxMargin(),
                vanilla.mobFactor());
    }
}
