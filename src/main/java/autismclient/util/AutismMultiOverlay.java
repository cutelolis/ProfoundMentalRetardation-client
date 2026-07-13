package autismclient.util;

import autismclient.gui.multi.MultiPanel;
import autismclient.gui.screen.AutismAccountsScreen;
import autismclient.gui.screen.AutismFormValuesScreen;
import autismclient.util.multi.MultiManualPackets;
import autismclient.util.multi.MultiPacketPolicy;
import autismclient.util.multi.MultiProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.Packet;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class AutismMultiOverlay extends AutismOverlayBase implements MultiPanel.Host {
    private static final String OVERLAY_ID = "autism-multi";
    private final Minecraft mc = Minecraft.getInstance();
    private final Font font;
    private final MultiPanel panel;
    private final AutismPacketSelectorOverlay packetSelector;

    private final Map<String, AutismMultiGuiOverlay> guiViewers = new LinkedHashMap<>();
    private boolean dragging;
    private boolean suppressForAccounts;
    private net.minecraft.client.gui.screens.Screen accountsReturnScreen;
    private double dragOffsetX;
    private double dragOffsetY;

    public AutismMultiOverlay(Font font) {
        super(OVERLAY_ID, 470, 300);
        this.font = font;
        panelX = 70;
        panelY = 30;
        panel = new MultiPanel(this, font);
        packetSelector = new AutismPacketSelectorOverlay(font);
    }

    public void toggle() {
        setVisible(!visible);
        if (visible) {
            panel.opened();
            AutismOverlayManager.get().bringToFront(this);
        }
    }

    public void openInGameInteractive() {
        setVisible(true);
        panel.opened();
    }

    @Override
    public void setVisible(boolean value) {
        if (!value) {
            panel.flushPendingEdit();
            packetSelector.close();

            suppressForAccounts = false;
            accountsReturnScreen = null;
        }
        super.setVisible(value);
    }

    @Override public int getMinWidth() { return 370; }
    @Override public int getMinHeight() { return 220; }
    @Override public OverlayScope getDefaultOverlayScope() { return OverlayScope.BACKGROUND_STATUS; }
    @Override public boolean usesSharedHeaderClickCollapse() { return true; }
    @Override public boolean hasTextFieldFocused() { return panel.hasFocusedTextInput() || packetSelector.hasTextFieldFocused(); }
    @Override public void clearTextFieldFocus() { panel.clearFocus(); packetSelector.clearTextFieldFocus(); }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!visible || suppressedForAccounts()) return;
        AutismWindowLayout bounds = clampToScreen(this);
        panelX = bounds.x; panelY = bounds.y; panelWidth = bounds.width; panelHeight = bounds.height;
        renderWindowFrame(context, mouseX, mouseY, getBounds(),
            autismclient.util.multi.MultiManager.get().isActive()
                ? "Multi " + autismclient.util.multi.MultiManager.get().connectedCount()
                : "Multi",
            collapsed, dragging);
        if (!collapsed) {
            boolean clipped = beginWindowBodyClip(context, getBounds(), false);
            panel.render(context, panelX + 2, panelY + HEADER_HEIGHT + 1, panelWidth - 4,
                panelHeight - HEADER_HEIGHT - 3, mouseX, mouseY, delta);
            endWindowBodyClip(context, clipped);
        }
        if (packetSelector.isVisible()) packetSelector.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || suppressedForAccounts()) return false;
        if (packetSelector.isVisible()) {
            packetSelector.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        AutismWindowLayout bounds = getBounds();
        if (isOverCloseButton(mouseX, mouseY, bounds)) {
            setVisible(false);
            dragging = false;
            return true;
        }
        if (button == 0 && isOverDragBar(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - panelX;
            dragOffsetY = mouseY - panelY;
            return true;
        }
        if (collapsed) return false;
        if (panel.mouseClicked((int) Math.round(mouseX), (int) Math.round(mouseY), button)) return true;
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!visible || suppressedForAccounts()) return false;
        if (packetSelector.isVisible()) {
            packetSelector.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (dragging) {
            dragging = false;
            saveLayout();
            return true;
        }
        return panel.mouseReleased((int) Math.round(mouseX), (int) Math.round(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!visible || suppressedForAccounts()) return false;
        if (packetSelector.isVisible()) {
            packetSelector.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            return true;
        }
        if (dragging) {
            AutismWindowLayout next = clampToScreen(this, new AutismWindowLayout(
                (int) Math.round(mouseX - dragOffsetX), (int) Math.round(mouseY - dragOffsetY),
                panelWidth, panelHeight, visible, collapsed));
            panelX = next.x;
            panelY = next.y;
            return true;
        }
        return !collapsed && panel.mouseDragged((int) Math.round(mouseX), (int) Math.round(mouseY), button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible || suppressedForAccounts()) return false;
        if (packetSelector.isVisible()) {
            packetSelector.mouseScrolled(mouseX, mouseY, amount);
            return true;
        }
        return !collapsed && isMouseOver(mouseX, mouseY)
            && panel.mouseScrolled((int) Math.round(mouseX), (int) Math.round(mouseY), amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || suppressedForAccounts()) return false;
        if (packetSelector.isVisible()) {
            packetSelector.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return !collapsed && panel.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!visible || suppressedForAccounts()) return false;
        if (packetSelector.isVisible()) {
            packetSelector.charTyped(chr, modifiers);
            return true;
        }
        return !collapsed && panel.charTyped(chr, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible || suppressedForAccounts()) return false;
        return (packetSelector.isVisible() && packetSelector.isMouseOver(mouseX, mouseY))
            || super.isMouseOver(mouseX, mouseY);
    }

    @Override
    public void manageAccounts() {
        if (mc == null) return;
        net.minecraft.client.gui.screens.Screen parent = mc.gui.screen();
        suppressForAccounts = true;
        accountsReturnScreen = parent;
        panel.clearFocus();
        mc.gui.setScreen(new AutismAccountsScreen(parent));
    }

    @Override
    public void openGui(String accountId) {
        if (accountId == null || accountId.isBlank()) return;
        AutismMultiGuiOverlay viewer = guiViewers.get(accountId);
        if (viewer != null && viewer.isOpenFor(accountId)) {
            viewer.setVisible(false);
            return;
        }
        if (viewer == null) {
            viewer = new AutismMultiGuiOverlay(font, accountId);
            viewer.restoreLayout();
            guiViewers.put(accountId, viewer);
        }
        AutismOverlayManager manager = AutismOverlayManager.get();
        manager.register(viewer, OverlayScope.BACKGROUND_STATUS);
        if (viewer.open()) manager.bringToFront(viewer);
    }

    @Override
    public boolean isGuiOpen(String accountId) {
        AutismMultiGuiOverlay viewer = guiViewers.get(accountId);
        return viewer != null && viewer.isOpenFor(accountId);
    }

    private boolean suppressedForAccounts() {
        if (!suppressForAccounts) return false;
        net.minecraft.client.gui.screens.Screen current = mc == null || mc.gui == null ? null : mc.gui.screen();
        if (current != accountsReturnScreen) return true;
        suppressForAccounts = false;
        accountsReturnScreen = null;
        return false;
    }

    @Override
    public void pickQuickPacket(Consumer<Class<? extends Packet<?>>> callback) {
        packetSelector.openC2S(callback, MultiManualPackets.unsafeC2S(), true);
    }

    @Override
    public void editBlocklist(MultiPacketPolicy.Direction direction,
                              Collection<Class<? extends Packet<?>>> selected,
                              BiConsumer<Class<? extends Packet<?>>, Boolean> callback) {
        if (direction == MultiPacketPolicy.Direction.S2C) {
            packetSelector.openToggleS2C(callback, selected);
        } else {
            packetSelector.openToggleC2S(callback, selected, MultiManualPackets.unsafeC2S());
        }
    }

    @Override
    public void editMacro(AutismMacro macro, Consumer<AutismMacro> onSaved) {
        AutismMacroEditorOverlay editor = AutismMacroEditorOverlay.getSharedOverlay();
        if (editor == null) return;
        AutismOverlayManager.get().register(editor);

        boolean inWorld = mc != null && mc.player != null && mc.level != null;
        editor.setConfigurationOnly(!inWorld);
        editor.openForMulti(macro, onSaved);
        AutismOverlayManager.get().bringToFront(editor);
    }

    @Override
    public void editFormValues(MultiProfile profile, Set<String> selectedAccounts, Consumer<MultiProfile> onSaved) {
        if (mc == null) return;
        net.minecraft.client.gui.screens.Screen parent = mc.gui.screen();
        suppressForAccounts = true;
        accountsReturnScreen = parent;
        panel.clearFocus();
        mc.gui.setScreen(new AutismFormValuesScreen(parent, profile, selectedAccounts, updated -> {
            onSaved.accept(updated);
        }));
    }
}
