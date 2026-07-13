package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.CompactKeybindButton;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class DirectCompactKeybindButton extends DirectUiNode {
    private final IntSupplier bindCode;
    private final BooleanSupplier capturing;
    private final Runnable onPress;

    public DirectCompactKeybindButton(IntSupplier bindCode, BooleanSupplier capturing, Runnable onPress) {
        this.bindCode = bindCode;
        this.capturing = capturing;
        this.onPress = onPress;
        this.width = CompactKeybindButton.WIDTH;
        this.height = CompactKeybindButton.HEIGHT;
    }

    @Override
    public float preferredWidth(DirectRenderContext context) {
        return CompactKeybindButton.WIDTH;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return CompactKeybindButton.HEIGHT;
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        UiBounds bounds = UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
        boolean hovered = enabled && contains(context.mouseX(), context.mouseY());
        CompactKeybindButton.render(
            UiContexts.overlay(context.drawContext(), context.textRenderer(), Math.round(context.mouseX()), Math.round(context.mouseY())),
            bounds,
            bindCode.getAsInt(),
            capturing.getAsBoolean(),
            hovered
        );
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (!enabled || !contains(mouseX, mouseY) || button != 0) return false;
        if (onPress != null) onPress.run();
        return true;
    }
}
