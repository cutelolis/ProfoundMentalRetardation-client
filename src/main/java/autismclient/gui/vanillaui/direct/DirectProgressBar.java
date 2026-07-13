package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.ProgressBar;

public final class DirectProgressBar extends DirectUiNode {
    private float progress = 0.0f;

    public DirectProgressBar() {
        this.height = 12;
    }

    public DirectProgressBar setProgress(float progress) {
        this.progress = Math.max(0.0f, Math.min(1.0f, progress));
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return 12.0f;
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        ProgressBar.render(UiContexts.overlay(context.drawContext(), context.textRenderer(),
                Math.round(context.mouseX()), Math.round(context.mouseY())),
            UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height)), progress);
    }
}
