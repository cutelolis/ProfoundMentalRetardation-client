package autismclient.util;

import autismclient.gui.screen.AutismModuleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookSignScreen;

public final class AutismInputGate {
    private static final Minecraft MC = Minecraft.getInstance();

    private AutismInputGate() {
    }

    public static boolean canRunAutismKeybinds() {
        AutismConfig config = AutismConfig.getGlobal();
        if (MC == null) return false;
        if (MC.gui.screen() == null) return true;
        if (config == null || !config.keybindInsideGui) return false;
        if (MC.gui.screen() instanceof ChatScreen || MC.gui.screen() instanceof InBedChatScreen) return false;
        if (MC.gui.screen() instanceof AbstractSignEditScreen) return false;
        if (MC.gui.screen() instanceof BookEditScreen || MC.gui.screen() instanceof BookSignScreen) return false;
        GuiEventListener focused = MC.gui.screen().getFocused();
        if (focused instanceof EditBox) return false;
        if (AutismOverlayManager.get().isAnyTextFieldFocused()) return false;
        if (MC.gui.screen() instanceof AutismModuleScreen moduleScreen) {
            return !moduleScreen.blocksGlobalKeybinds();
        }
        return true;
    }
}
