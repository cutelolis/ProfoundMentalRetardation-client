package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactOverlayControls;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.util.AutismChatField;
import autismclient.util.AutismJoinMacroController;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroEditorOverlay;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismOverlayManager;
import autismclient.util.AutismUiScale;
import autismclient.util.multi.MultiProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static autismclient.gui.screen.AutismScreenPalette.*;

public final class AutismJoinMacroScreen extends AutismScreen {
    private static final int PANEL_W = 382;
    private static final int PANEL_MARGIN = 12;
    private static final int TOP_PANEL_Y = 20;
    private static final int TOP_PANEL_H = 154;
    private static final int LIST_TOP = 180;
    private static final int LIST_BOTTOM_MARGIN = 12;
    private static final int LIST_HEADER_H = 22;
    private static final int ROW_H = 22;
    private static final int GAP = 6;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final List<HitButton> buttons = new ArrayList<>();
    private AutismChatField searchField;
    private int scroll;
    private String lastQuery = "";
    private DropdownKind openDropdown = DropdownKind.NONE;
    private int dropdownX;
    private int dropdownY;
    private int dropdownW;
    private int dropdownH;
    private int methodX;
    private int methodY;
    private int methodW;
    private int methodH;
    private int triggerX;
    private int triggerY;
    private int triggerW;
    private int triggerH;
    private boolean macroScrollbarDragging;
    private int macroScrollbarGrabOffset;

