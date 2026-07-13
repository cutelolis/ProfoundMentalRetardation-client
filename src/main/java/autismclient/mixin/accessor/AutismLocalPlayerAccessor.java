package autismclient.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface AutismLocalPlayerAccessor {
    @Accessor("positionReminder")
    void autism$setPositionReminder(int ticks);
}
