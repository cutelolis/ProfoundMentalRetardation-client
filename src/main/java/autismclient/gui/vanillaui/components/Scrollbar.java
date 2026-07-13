package autismclient.gui.vanillaui.components;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiRenderer;

public final class Scrollbar {
    private Scrollbar() {
    }

    public static Metrics metrics(UiBounds track, int contentHeight, int viewHeight, int scroll) {
        int max = Math.max(0, contentHeight - Math.max(0, viewHeight));
        if (max <= 0 || track.height() <= 0) return new Metrics(track, UiBounds.of(track.x(), track.y(), track.width(), track.height()), 0);
        int thumbH = Math.max(12, (int) Math.round(track.height() * (viewHeight / (double) Math.max(viewHeight, contentHeight))));
        thumbH = Math.min(track.height(), thumbH);
        int thumbY = track.y() + (int) Math.round((track.height() - thumbH) * (scroll / (double) max));
        return new Metrics(track, UiBounds.of(track.x(), thumbY, track.width(), thumbH), max);
    }

    public static void render(UiContext context, Metrics metrics, boolean dragging) {
        render(context, metrics, false, dragging);
    }

    public static void render(UiContext context, Metrics metrics, boolean hovered, boolean dragging) {
        if (metrics == null || metrics.maxScroll() <= 0) return;
        var colors = context.theme().colors();
        UiRenderer.rect(context.graphics(), metrics.track(), 0x88101012);
        UiRenderer.rect(context.graphics(), metrics.thumb(), dragging ? colors.accent : hovered ? colors.accentSoft : colors.border);
    }

    public static int scrollFromMouse(Metrics metrics, int mouseY, int grabOffset) {
        if (metrics == null || metrics.maxScroll() <= 0) return 0;
        int available = Math.max(1, metrics.track().height() - metrics.thumb().height());
        int y = Math.max(0, Math.min(available, mouseY - grabOffset - metrics.track().y()));
        return (int) Math.round(metrics.maxScroll() * (y / (double) available));
    }

    public record Metrics(UiBounds track, UiBounds thumb, int maxScroll) {
        public boolean overTrack(int x, int y) {
            return track.contains(x, y);
        }

        public boolean overThumb(int x, int y) {
            return thumb.contains(x, y);
        }
    }
}
