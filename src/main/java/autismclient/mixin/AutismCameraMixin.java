package autismclient.mixin;

import autismclient.modules.ModuleRenderUtil;
import autismclient.modules.PackFreecamState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class AutismCameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(
        method = "update(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER)
    )
    private void autism$freecamCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!PackFreecamState.isActive()) return;
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        detached = true;
        setPosition(new Vec3(PackFreecamState.getX(partialTicks), PackFreecamState.getY(partialTicks), PackFreecamState.getZ(partialTicks)));
        setRotation(PackFreecamState.getYaw(partialTicks), PackFreecamState.getPitch(partialTicks));
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void autism$disableSmartCull(CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci) {
        if (ModuleRenderUtil.shouldBypassOcclusionCulling()) {
            cameraState.smartCull = false;
        }
    }
}
