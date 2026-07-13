package autismclient.mixin;

import autismclient.gui.screen.AutismStyledButton;
import autismclient.gui.vanillaui.components.Button;
import autismclient.modules.PackAutoReconnectState;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DisconnectedScreen.class)
public abstract class AutismDisconnectedScreenMixin extends Screen {
    @Shadow @Final private Screen parent;

    protected AutismDisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void autism$addReconnectButton(CallbackInfo ci) {
        if (!PackAutoReconnectState.shouldShow()) return;
        if (PackAutoReconnectState.hideButtons()) return;

        int w = 124;
        int gap = 8;
        int h = 20;
        int x = this.width / 2 - (w * 2 + gap) / 2;
        int y = this.height - 38;

        this.addRenderableWidget(new AutismStyledButton(
            x, y, w, h,
            Component.literal("Reconnect Now"),
            Button.Tone.PRIMARY,
            PackAutoReconnectState::reconnectButtonLabel,
            button -> PackAutoReconnectState.reconnect(parent)
        ));

        this.addRenderableWidget(new AutismStyledButton(
            x + w + gap, y, w, h,
            Component.literal("Cancel"),
            Button.Tone.DANGER,
            button -> PackAutoReconnectState.cancel()
        ));
    }
}
