package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiContexts;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactWindow;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.gui.vanillaui.components.CompactSurfaces;
import autismclient.modules.Module;
import autismclient.modules.ModuleRegistry;
import autismclient.util.AutismHudManager;
import autismclient.util.AutismUiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class AutismHudEditorScreen extends Screen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int TEXT = 0xFFF3ECE7;
    private static final int MUTED = 0xFFB79E9E;

    private static int muted() { return autismclient.util.AutismTheme.recolor(MUTED, autismclient.util.AutismTheme.Channel.TEXT); }
    private static final int GREEN = 0xFF5CFF9A;
    private static final int RED = 0xFFFF3B3B;
    private static final int VISUAL_GRID_SIZE = 10;
    private static final int SNAP_THRESHOLD = 6;
    private static final int NEIGHBOR_GAP = 0;

    private final Screen parent;
    private String selectedId;
    private String draggingId;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean moved;
    private boolean addPickerOpen;
    private int pickerX;
    private int pickerY;
    private int pickerScroll;
    private Integer guideX;
    private Integer guideY;

    private int[] cachedToolbarPos;
    private boolean toolbarPosCached;
    private int toolbarPosSw, toolbarPosSh, toolbarPosW, toolbarPosH, toolbarPosRev;

    public AutismHudEditorScreen(Screen parent) {
        super(Component.literal("Autism HUD Editor"));
        this.parent = parent;
        AutismHudManager.ensureDefaults();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int mx = AutismUiScale.toVirtualInt(mouseX);
        int my = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            int sw = AutismUiScale.getVirtualScreenWidth();
            int sh = AutismUiScale.getVirtualScreenHeight();
            UiRenderer.rect(graphics, UiBounds.of(0, 0, sw, sh), 0x55000000);
            if (editorGrid()) renderGrid(graphics, sw, sh);
            renderGuides(graphics, sw, sh);
            AutismHudManager.render(graphics, font, true, selectedId, mx, my);
            renderToolbar(graphics, sw, sh, mx, my);
            if (addPickerOpen) renderAddPicker(graphics, mx, my);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int sw, int sh, int mx, int my) {
        String help = "Drag elements, right click edits/adds, Delete disables, arrows nudge.";
        int titleW = UiText.width(font, "Autism HUD Editor", THEME.fontFor(UiTone.BODY), TEXT) + 30;
        int helpW = UiText.width(font, help, THEME.fontFor(UiTone.BODY), muted()) + 16;
        int w = Math.min(sw - 16, Math.max(172, Math.min(430, Math.max(titleW, helpW))));
        int h = 34;
        int[] pos = toolbarPosition(sw, sh, w, h);
        if (pos == null) return;
        int x = pos[0];
        int y = pos[1];
        CompactWindow.renderFrame(UiContexts.overlay(graphics, font, mx, my), UiBounds.of(x, y, w, h),
            "Autism HUD Editor", false, false, false, hover(mx, my, x, y, w, 16), true, 7, 7, 16);
        draw(graphics, help, x + 8, y + 19, muted(), w - 16);
    }

    private int[] toolbarPosition(int sw, int sh, int w, int h) {
        int rev = AutismHudManager.settingsRevision();
        if (toolbarPosCached && sw == toolbarPosSw && sh == toolbarPosSh
            && w == toolbarPosW && h == toolbarPosH && rev == toolbarPosRev) {
            return cachedToolbarPos;
        }
        int pad = 8;
        int[][] candidates = {
            {pad, pad},
            {Math.max(pad, sw - w - pad), pad},
            {pad, Math.max(pad, sh - h - pad)},
            {Math.max(pad, sw - w - pad), Math.max(pad, sh - h - pad)}
        };
        int[] pos = null;
        for (int[] candidate : candidates) {
            if (!intersectsHudElement(candidate[0], candidate[1], w, h)) {
                pos = candidate;
                break;
            }
        }
        cachedToolbarPos = pos;
        toolbarPosCached = true;
        toolbarPosSw = sw;
        toolbarPosSh = sh;
        toolbarPosW = w;
        toolbarPosH = h;
        toolbarPosRev = rev;
        return pos;
    }

    private boolean intersectsHudElement(int x, int y, int w, int h) {
        for (String id : AutismHudManager.elementIds()) {
            if (!AutismHudManager.state(id).enabled) continue;
            AutismHudManager.ElementBounds bounds = AutismHudManager.bounds(id, font);
            if (rectsIntersect(x, y, w, h, bounds.x(), bounds.y(), bounds.width(), bounds.height())) return true;
        }
        return false;
    }

    private boolean rectsIntersect(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void renderGrid(GuiGraphicsExtractor graphics, int sw, int sh) {
        int step = gridSize();
        for (int x = 0; x < sw; x += step) {
            boolean major = (x / step) % 5 == 0;
            UiRenderer.rect(graphics, UiBounds.of(x, 0, 1, sh), major ? 0x24111114 : 0x14111114);
        }
        for (int y = 0; y < sh; y += step) {
            boolean major = (y / step) % 5 == 0;
            UiRenderer.rect(graphics, UiBounds.of(0, y, sw, 1), major ? 0x24111114 : 0x14111114);
        }
        UiRenderer.rect(graphics, UiBounds.of(sw / 2, 0, 1, sh), autismclient.util.AutismTheme.recolor(0x44FF3B3B, autismclient.util.AutismTheme.Channel.ACCENT));
        UiRenderer.rect(graphics, UiBounds.of(0, sh / 2, sw, 1), autismclient.util.AutismTheme.recolor(0x44FF3B3B, autismclient.util.AutismTheme.Channel.ACCENT));
    }

    private void renderAddPicker(GuiGraphicsExtractor graphics, int mx, int my) {
        int w = 150;
        int rowH = 18;
        var ids = AutismHudManager.elementIds();
        int contentH = 24 + ids.size() * rowH;
        int h = Math.min(contentH, Math.max(80, AutismUiScale.getVirtualScreenHeight() - 8));
        int viewH = Math.max(20, h - 24);
        pickerScroll = clamp(pickerScroll, 0, Math.max(0, contentH - h));
        int x = Math.min(pickerX, Math.max(0, AutismUiScale.getVirtualScreenWidth() - w - 4));
        int y = Math.min(pickerY, Math.max(0, AutismUiScale.getVirtualScreenHeight() - h - 4));
        CompactWindow.renderFrame(UiContexts.overlay(graphics, font, mx, my), UiBounds.of(x, y, w, h),
            "Add / Toggle", false, false, false, hover(mx, my, x, y, w, 24), true, 7, 7, 24);
        autismclient.gui.vanillaui.UiScissorStack.global().push(graphics,
            autismclient.gui.vanillaui.UiBounds.of(x + 2, y + 24, Math.max(0, w - 4), Math.max(0, h - 26)));
        try {
            int cy = y + 24 - pickerScroll;
            for (String id : ids) {
                if (cy + rowH >= y + 24 && cy <= y + h) {
            boolean over = hover(mx, my, x + 4, cy, w - 8, rowH - 2);
            boolean enabled = AutismHudManager.state(id).enabled;
            CompactSurfaces.tintedRow(graphics, x + 4, cy, w - 8, rowH - 2, over ? 0x66351B1F : 0x33131418);
            if (enabled) CompactSurfaces.indicator(graphics, x + 5, cy + 1, 3, rowH - 4, GREEN);
                    draw(graphics, AutismHudManager.label(id), x + 12, cy + 4, enabled ? TEXT : muted(), w - 18);
                }
            cy += rowH;
            }
        } finally {
            autismclient.gui.vanillaui.UiScissorStack.global().pop(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        if (addPickerOpen) {
            String picked = pickerHit(mx, my);
            if (picked != null) {
                AutismHudManager.setEnabled(picked, true);
                int[] snapped = snappedPosition(picked, mx, my);
                AutismHudManager.move(picked, snapped[0], snapped[1], AutismUiScale.getVirtualScreenWidth(), AutismUiScale.getVirtualScreenHeight());
                selectedId = picked;
                addPickerOpen = false;
                pickerScroll = 0;
                return true;
            }
            addPickerOpen = false;
            pickerScroll = 0;
            return true;
        }
        String hit = AutismHudManager.hit(font, mx, my);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (hit != null) {
                minecraft.gui.setScreen(new AutismHudElementSettingsScreen(this, hit));
            } else {
                pickerX = mx;
                pickerY = my;
                pickerScroll = 0;
                addPickerOpen = true;
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit != null) {
            selectedId = hit;
            draggingId = hit;
            AutismHudManager.ElementBounds bounds = AutismHudManager.bounds(hit, font);
            dragOffsetX = mx - bounds.x();
            dragOffsetY = my - bounds.y();
            moved = false;
            return true;
        }
        selectedId = null;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingId = null;
        guideX = null;
        guideY = null;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingId == null) return true;
        int mx = AutismUiScale.toVirtualInt(event.x());
        int my = AutismUiScale.toVirtualInt(event.y());
        int[] snapped = snappedPosition(draggingId, mx - dragOffsetX, my - dragOffsetY);
        AutismHudManager.move(draggingId, snapped[0], snapped[1], AutismUiScale.getVirtualScreenWidth(), AutismUiScale.getVirtualScreenHeight());
        moved = true;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        if (selectedId != null && input.key() == GLFW.GLFW_KEY_DELETE) {
            AutismHudManager.setEnabled(selectedId, false);
            return true;
        }
        if (selectedId != null) {
            int step = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0 ? VISUAL_GRID_SIZE : 1;
            AutismHudManager.ElementBounds bounds = AutismHudManager.bounds(selectedId, font);
            int x = bounds.x();
            int y = bounds.y();
            boolean moved = true;
            switch (input.key()) {
                case GLFW.GLFW_KEY_LEFT -> x -= step;
                case GLFW.GLFW_KEY_RIGHT -> x += step;
                case GLFW.GLFW_KEY_UP -> y -= step;
                case GLFW.GLFW_KEY_DOWN -> y += step;
                default -> moved = false;
            }
            if (moved) {
                int[] snapped = snappedPosition(selectedId, x, y);
                AutismHudManager.move(selectedId, snapped[0], snapped[1], AutismUiScale.getVirtualScreenWidth(), AutismUiScale.getVirtualScreenHeight());
                return true;
            }
        }
        return true;
    }

    private int[] snappedPosition(String id, int x, int y) {
        AutismHudManager.ElementBounds bounds = AutismHudManager.bounds(id, font);
        int sw = AutismUiScale.getVirtualScreenWidth();
        int sh = AutismUiScale.getVirtualScreenHeight();
        int outX = x;
        int outY = y;
        int maxX = Math.max(0, sw - bounds.width());
        int maxY = Math.max(0, sh - bounds.height());
        guideX = null;
        guideY = null;
        SnapResult snappedX = snapAxis(id, x, bounds.width(), sw, true);
        SnapResult snappedY = snapAxis(id, y, bounds.height(), sh, false);
        if (snappedX.snapped()) {
            outX = snappedX.position();
            guideX = snappedX.guide();
        }
        if (snappedY.snapped()) {
            outY = snappedY.position();
            guideY = snappedY.guide();
        }
        outX = clamp(outX, 0, maxX);
        outY = clamp(outY, 0, maxY);
        return new int[] {outX, outY};
    }

    private SnapResult snapAxis(String id, int value, int size, int screenSize, boolean horizontal) {
        int max = Math.max(0, screenSize - size);
        SnapResult best = SnapResult.none();
        best = bestCloser(best, value, 0, 0);
        best = bestCloser(best, value, max, screenSize);
        best = bestCloser(best, value, (screenSize - size) / 2, screenSize / 2);
        for (String other : AutismHudManager.elementIds()) {
            if (other.equals(id) || !AutismHudManager.state(other).enabled) continue;
            AutismHudManager.ElementBounds ob = AutismHudManager.bounds(other, font);
            int otherStart = horizontal ? ob.x() : ob.y();
            int otherSize = horizontal ? ob.width() : ob.height();
            int otherEnd = otherStart + otherSize;
            int otherCenter = otherStart + otherSize / 2;
            best = bestCloser(best, value, otherStart, otherStart);
            best = bestCloser(best, value, otherEnd - size, otherEnd);
            best = bestCloser(best, value, otherCenter - size / 2, otherCenter);
            best = bestCloser(best, value, otherStart - size - NEIGHBOR_GAP, otherStart);
            best = bestCloser(best, value, otherEnd + NEIGHBOR_GAP, otherEnd);
        }
        return best;
    }

    private SnapResult bestCloser(SnapResult current, int value, int candidatePosition, int guide) {
        int distance = Math.abs(value - candidatePosition);
        if (distance > SNAP_THRESHOLD) return current;
        if (!current.snapped() || distance < current.distance()) return new SnapResult(candidatePosition, guide, distance, true);
        return current;
    }

    private int gridSize() {
        return VISUAL_GRID_SIZE;
    }

    private String pickerHit(int mx, int my) {
        int w = 150;
        int rowH = 18;
        int contentH = 24 + AutismHudManager.elementIds().size() * rowH;
        int h = Math.min(contentH, Math.max(80, AutismUiScale.getVirtualScreenHeight() - 8));
        int x = Math.min(pickerX, Math.max(0, AutismUiScale.getVirtualScreenWidth() - w - 4));
        int y = Math.min(pickerY, Math.max(0, AutismUiScale.getVirtualScreenHeight() - h - 4));
        if (!hover(mx, my, x, y + 24, w, h - 24)) return null;
        int cy = y + 24 - pickerScroll;
        for (String id : AutismHudManager.elementIds()) {
            if (hover(mx, my, x + 4, cy, w - 8, rowH - 2)) return id;
            cy += rowH;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (addPickerOpen) {
            int rowH = 18;
            int contentH = 24 + AutismHudManager.elementIds().size() * rowH;
            int h = Math.min(contentH, Math.max(80, AutismUiScale.getVirtualScreenHeight() - 8));
            pickerScroll = clamp(pickerScroll + (scrollY < 0 ? rowH * 2 : -rowH * 2), 0, Math.max(0, contentH - h));
            return true;
        }
        return true;
    }

    private boolean editorGrid() {
        Module hud = ModuleRegistry.get("hud");
        return hud == null || Boolean.parseBoolean(hud.value("editor-grid"));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void renderGuides(GuiGraphicsExtractor graphics, int sw, int sh) {
        if (guideX != null) UiRenderer.rect(graphics, UiBounds.of(guideX, 0, 1, sh), autismclient.util.AutismTheme.recolor(0xAAFF3B3B, autismclient.util.AutismTheme.Channel.ACCENT));
        if (guideY != null) UiRenderer.rect(graphics, UiBounds.of(0, guideY, sw, 1), autismclient.util.AutismTheme.recolor(0xAAFF3B3B, autismclient.util.AutismTheme.Channel.ACCENT));
    }

    private boolean hover(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void draw(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxW) {
        String trimmed = UiText.trimToWidth(font, text, maxW, THEME.fontFor(UiTone.BODY), color);
        UiText.draw(graphics, font, trimmed, THEME.fontFor(UiTone.BODY), color, x, y, false);
    }

    private record SnapResult(int position, int guide, int distance, boolean snapped) {
        static SnapResult none() {
            return new SnapResult(0, 0, Integer.MAX_VALUE, false);
        }
    }
}