    public AutismJoinMacroScreen(Screen parent) {
        super(Component.literal("Join Macro"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = TOP_PANEL_Y;
        if (searchField == null) {
            searchField = new AutismChatField(minecraft, font, x + 22, y + 48, panelW() - 44, 18, false);
            searchField.setPlaceholder(Component.literal("Search macros..."));
            searchField.setMaxLength(64);
            searchField.setChangedListener(value -> {
                scroll = 0;
                rebuildRows();
            });
        }
        syncSearchBounds();
        rebuildRows();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = AutismUiScale.toVirtualInt(mouseX);
        int virtualMouseY = AutismUiScale.toVirtualInt(mouseY);
        AutismUiScale.pushOverlayScale(graphics);
        try {
            syncSearchBounds();
            rebuildRowsIfNeeded();
            buttons.clear();

            graphics.fill(0, 0, screenWidth(), screenHeight(), BG);
            int x = panelX();
            int panelW = panelW();
            addButton(10, 10, 76, 18, "Back", CompactOverlayButton.Variant.SECONDARY, true, () -> minecraft.gui.setScreen(parent));

            UiRenderer.frame(graphics, UiBounds.of(x + 10, TOP_PANEL_Y, panelW - 20, TOP_PANEL_H), PANEL_BG, AutismTheme.recolor(BORDER, Channel.OUTLINE));
            UiRenderer.frame(graphics, UiBounds.of(listX(), LIST_TOP, listW(), listPanelH()), PANEL_BG_SOFT, AutismTheme.recolor(BORDER, Channel.OUTLINE));

            graphics.text(font, "Join Macro", x + 22, TOP_PANEL_Y + 10, TEXT, false);
            renderStatus(graphics, x + 22, TOP_PANEL_Y + 24, panelW - 44);

            searchField.render(graphics, virtualMouseX, virtualMouseY, delta);
            int hoverX = openDropdown == DropdownKind.NONE ? virtualMouseX : Integer.MIN_VALUE;
            int hoverY = openDropdown == DropdownKind.NONE ? virtualMouseY : Integer.MIN_VALUE;
            renderActionRow(hoverX, hoverY);
            renderMethodRow(graphics, hoverX, hoverY);
            renderRows(graphics, hoverX, hoverY);
            renderButtons(graphics, hoverX, hoverY);
            renderOpenDropdown(graphics, virtualMouseX, virtualMouseY);
        } finally {
            AutismUiScale.popOverlayScale(graphics);
        }
    }

    private void renderActionRow(int mouseX, int mouseY) {
        int x = panelX() + 22;
        int y = TOP_PANEL_Y + 72;
        addButton(x, y, 58, 18, "Edit", CompactOverlayButton.Variant.SECONDARY, selectedMacro() != null, () -> openEditor(selectedMacro()));
        addButton(x + 64, y, 62, 18, "Create", CompactOverlayButton.Variant.SUCCESS, true, () -> openEditor(null));
        addButton(x + 132, y, 92, 18, "Passwords", CompactOverlayButton.Variant.SECONDARY, true,
            this::openFormValues);
    }

    private void openFormValues() {
        MultiProfile profile = new MultiProfile();
        profile.name = "Rendered client";
        profile.sessions.clear();
        profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        AutismJoinMacroController.openFormValues().forEach((name, value) ->
            profile.setFormValue(MultiProfile.DEFAULT_ACCOUNT_ID, name, value));
        minecraft.gui.setScreen(new AutismFormValuesScreen(this, profile,
            java.util.Set.of(MultiProfile.DEFAULT_ACCOUNT_ID), updated -> {
                if (!AutismJoinMacroController.setFormValues(
                    updated.openFormValues(MultiProfile.DEFAULT_ACCOUNT_ID))) {
                    AutismNotifications.error("Secure encryption is unavailable; values were not saved.");
                }
            }, false));
    }

    private void renderMethodRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = panelX() + 22;
        int y = TOP_PANEL_Y + 98;
        graphics.text(font, "Execution Method", x, y + 5, 0xFFEFE8E4, false);

        int dropdownX = x + 104;
        int dropdownW = 104;
        methodX = dropdownX;
        methodY = y;
        methodW = dropdownW;
        methodH = 18;

        addButton(dropdownX, y, dropdownW, 18, AutismJoinMacroController.timing().label(), CompactOverlayButton.Variant.PRIMARY, true, () -> {
            toggleDropdown(DropdownKind.METHOD, methodX, methodY, methodW, methodH);
        });

        int triggerRowY = y + 24;
        graphics.text(font, AutismJoinMacroController.keepEnabled() ? "Repeat On" : "Run On", x, triggerRowY + 5, 0xFFEFE8E4, false);
        triggerX = dropdownX;
        triggerY = triggerRowY;
        triggerW = 118;
        triggerH = 18;
        addButton(triggerX, triggerY, triggerW, triggerH, triggerLabel(AutismJoinMacroController.triggerJoin()), CompactOverlayButton.Variant.PRIMARY, true, () -> {
            toggleDropdown(DropdownKind.TRIGGER, triggerX, triggerY, triggerW, triggerH);
        });

        String keepLabel = AutismJoinMacroController.keepEnabled() ? "Stays Enabled" : "Clears After";
        addToggleButton(triggerX + triggerW + GAP, triggerY, 118, 18, keepLabel,
            AutismJoinMacroController.keepEnabled(), "join-macro:keep-enabled", true, () -> {
            AutismJoinMacroController.setKeepEnabled(!AutismJoinMacroController.keepEnabled());
        });
    }

    private void renderStatus(GuiGraphicsExtractor graphics, int x, int y, int maxWidth) {
        String selected = AutismJoinMacroController.selectedMacroName();
        if (selected.isBlank()) {
            graphics.text(font, "Click a macro to select it.", x, y, MUTED, false);
            return;
        }

        String selectedLine = "Selected: " + selected;
        String modeLine = AutismJoinMacroController.modeSummary();
        String combined = selectedLine + "  " + modeLine;
        if (font.width(combined) <= maxWidth) {
            graphics.text(font, combined, x, y, SUCCESS, false);
            return;
        }

        graphics.text(font, fit(selectedLine, maxWidth), x, y, SUCCESS, false);
        graphics.text(font, fit(modeLine, maxWidth), x, y + 10, SUCCESS, false);
    }

    private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int listX = listX();
        int listY = listY();
        int listW = listW();
        CompactScrollbar.Metrics scrollbar = macroScrollbarMetrics();
        boolean hasScrollbar = scrollbar.hasScroll();
        int rowRightInset = hasScrollbar ? 18 : 8;

        int titleY = listY + 10;
        int visibleRows = visibleRows();
        String title = rows.size() <= visibleRows
            ? "Macros"
            : "Macros  showing " + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visibleRows) + " / " + rows.size();
        graphics.text(font, fit(title, listW - 24), listX + 12, titleY, TEXT, false);

