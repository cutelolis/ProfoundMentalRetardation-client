package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class ExternalButtonSkin {
    private ExternalButtonSkin() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, Component message,
                              int x, int y, int width, int height, int mouseX, int mouseY) {
        UiBounds bounds = UiBounds.of(x, y, width, height);
        Button.render(UiContexts.overlay(graphics, font, mouseX, mouseY), bounds, message.getString(),
            Button.Tone.NORMAL, bounds.contains(mouseX, mouseY), true);
    }

    public static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
