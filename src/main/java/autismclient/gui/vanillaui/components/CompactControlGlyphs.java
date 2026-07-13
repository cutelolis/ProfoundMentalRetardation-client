package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.components.CompactSymbolButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class CompactControlGlyphs {
    public enum ChevronDirection {
        RIGHT,
        DOWN,
        UP
    }

    private CompactControlGlyphs() {
    }

    public static void drawClose(GuiGraphicsExtractor context, int x, int y, int size, int color, int shadowColor, float alpha) {
        CompactSymbolButton.renderGlyph(context, CompactSymbolButton.minecraftFont(), UiBounds.of(x, y, size, size),
            CompactSymbolButton.CLOSE, color, alpha);
    }

    public static void drawChevron(GuiGraphicsExtractor context, int x, int y, int size, ChevronDirection direction, int color, int shadowColor, float alpha) {
        CompactSymbolButton.renderGlyph(context, CompactSymbolButton.minecraftFont(), UiBounds.of(x, y, size, size),
            switch (direction) {
                case UP -> CompactSymbolButton.MOVE_UP;
                case DOWN -> CompactSymbolButton.MOVE_DOWN;
                case RIGHT -> ">";
            }, color, alpha);
    }

    public static void drawChevronProgress(GuiGraphicsExtractor context, int x, int y, int size, float progress, int color, int shadowColor, float alpha) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        CompactSymbolButton.renderGlyph(context, CompactSymbolButton.minecraftFont(), UiBounds.of(x, y, size, size),
            clamped >= 0.5f ? CompactSymbolButton.MOVE_DOWN : ">", color, alpha);
    }

}
