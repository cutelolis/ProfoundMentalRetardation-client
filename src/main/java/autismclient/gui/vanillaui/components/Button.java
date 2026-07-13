package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.AutismThemeTextures;
import net.minecraft.resources.Identifier;

public final class Button {
    private Button() {
    }

    public enum Tone {
        NORMAL,
        SECONDARY,
        PRIMARY,
        SUCCESS,
        DANGER
    }

    public static void render(UiContext context, UiBounds bounds, String label, Tone tone, boolean hovered, boolean active) {
        render(context, bounds, label, null, tone, hovered, active, active ? 1.0f : 0.0f);
    }

    public static void renderIcon(UiContext context, UiBounds bounds, String label, Identifier icon, Tone tone, boolean hovered, boolean active, float activeProgress) {
        render(context, bounds, label, icon, tone, hovered, active, activeProgress);
    }

    private static void render(UiContext context, UiBounds bounds, String label, Identifier icon, Tone tone, boolean hovered, boolean active, float activeProgress) {
        var colors = context.theme().colors();
        int fill = switch (tone) {
            case PRIMARY -> active ? colors.accentDark : AutismTheme.recolor(0xCC22161A, Channel.BUTTON);
            case SUCCESS -> AutismTheme.recolor(active ? 0xCC10301D : 0xBB121A15, Channel.SUCCESS);
            case DANGER -> AutismTheme.recolor(0xCC351317, Channel.DANGER);
            default -> active ? 0xCC191C22 : 0xBB121319;
        };
        int accent = switch (tone) {
            case SUCCESS -> colors.success;
            case DANGER, PRIMARY, SECONDARY -> colors.accent;
            default -> colors.borderSoft;
        };
        float progress = Math.min(1.0f, Math.max(0.0f, activeProgress));
        int border = active || progress > 0.001f
            ? blendArgb(colors.borderSoft, accent, active ? Math.max(progress, 0.001f) : progress)
            : tone != Tone.NORMAL ? accent : colors.borderSoft;
        UiRenderer.frame(context.graphics(), bounds, fill, border);
        if (activeProgress > 0.001f) {
            int fillW = Math.max(1, Math.round(bounds.width() * progress));
            int color = tone == Tone.SUCCESS ? colors.successSoft : colors.accentSoft;
            UiRenderer.rect(context.graphics(), UiBounds.of(bounds.x() + 1, bounds.y() + 1, Math.max(0, fillW - 2), Math.max(0, bounds.height() - 2)), color);
        }
        if (hovered) UiRenderer.rect(context.graphics(), bounds.inset(1), 0x14FFFFFF);
        if (icon == null) {
            context.text().drawCentered(context.graphics(), label, bounds, colors.text);
            return;
        }
        int iconSize = Math.min(12, Math.max(8, bounds.height() - 5));
        int iconX = bounds.x() + 5;
        int iconY = bounds.y() + Math.max(1, (bounds.height() - iconSize) / 2);
        context.graphics().blit(AutismThemeTextures.recolored(icon, Channel.ACCENT), iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f);
        int textX = iconX + iconSize + 4;
        int maxText = Math.max(1, bounds.right() - textX - 4);
        context.text().drawFitted(context.graphics(), label, textX, context.text().centeredY(bounds), maxText, colors.text);
    }

    private static int blendArgb(int from, int to, float progress) {
        float t = Math.min(1.0f, Math.max(0.0f, progress));
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t));
        int r = Math.round(((from >>> 16) & 0xFF) + ((((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t));
        int g = Math.round(((from >>> 8) & 0xFF) + ((((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