        if (rows.isEmpty()) {
            graphics.text(font, "No macros found.", listX + 12, rowsTop() + 8, MUTED, false);
            return;
        }

        String selected = AutismJoinMacroController.selectedMacroName();
        int max = Math.min(rows.size(), scroll + visibleRows);
        for (int i = scroll; i < max; i++) {
            Row row = rows.get(i);
            int rowY = rowsTop() + (i - scroll) * ROW_H;
            boolean hovered = mouseX >= listX + 1 && mouseX < listX + listW - 1 && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean active = row.name.equalsIgnoreCase(selected);
            UiBounds rowBounds = UiBounds.of(listX + 8, rowY, listW - 8 - rowRightInset, ROW_H - 3);
            renderMacroSelectionRow(graphics, rowBounds, row.name, active, hovered);

            int markerW = active ? font.width("Selected") + 8 : 0;
            int nameW = Math.max(1, listW - 32 - rowRightInset - markerW);
            graphics.text(font, fit(row.name, nameW), listX + 20, rowY + 5, 0xFFEFE8E4, false);
            if (active) {
                graphics.text(font, "Selected", listX + listW - rowRightInset - markerW - 4, rowY + 5, 0xFFEFE8E4, false);
            }
        }

        CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(mouseX, mouseY), macroScrollbarDragging);
    }

    private void renderButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (HitButton button : buttons) {
            if (button.toggleState != null) {
                renderSmoothToggleButton(graphics, button, mouseX, mouseY);
            } else {
                CompactOverlayControls.action(graphics, font, button.x, button.y, button.w, button.h, button.label, button.variant, button.enabled, mouseX, mouseY);
            }
        }
    }

    private void renderOpenDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (openDropdown == DropdownKind.NONE) return;
        String[] labels = dropdownLabels();
        int itemH = 18;
        int x = dropdownX;
        int h = labels.length * itemH + 2;
        int y = dropdownListY(h);
        UiRenderer.frame(graphics, UiBounds.of(x, y, dropdownW, h), 0xF0050507, AutismTheme.recolor(0xFFFF4E55, Channel.OUTLINE));
        for (int i = 0; i < labels.length; i++) {
            int rowY = y + 1 + i * itemH;
            boolean hovered = mouseX >= x + 1 && mouseX < x + dropdownW - 1 && mouseY >= rowY && mouseY < rowY + itemH;
            boolean active = dropdownActive(i);
            int fill = active ? AutismTheme.recolor(0x66305022, Channel.SUCCESS) : hovered ? AutismTheme.recolor(0x5520181A, Channel.BUTTON) : 0xEE111014;
            graphics.fill(x + 1, rowY, x + dropdownW - 1, rowY + itemH - 1, fill);
            graphics.text(font, fit(labels[i], dropdownW - 8), x + 5, rowY + 5, active ? SUCCESS : 0xFFEFE8E4, false);
        }
    }

    private void addButton(int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant, boolean enabled, Runnable action) {
        buttons.add(new HitButton(x, y, w, h, label, variant, enabled, action, null, null));
    }

    private void addToggleButton(int x, int y, int w, int h, String label, boolean state, String animationKey, boolean enabled, Runnable action) {
        buttons.add(new HitButton(x, y, w, h, label, state ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.DANGER,
            enabled, action, state, animationKey));
    }

    private void renderMacroSelectionRow(GuiGraphicsExtractor graphics, UiBounds bounds, String key, boolean selected, boolean hovered) {
        UiRenderer.frame(graphics, bounds,
            selected ? AutismTheme.recolor(0x3A35D873, Channel.SUCCESS) : (hovered ? AutismTheme.recolor(0x242B1A1D, Channel.ACCENT) : 0x18111113),
            selected ? SUCCESS : (hovered ? AutismTheme.recolor(0xFF5A3038, Channel.OUTLINE) : AutismTheme.recolor(BORDER, Channel.OUTLINE)));
        if (selected) {
            UiRenderer.rect(graphics, bounds.inset(1, 1, 1, 1), AutismTheme.recolor(hovered ? 0x5735D873 : 0x3A35D873, Channel.SUCCESS));
        }
    }

    private void renderSmoothToggleButton(GuiGraphicsExtractor graphics, HitButton button, int mouseX, int mouseY) {
        UiBounds bounds = UiBounds.of(button.x, button.y, button.w, button.h);
        boolean hovered = button.enabled && bounds.contains(mouseX, mouseY);
        boolean active = Boolean.TRUE.equals(button.toggleState);
        UiBounds inner = bounds.inset(1, 1, 1, 1);

        UiRenderer.frame(graphics, bounds, active ? AutismTheme.recolor(0xDD10301D, Channel.SUCCESS) : AutismTheme.recolor(0xDD351317, Channel.DANGER), active ? SUCCESS : AutismTheme.recolor(0xFFFF4E55, Channel.OUTLINE));
        if (!button.enabled) UiRenderer.rect(graphics, inner, 0x77000000);
        else if (hovered) UiRenderer.rect(graphics, inner, 0x14FFFFFF);

        String label = fit(button.label, Math.max(1, bounds.width() - 8));
        graphics.text(font, label, bounds.x() + (bounds.width() - font.width(label)) / 2, bounds.y() + centeredTextY(bounds.height()), TEXT, false);
    }

    private int centeredTextY(int height) {
        return Math.max(1, (height - 8) / 2);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        event = virtualEvent(event);
        if (openDropdown != DropdownKind.NONE) {
            if (handleOpenDropdownClick(event.x(), event.y(), event.button())) return true;
            openDropdown = DropdownKind.NONE;
            return true;
        }
        if (searchField != null && searchField.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        if (event.button() == 0) {
            CompactScrollbar.Metrics scrollbar = macroScrollbarMetrics();
            if (scrollbar.hasScroll() && scrollbar.contains(event.x(), event.y())) {
                macroScrollbarDragging = true;
                macroScrollbarGrabOffset = scrollbar.overThumb(event.x(), event.y())
                    ? Math.max(0, (int) Math.round(event.y()) - scrollbar.thumbY())
                    : scrollbar.thumbHeight() / 2;
                setScrollFromPixels(CompactScrollbar.scrollFromThumb(scrollbar, event.y(), macroScrollbarGrabOffset));
                if (searchField != null) searchField.setFocused(false);
                return true;
            }
            for (HitButton button : buttons) {
                if (button.contains(event.x(), event.y())) {
                    if (button.enabled && button.action != null) button.action.run();
                    return true;
                }
            }
            int rowIndex = rowAt(event.x(), event.y());
            if (rowIndex >= 0) {
                Row row = rows.get(rowIndex);
                String selected = AutismJoinMacroController.selectedMacroName();
                AutismJoinMacroController.setSelectedMacro(row.name.equalsIgnoreCase(selected) ? "" : row.name);
                return true;
            }
        }
        if (searchField != null) searchField.setFocused(false);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        event = virtualEvent(event);
        if (openDropdown != DropdownKind.NONE) return true;
        if (macroScrollbarDragging) {
            macroScrollbarDragging = false;
            return true;
        }
        return searchField != null && searchField.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        event = virtualEvent(event);
        dx = AutismUiScale.toVirtual(dx);
        dy = AutismUiScale.toVirtual(dy);
        if (openDropdown != DropdownKind.NONE) return true;
        if (macroScrollbarDragging) {
            setScrollFromPixels(CompactScrollbar.scrollFromThumb(macroScrollbarMetrics(), event.y(), macroScrollbarGrabOffset));
            return true;
        }
        return searchField != null && searchField.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = AutismUiScale.toVirtual(mouseX);
        mouseY = AutismUiScale.toVirtual(mouseY);
        if (openDropdown != DropdownKind.NONE) return true;
        if (searchField != null && searchField.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (mouseX >= listX() && mouseX < listX() + listW() && mouseY >= listY() && mouseY < listY() + listPanelH()) {
            int maxScroll = Math.max(0, rows.size() - visibleRows());
            if (maxScroll > 0) {
                scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY)));
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (searchField != null && searchField.keyPressed(input)) return true;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (openDropdown != DropdownKind.NONE) {
                openDropdown = DropdownKind.NONE;
                return true;
            }
            minecraft.gui.setScreen(parent);
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return searchField != null && searchField.charTyped(input);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void rebuildRowsIfNeeded() {
        String query = searchText();
        if (!query.equals(lastQuery)) {
            rebuildRows();
        }
    }

    private void rebuildRows() {
        String query = searchText();
        lastQuery = query;
        rows.clear();
        for (AutismMacro macro : AutismMacroManager.get().getAll()) {
            if (macro == null || macro.name == null || macro.name.isBlank()) continue;
            if (!query.isEmpty() && !macro.name.toLowerCase(Locale.ROOT).contains(query)) continue;
            rows.add(new Row(macro.name));
        }
        rows.sort(Comparator.comparing(row -> row.name.toLowerCase(Locale.ROOT)));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
    }

    private String searchText() {
        return searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private AutismMacro selectedMacro() {
        String selected = AutismJoinMacroController.selectedMacroName();
        return selected.isBlank() ? null : AutismMacroManager.get().get(selected);
    }

    private void openEditor(AutismMacro macro) {
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        AutismOverlayManager.get().register(editor, autismclient.util.IAutismOverlay.OverlayScope.HOST_SCREEN);
        editor.openForJoinMacroMenu(macro, saved -> {
            if (saved != null && saved.name != null && !saved.name.isBlank()) {
                AutismJoinMacroController.setSelectedMacro(saved.name);
                AutismNotifications.show("Join macro selected: " + saved.name, 0xFF66E08A);
            }
        });
        if (minecraft != null) minecraft.gui.setScreen(new AutismOverlayHostScreen(editor, new AutismJoinMacroScreen(parent)));
    }

    private int rowAt(double mouseX, double mouseY) {
        int x = listX();
        int y = rowsTop();
        int h = visibleRows() * ROW_H;
        if (mouseX < x + 8 || mouseX >= x + listW() - 8 || mouseY < y || mouseY >= y + h) return -1;
        int visibleIndex = ((int) mouseY - y) / ROW_H;
        int index = scroll + visibleIndex;
        return index >= 0 && index < rows.size() ? index : -1;
    }

    private void syncSearchBounds() {
        if (searchField == null) return;
        int x = panelX();
        searchField.setX(x + 22);
        searchField.setY(TOP_PANEL_Y + 48);
        searchField.setWidth(panelW() - 44);
        searchField.setHeight(18);
    }

    private int panelX() {
        return Math.max(PANEL_MARGIN, (screenWidth() - panelW()) / 2);
    }

    private int panelW() {
        return Math.max(1, Math.min(PANEL_W, screenWidth() - PANEL_MARGIN * 2));
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listY() {
        return LIST_TOP;
    }

    private int listW() {
        return panelW() - 20;
    }

    private int listPanelH() {
        return Math.max(72, screenHeight() - LIST_TOP - LIST_BOTTOM_MARGIN);
    }

    private int rowsTop() {
        return LIST_TOP + LIST_HEADER_H;
    }

    private int visibleRows() {
        return Math.max(1, (listPanelH() - LIST_HEADER_H - 8) / ROW_H);
    }

    private CompactScrollbar.Metrics macroScrollbarMetrics() {
        int trackX = listX() + listW() - 10;
        int trackY = rowsTop();
        int trackH = Math.max(1, visibleRows() * ROW_H - 3);
        int contentPixels = Math.max(0, rows.size() * ROW_H);
        int viewPixels = Math.max(1, visibleRows() * ROW_H);
        return CompactScrollbar.compute(contentPixels, viewPixels, trackX, trackY, 4, trackH, scroll * ROW_H);
    }

    private void setScrollFromPixels(int scrollPixels) {
        int maxScrollRows = Math.max(0, rows.size() - visibleRows());
        scroll = Math.max(0, Math.min(maxScrollRows, Math.round(scrollPixels / (float) ROW_H)));
    }

    private boolean handleOpenDropdownClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            openDropdown = DropdownKind.NONE;
            return true;
        }
        int itemCount = dropdownLabels().length;
        int itemH = 18;
        int x = dropdownX;
        int h = itemCount * itemH + 2;
        int y = dropdownListY(h);
        if (mouseX < x || mouseX >= x + dropdownW || mouseY < y || mouseY >= y + h) {
            openDropdown = DropdownKind.NONE;
            return false;
        }
        int index = ((int) mouseY - y - 1) / itemH;
        if (index >= 0 && index < itemCount) {
            if (openDropdown == DropdownKind.METHOD) {
                AutismJoinMacroController.setTiming(AutismJoinMacroController.Timing.values()[index]);
            } else if (openDropdown == DropdownKind.TRIGGER) {
                AutismJoinMacroController.setTriggerJoin(triggerOptions()[index]);
            }
        }
        openDropdown = DropdownKind.NONE;
        return true;
    }

    private void toggleDropdown(DropdownKind kind, int x, int y, int w, int h) {
        if (openDropdown == kind) {
            openDropdown = DropdownKind.NONE;
            return;
        }
        openDropdown = kind;
        dropdownX = x;
        dropdownY = y;
        dropdownW = w;
        dropdownH = h;
    }

    private String[] dropdownLabels() {
        if (openDropdown == DropdownKind.TRIGGER) {
            AutismJoinMacroController.TriggerJoin[] targets = triggerOptions();
            String[] labels = new String[targets.length];
            for (int i = 0; i < targets.length; i++) labels[i] = triggerLabel(targets[i]);
            return labels;
        }

        AutismJoinMacroController.Timing[] methods = AutismJoinMacroController.Timing.values();
        String[] labels = new String[methods.length];
        for (int i = 0; i < methods.length; i++) labels[i] = methods[i].label();
        return labels;
    }

    private boolean dropdownActive(int index) {
        if (openDropdown == DropdownKind.TRIGGER) {
            AutismJoinMacroController.TriggerJoin[] targets = triggerOptions();
            return index >= 0 && index < targets.length && targets[index] == AutismJoinMacroController.triggerJoin();
        }
        AutismJoinMacroController.Timing[] methods = AutismJoinMacroController.Timing.values();
        return index >= 0 && index < methods.length && methods[index] == AutismJoinMacroController.timing();
    }

    private int dropdownListY(int listHeight) {
        int below = dropdownY + dropdownH + 2;
        if (below + listHeight <= screenHeight() - 4) return below;
        return Math.max(4, dropdownY - listHeight - 2);
    }

    private AutismJoinMacroController.TriggerJoin[] triggerOptions() {
        if (AutismJoinMacroController.keepEnabled()) {
            return new AutismJoinMacroController.TriggerJoin[] {
                AutismJoinMacroController.TriggerJoin.ANY,
                AutismJoinMacroController.TriggerJoin.SECOND,
                AutismJoinMacroController.TriggerJoin.THIRD,
                AutismJoinMacroController.TriggerJoin.FOURTH,
                AutismJoinMacroController.TriggerJoin.FIFTH,
                AutismJoinMacroController.TriggerJoin.SIXTH_PLUS
            };
        }

        return new AutismJoinMacroController.TriggerJoin[] {
            AutismJoinMacroController.TriggerJoin.FIRST,
            AutismJoinMacroController.TriggerJoin.SECOND,
            AutismJoinMacroController.TriggerJoin.THIRD,
            AutismJoinMacroController.TriggerJoin.FOURTH,
            AutismJoinMacroController.TriggerJoin.FIFTH,
            AutismJoinMacroController.TriggerJoin.SIXTH_PLUS
        };
    }

    private String triggerLabel(AutismJoinMacroController.TriggerJoin triggerJoin) {
        return triggerJoin.displayLabel(AutismJoinMacroController.keepEnabled());
    }

    private String fit(String value, int maxWidth) {
        if (value == null) return "";
        if (font.width(value) <= maxWidth) return value;
        return font.plainSubstrByWidth(value, Math.max(1, maxWidth - 4));
    }

    private record Row(String name) {
    }

    private record HitButton(int x, int y, int w, int h, String label, CompactOverlayButton.Variant variant,
                             boolean enabled, Runnable action, Boolean toggleState, String animationKey) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private enum DropdownKind {
        NONE,
        METHOD,
        TRIGGER
    }
}
