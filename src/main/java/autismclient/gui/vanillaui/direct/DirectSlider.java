package autismclient.gui.vanillaui.direct;

import autismclient.gui.vanillaui.components.*;

import autismclient.gui.vanillaui.direct.DirectUiNode;
import autismclient.gui.vanillaui.direct.DirectRenderContext;
import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContext;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.components.Slider;

import java.util.function.Consumer;

public final class DirectSlider extends DirectUiNode {
    private float min = 0.0f;
    private float max = 1.0f;
    private float value = 0.0f;
    private float step = 0.0f;
    private boolean dragging = false;
    private Consumer<Float> onChange;
    private Consumer<Float> onRelease;

    public DirectSlider() {
        this.height = 12;
    }

    public DirectSlider setRange(float min, float max) {
        this.min = min;
        this.max = Math.max(min, max);
        return setValue(value);
    }

    public DirectSlider setStep(float step) {
        this.step = Math.max(0.0f, step);
        return setValue(value);
    }

    public DirectSlider setValue(float value) {
        float snapped = snap(clamp(value));
        if (Math.abs(this.value - snapped) > 0.0001f) {
            this.value = snapped;
            if (onChange != null) onChange.accept(this.value);
        }
        return this;
    }

    public DirectSlider setOnChange(Consumer<Float> onChange) {
        this.onChange = onChange;
        return this;
    }

    public DirectSlider setOnRelease(Consumer<Float> onRelease) {
        this.onRelease = onRelease;
        return this;
    }

    @Override
    public float preferredHeight(DirectRenderContext context, float availableWidth) {
        return 12.0f;
    }

    @Override
    public void render(DirectRenderContext context) {
        if (!visible) return;
        UiBounds bounds = UiBounds.of(Math.round(x), Math.round(y), Math.round(width), Math.round(height));
        UiContext uiContext = UiContexts.overlay(context.drawContext(), context.textRenderer(),
            Math.round(context.mouseX()), Math.round(context.mouseY()));
        Slider.render(uiContext, bounds, Slider.ratio(value, min, max), contains(context.mouseX(), context.mouseY()) || dragging);
    }

    @Override
    public boolean mouseClicked(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) return false;
        dragging = true;
        updateFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(DirectRenderContext context, float mouseX, float mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            if (onRelease != null) onRelease.accept(value);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(DirectRenderContext context, float mouseX, float mouseY, int button, float deltaX, float deltaY) {
        if (!dragging || button != 0) return false;
        updateFromMouse(mouseX);
        return true;
    }

    private void updateFromMouse(float mouseX) {
        setValue((float) Slider.valueFromMouse(mouseX, Math.round(x), Math.round(width), min, max, step <= 0.0f ? 0.0001f : step));
    }

    private float clamp(float value) {
        return Math.max(min, Math.min(max, value));
    }

    private float snap(float value) {
        if (step <= 0.0f) return value;
        return min + Math.round((value - min) / step) * step;
    }
}
