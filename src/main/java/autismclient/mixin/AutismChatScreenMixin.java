package autismclient.mixin;

import autismclient.commands.AutismCommands;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class AutismChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void yang$onSendMessage(String message, boolean addToHistory, CallbackInfo ci) {
        if (message == null) return;
        String trimmed = message.trim();
        if (trimmed.isEmpty()) return;
        if (AutismCommands.isBlockedPanicCommandMessage(trimmed)) {
            ci.cancel();
            return;
        }

        if ("^toggleautism".equalsIgnoreCase(trimmed)) {
            if (PackHideState.isActive()) {
                ci.cancel();
                return;
            }
            AutismModule module = AutismModule.get();
            module.toggle();
            Minecraft mc = Minecraft.getInstance();
            AutismClientMessaging.sendPrefixed("Autism is now " + (module.isActive() ? "enabled" : "disabled") + ".");
            mc.gui.setScreen(null);
            ci.cancel();
        }
    }
}
