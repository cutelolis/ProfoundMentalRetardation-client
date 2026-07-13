package autismclient.mixin;

import autismclient.modules.ModuleMovementUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class AutismLivingEntityMovementMixin {
    @Inject(method = "travelInFluid", at = @At("RETURN"))
    private void autism$restoreLiquidSpeed(Vec3 input, CallbackInfo ci) {
        ModuleMovementUtil.applySpeedAfterLiquidTravel((LivingEntity) (Object) this);
    }
}
