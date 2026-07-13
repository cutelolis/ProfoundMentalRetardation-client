package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.TabStrip;

import java.util.function.IntConsumer;

public final class DirectTabStrip extends DirectUiNode {
    private static final int GAP = 2;
    private String[] tabs = new String[0];
    private int activeIndex = 0;
    private IntConsumer onSelect;

    public DirectTabStrip setTabs(String... tabs) {
        this.tabs = tabs == null ? new String[0] : tabs.clone();
        setActiveIndex(activeIndex);
        markLayoutDirty();
        return this;
    }

    public DirectTabStrip setActiveIndex(int activeIndex) {
        this.activeIndex = Math.max(0, Math.min(Math.max(0, tabs.length - 1), activeIndex));
        return this;
    }

    public DirectTabStrip setOnSelect(IntConsumer onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return Math.max(14, context.theme().buttonHeight());
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible || tabs.length == 0) return;
        UiContext uiContext = UiContexts.overlay(context.drawContext(), context.textRenderer(),
            Math.round(context.mouseX()), Math.round(context.mouseY()));
        for (int i = 0; i < tabs.length; i++) {
            UiBounds bounds = tabBounds(i);
            boolean active = i == activeIndex;
            TabStrip.renderTab(uiContext, bounds, tabs[i], active);
        }
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (!visible || !enabled || button != 0 || tabs.length == 0) return false;
        for (int i = 0; i < tabs.length; i++) {
            if (tabBounds(i).contains(Math.round(mouseX), Math.round(mouseY))) {
                if (i != activeIndex) {
                    setActiveIndex(i);
                    if (onSelect != null) onSelect.accept(i);
                }
                return true;
            }
        }
        return false;
    }

    private UiBounds tabBounds(int index) {
        return TabStrip.tabBounds(UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height)), tabs.length, index, GAP);
    }
}
