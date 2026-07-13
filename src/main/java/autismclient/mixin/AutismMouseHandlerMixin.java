package autismclient.mixin;

import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismMouseInputSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class AutismMouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique private float autism$turnStartYaw;
    @Unique private float autism$turnStartPitch;
    @Unique private boolean autism$turnHadPlayer;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void autism$clearQueuedMouseInputWhenUnavailable(CallbackInfo ci) {
        AutismMouseInputSimulator.clearIfUnavailable();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void autism$freecamScrollSpeed(long window, double xOffset, double yOffset, CallbackInfo ci) {

        if (autismclient.modules.PackFreecamState.onMouseScroll(yOffset)) ci.cancel();
    }

    @Inject(
        method = "handleAccumulatedMovement",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;turnPlayer(D)V")
    )
    private void autism$applyQueuedRawMouseInput(CallbackInfo ci) {
        AutismMouseInputSimulator.Delta delta = AutismMouseInputSimulator.consume();
        if (delta.isZero()) return;
        accumulatedDX += autism$additiveAssist(accumulatedDX, delta.x());
        accumulatedDY += autism$additiveAssist(accumulatedDY, delta.y());
    }

    @Unique
    private static double autism$additiveAssist(double realInput, double assistInput) {
        if (Math.abs(assistInput) < 1.0E-7) return 0.0;
        if (Math.abs(realInput) < 1.0E-4) return assistInput;
        return Math.signum(realInput) == Math.signum(assistInput) ? assistInput : 0.0;
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void autism$beforeTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!ModuleRegistry.hasMouseRotationHooks()) {
            autism$turnHadPlayer = false;
            return;
        }
        autism$turnHadPlayer = minecraft != null && minecraft.player != null;
        if (autism$turnHadPlayer) {
            autism$turnStartYaw = minecraft.player.getYRot();
            autism$turnStartPitch = minecraft.player.getXRot();
        }
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void autism$afterTurnPlayer(double deltaTime, CallbackInfo ci) {
        if (!autism$turnHadPlayer || minecraft == null || minecraft.player == null) return;
        double deltaYaw = minecraft.player.getYRot() - autism$turnStartYaw;
        double deltaPitch = minecraft.player.getXRot() - autism$turnStartPitch;
        if (Math.abs(deltaYaw) > 1.0E-6 || Math.abs(deltaPitch) > 1.0E-6) {
            ModuleRegistry.onMouseRotation(deltaYaw, deltaPitch);
        }
    }
}
