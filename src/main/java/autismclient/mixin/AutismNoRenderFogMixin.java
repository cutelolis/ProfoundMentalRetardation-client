package autismclient.mixin;

import autismclient.modules.NoRenderState;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FogRenderer.class)
public abstract class AutismNoRenderFogMixin {
    @ModifyExpressionValue(
        method = "getBuffer",
        at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;fogEnabled:Z", opcode = Opcodes.GETSTATIC),
        require = 0)
    private boolean autism$noFog(boolean original) {
        return original && !NoRenderState.noFog();
    }
}
