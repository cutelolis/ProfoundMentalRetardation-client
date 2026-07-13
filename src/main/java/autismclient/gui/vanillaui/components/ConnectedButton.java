package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import net.minecraft.resources.Identifier;

public final class ConnectedButton {
    public static final Edges FULL = new Edges(true, true, true, true);
    public static final Edges LEFT_CELL = new Edges(true, true, false, true);
    public static final Edges RIGHT_CELL = new Edges(false, true, true, true);

    private ConnectedButton() {
    }

    public static void renderAction(UiContext context, UiBounds bounds, String label, Identifier icon,
                                    Button.Tone tone, boolean hovered, Edges edges) {
        var colors = context.theme().colors();
        int fill = switch (tone) {
            case PRIMARY -> AutismTheme.recolor(0xCC22161A, Channel.BUTTON);
            case SUCCESS -> AutismTheme.recolor(0xCC10301D, Channel.SUCCESS);
            case DANGER -> AutismTheme.recolor(0xCC351317, Channel.DANGER);
            case NORMAL, SECONDARY -> 0xBB121319;
        };
        int border = switch (tone) {
            case SUCCESS -> colors.success;
            case DANGER, PRIMARY, SECONDARY -> colors.accent;
            case NORMAL -> colors.borderSoft;
        };
        renderBase(context, bounds, label, icon, hovered, fill, border, edges);
    }

    public static void renderToggle(UiContext context, UiBounds bounds, String label, Identifier icon,
                                    boolean hovered, float enabledProgress, Edges edges) {
        var colors = context.theme().colors();
        float progress = clamp01(enabledProgress);
        UiBounds fillArea = contentBounds(bounds, edges);
        int fillWidth = Math.round(fillArea.width() * progress);
        if (fillWidth > 0) {
            UiRenderer.rect(context.graphics(), UiBounds.of(fillArea.x(), fillArea.y(), fillWidth, fillArea.height()), AutismTheme.recolor(0xDD10301D, Channel.SUCCESS));
        }
        if (fillWidth < fillArea.width()) {
            UiRenderer.rect(context.graphics(), UiBounds.of(fillArea.x() + fillWidth, fillArea.y(), fillArea.width() - fillWidth, fillArea.height()), AutismTheme.recolor(0xDD351317, Channel.DANGER));
        }
        drawEdges(context, bounds, colors.accent, edges);
        if (progress > 0.001f) drawProgressEdges(context, bounds, colors.success, edges, progress);
        if (hovered) UiRenderer.rect(context.graphics(), contentBounds(bounds, edges), 0x14FFFFFF);
        drawContent(context, bounds, label, icon);
    }

    public static void renderCategory(UiContext context, UiBounds bounds, String label, Identifier icon) {
        var colors = context.theme().colors();
        UiRenderer.rect(context.graphics(), bounds, 0xAA15171D);
        UiRenderer.horizontalEdge(context.graphics(), bounds.x(), bounds.bottom() - 1, bounds.width(), colors.borderSoft);
        drawContent(context, bounds, label, icon, colors.muted);
    }

    private static void renderBase(UiContext context, UiBounds bounds, String label, Identifier icon,
                                   boolean hovered, int fill, int border, Edges edges) {
        UiRenderer.rect(context.graphics(), bounds, fill);
        drawEdges(context, bounds, border, edges);
        if (hovered) UiRenderer.rect(context.graphics(), contentBounds(bounds, edges), 0x14FFFFFF);
        drawContent(context, bounds, label, icon);
    }

    private static void drawContent(UiContext context, UiBounds bounds, String label, Identifier icon) {
        drawContent(context, bounds, label, icon, context.theme().colors().text);
    }

    private static void drawContent(UiContext context, UiBounds bounds, String label, Identifier icon, int color) {
        if (icon == null) {
            context.text().drawCentered(context.graphics(), label, bounds, color);
            return;
        }
        int iconSize = Math.min(12, Math.max(8, bounds.height() - 5));
        int iconX = bounds.x() + 5;
        int iconY = bounds.y() + Math.max(1, (bounds.height() - iconSize) / 2);
        context.graphics().blit(AutismThemeTextures.recolored(icon, Channel.ACCENT), iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        int textX = iconX + iconSize + 4;
        context.text().drawFitted(context.graphics(), label, textX, context.text().centeredY(bounds),
            Math.max(1, bounds.right() - textX - 4), color);
    }

    private static void drawEdges(UiContext context, UiBounds bounds, int color, Edges edges) {
        if (edges.top) UiRenderer.horizontalEdge(context.graphics(), bounds.x(), bounds.y(), bounds.width(), color);
        if (edges.bottom) UiRenderer.horizontalEdge(context.graphics(), bounds.x(), bounds.bottom() - 1, bounds.width(), color);
        if (edges.left) UiRenderer.verticalEdge(context.graphics(), bounds.x(), bounds.y(), bounds.height(), color);
        if (edges.right) UiRenderer.verticalEdge(context.graphics(), bounds.right() - 1, bounds.y(), bounds.height(), color);
    }

    private static void drawProgressEdges(UiContext context, UiBounds bounds, int color, Edges edges, float progress) {
        int width = Math.max(1, Math.round(bounds.width() * progress));
        if (edges.top) UiRenderer.horizontalEdge(context.graphics(), bounds.x(), bounds.y(), width, color);
        if (edges.bottom) UiRenderer.horizontalEdge(context.graphics(), bounds.x(), bounds.bottom() - 1, width, color);
        if (edges.left) UiRenderer.verticalEdge(context.graphics(), bounds.x(), bounds.y(), bounds.height(), color);
        if (edges.right && progress >= 0.999f) UiRenderer.verticalEdge(context.graphics(), bounds.right() - 1, bounds.y(), bounds.height(), color);
    }

    public static int toneBorderColor(UiContext context, Button.Tone tone) {
        var colors = context.theme().colors();
        return switch (tone) {
            case SUCCESS -> colors.success;
            case DANGER, PRIMARY, SECONDARY -> colors.accent;
            case NORMAL -> colors.borderSoft;
        };
    }

    public static int toggleBorderColor(UiContext context, float progress) {
        var colors = context.theme().colors();
        return blendArgb(colors.accent, colors.success, clamp01(progress));
    }

    public static float seamWeight(Button.Tone tone, boolean toggle, float progress) {
        if (toggle) return clamp01(progress);
        return switch (tone) {
            case SUCCESS -> 1.0f;
            case DANGER, PRIMARY, SECONDARY -> 0.25f;
            case NORMAL -> 0.0f;
        };
    }

    public static int chooseSeamColor(int leftColor, float leftWeight, int rightColor, float rightWeight) {
        return rightWeight > leftWeight ? rightColor : leftColor;
    }

    public static void drawVerticalSeam(UiContext context, int x, int y, int height, int color) {
        UiRenderer.verticalEdge(context.graphics(), x, y, height, color);
    }

    private static UiBounds contentBounds(UiBounds bounds, Edges edges) {
        return bounds.inset(edges.left ? 1 : 0, edges.top ? 1 : 0, edges.right ? 1 : 0, edges.bottom ? 1 : 0);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int blendArgb(int from, int to, float progress) {
        float t = clamp01(progress);
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = Math.round(((from >>> 16) & 0xFF) + ((((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t));
        int g = Math.round(((from >>> 8) & 0xFF) + ((((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public record Edges(boolean left, boolean top, boolean right, boolean bottom) {
    }
}
