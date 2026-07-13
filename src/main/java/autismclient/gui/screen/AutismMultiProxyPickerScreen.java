package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.CompactOverlayButton;
import autismclient.gui.vanillaui.components.CompactScrollbar;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismBackgroundTasks;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.BORDER_ACTIVE;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiProxyPickerScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MARGIN = 14;

    private static final int ROW_HEIGHT = 30;
    private static final int ROW_FRAME_H = ROW_HEIGHT - 6;

    private final Screen parent;
    private final String currentProxyId;
    private final Consumer<String> onPick;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private EditBox searchField;
    private String search = "";
    private int scrollOffset;
    private boolean scrollbarDragging;
    private int scrollbarGrab;
    private long lastRevision = Long.MIN_VALUE;
    private long cachedFilterRevision = Long.MIN_VALUE;
    private String cachedFilterSearch = "";
    private List<AutismProxy> cachedFiltered = List.of();

    public AutismMultiProxyPickerScreen(Screen parent, String currentProxyId, Consumer<String> onPick) {
        super(Component.literal("Choose Proxy"));
        this.parent = parent;
        this.currentProxyId = currentProxyId == null ? "" : currentProxyId;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        searchField = new EditBox(font, MARGIN + 10, 42, panelW() - 24, 18, Component.literal("Search proxies"));
        searchField.setHint(Component.literal("Search name/address..."));
        searchField.setMaxLength(128);
        searchField.setValue(search);
        searchField.setResponder(value -> {
            search = safeTrim(value);
            scrollOffset = 0;
            rebuild();
        });
        addRenderableWidget(searchField);
        AutismProxyManager.get().requestGeoLookup(false);
        rebuild();
    }

    private void rebuild() {
        buttons.clear();
        rows.clear();
        AutismProxyManager manager = AutismProxyManager.get();
        boolean refreshing = manager.refreshStatus().running();

        buttons.add(CompactOverlayButton.create(width - MARGIN - 10 - 60, 22, 60, 18, Component.literal("Back"),
            b -> onClose()).setVariant(CompactOverlayButton.Variant.SECONDARY));
        buttons.add(CompactOverlayButton.create(width - MARGIN - 10 - 60 - 6 - 78, 22, 78, 18,
            Component.literal(refreshing ? "Cancel" : "Check all"), b -> checkAll())
            .setVariant(refreshing ? CompactOverlayButton.Variant.DANGER : CompactOverlayButton.Variant.PRIMARY));

        List<AutismProxy> proxies = filteredProxies();
        int total = 1 + proxies.size();
        int viewport = rowsBottom() - rowsTop();
        int visible = Math.max(1, viewport / ROW_HEIGHT);
        int maxScroll = Math.max(0, total - visible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int y = rowsTop();
        for (int i = scrollOffset; i < total && y + ROW_HEIGHT <= rowsBottom(); i++) {
            if (i == 0) {
                rows.add(new Row(null, y, null));
            } else {
                AutismProxy proxy = proxies.get(i - 1);
                CompactOverlayButton check = CompactOverlayButton.create(rowRight() - 56, y + 4, 56, 16,
                    Component.literal(proxy.status == AutismProxy.Status.CHECKING ? "..." : "Check"), b -> check(proxy))
                    .setVariant(CompactOverlayButton.Variant.SECONDARY);
                check.active = proxy.status != AutismProxy.Status.CHECKING;
                rows.add(new Row(proxy, y, check));
            }
            y += ROW_HEIGHT;
        }
        lastRevision = revisionKey();
    }

    private void pick(String proxyId) {
        if (onPick != null) onPick.accept(proxyId == null ? "" : proxyId);
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void check(AutismProxy proxy) {
        if (proxy == null || proxy.status == AutismProxy.Status.CHECKING) return;
        proxy.status = AutismProxy.Status.CHECKING;
        proxy.latency = 0L;
        rebuild();
        AutismBackgroundTasks.runTracked("Autism-MultiPicker-Check", () -> {
            proxy.checkStatus(AutismProxyManager.get().getTimeoutMs());
            if (minecraft != null) minecraft.execute(this::rebuild);
        });
    }

    private void checkAll() {
        AutismProxyManager manager = AutismProxyManager.get();
        if (manager.refreshStatus().running()) {
            manager.cancelRefresh();
        } else {
            manager.startRefresh(true);
        }
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (revisionKey() != lastRevision) rebuild();
    }

    private long revisionKey() {
        AutismProxyManager manager = AutismProxyManager.get();
        return manager.listRevision() * 31L + manager.refreshStatus().revision();
    }

    private List<AutismProxy> filteredProxies() {
        String query = search.toLowerCase(Locale.ROOT);
        long revision = AutismProxyManager.get().listRevision();
        if (revision == cachedFilterRevision && query.equals(cachedFilterSearch)) return cachedFiltered;
        List<AutismProxy> out = new ArrayList<>();
        for (AutismProxy proxy : AutismProxyManager.get().all()) {
            if (proxy == null || !proxy.isValid()) continue;
            if (query.isEmpty() || matches(proxy, query)) out.add(proxy);
        }
        cachedFilterRevision = revision;
        cachedFilterSearch = query;
        cachedFiltered = List.copyOf(out);
        return cachedFiltered;
    }

    private static boolean matches(AutismProxy proxy, String query) {
        String haystack = (proxy.displayName() + " " + proxy.address + " " + proxy.port + " "
            + proxy.type + " " + proxy.geoSearchText()).toLowerCase(Locale.ROOT);
        return haystack.contains(query);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            CompactScrollbar.Metrics bar = scrollbarMetrics();
            if (bar.hasScroll() && bar.contains(event.x(), event.y())) {
                scrollbarDragging = true;
                scrollbarGrab = bar.overThumb(event.x(), event.y()) ? (int) Math.round(event.y() - bar.thumbY()) : bar.thumbHeight() / 2;
                scrollOffset = clampScroll(CompactScrollbar.scrollFromThumb(bar, event.y(), scrollbarGrab) / ROW_HEIGHT);
                rebuild();
                return true;
            }
            for (CompactOverlayButton button : buttons) {
                if (CompactOverlayButton.fireIfHit(button, event.x(), event.y(), event.button())) return true;
            }
            for (Row row : rows) {
                if (row.check() != null && CompactOverlayButton.fireIfHit(row.check(), event.x(), event.y(), event.button())) return true;
            }
            for (Row row : rows) {
                if (event.x() >= rowX() && event.x() < rowRight() && event.y() >= row.y() && event.y() < row.y() + ROW_FRAME_H) {
                    pick(row.proxy() == null ? "" : row.proxy().stableId());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (scrollbarDragging) {
            CompactScrollbar.Metrics bar = scrollbarMetrics();
            scrollOffset = clampScroll(CompactScrollbar.scrollFromThumb(bar, event.y(), scrollbarGrab) / ROW_HEIGHT);
            rebuild();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollOffset = clampScroll(scrollOffset + (vertical < 0 ? 1 : -1));
        rebuild();
        return true;
    }

    private CompactScrollbar.Metrics scrollbarMetrics() {
        int total = 1 + filteredProxies().size();
        int viewport = rowsBottom() - rowsTop();
        return CompactScrollbar.compute(total * ROW_HEIGHT, viewport, MARGIN + panelW() - 8, rowsTop(), 4, viewport, scrollOffset * ROW_HEIGHT);
    }

    private int clampScroll(int rows) {
        int total = 1 + filteredProxies().size();
        int visible = Math.max(1, (rowsBottom() - rowsTop()) / ROW_HEIGHT);
        return Math.max(0, Math.min(rows, Math.max(0, total - visible)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), themeBg());
        UiRenderer.frame(graphics, UiBounds.of(MARGIN, 14, panelW(), height - 28), themePanel(), themeBorder());
        drawText(graphics, "Choose Proxy", MARGIN + 10, 24, themeText());

        int total = 1 + filteredProxies().size();
        for (Row row : rows) renderRow(graphics, row);
        if (total == 1) drawText(graphics, "No proxies saved. Add some in Proxies.", rowX(), rowsTop() + 8, themeMuted());

        for (CompactOverlayButton button : buttons) CompactOverlayButton.renderStyled(graphics, font, button, mouseX, mouseY);

        CompactScrollbar.Metrics bar = scrollbarMetrics();
        if (bar.hasScroll()) CompactScrollbar.draw(graphics, bar, bar.contains(mouseX, mouseY), scrollbarDragging);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderRow(GuiGraphicsExtractor graphics, Row row) {
        boolean direct = row.proxy() == null;
        boolean selected = direct ? currentProxyId.isBlank() : currentProxyId.equals(row.proxy().stableId());
        int fill = selected ? themeSelected() : AutismTheme.recolor(PANEL_BG_SOFT, Channel.BUTTON);
        int border = selected ? AutismTheme.recolor(BORDER_ACTIVE, Channel.SUCCESS) : themeBorder();
        UiRenderer.frame(graphics, UiBounds.of(rowX(), row.y(), rowRight() - rowX(), ROW_FRAME_H), fill, border);
        if (direct) {
            drawText(graphics, "Proxy Off", rowX() + 8, row.y() + 4, selected ? themeSuccess() : themeText());
            drawText(graphics, "No proxy", rowX() + 8, row.y() + 14, themeMuted());
            return;
        }
        AutismProxy proxy = row.proxy();
        int checkLeft = row.check() != null ? row.check().getX() : rowRight();
        int statusX = Math.max(rowX() + 8, checkLeft - 8 - 64);
        int textRight = Math.max(rowX() + 28, statusX - 8);
        int textW = Math.max(20, textRight - rowX() - 8);
        drawFitted(graphics, proxy.displayName(), rowX() + 8, row.y() + 4, textW, selected ? themeSuccess() : themeText());
        drawFitted(graphics, proxy.type + "  " + proxy.address + ":" + proxy.port, rowX() + 8, row.y() + 14, textW, themeMuted());
        drawFitted(graphics, statusText(proxy), statusX, row.y() + 4, 62, proxy.status.color());
        drawFitted(graphics, proxy.geoLabel(), statusX, row.y() + 14, 62, proxy.geoColor());
        if (row.check() != null) CompactOverlayButton.renderStyled(graphics, font, row.check(), Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private static String statusText(AutismProxy proxy) {
        return switch (proxy.status) {
            case ALIVE -> proxy.latency + "ms";
            case DEAD -> "Dead";
            case CHECKING -> "Checking";
            case UNCHECKED -> "Unchecked";
        };
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private int panelW() {
        return width - 2 * MARGIN;
    }

    private int rowX() {
        return MARGIN + 10;
    }

    private int rowRight() {
        return MARGIN + panelW() - 14;
    }

    private int rowsTop() {
        return 66;
    }

    private int rowsBottom() {
        return height - 18;
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        UiText.draw(graphics, font, text, fontId, color, x, y, false);
    }

    private void drawFitted(GuiGraphicsExtractor graphics, String text, int x, int y, int maxWidth, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        String safe = UiText.trimToWidthEllipsis(font, text == null ? "" : text, Math.max(1, maxWidth), fontId, color);
        UiText.draw(graphics, font, safe, fontId, color, x, y, false);
    }

    private static int themeBg() {
        return AutismTheme.recolor(BG, Channel.BACKDROP);
    }

    private static int themePanel() {
        return AutismTheme.recolor(PANEL_BG, Channel.BUTTON);
    }

    private static int themeSelected() {
        return AutismTheme.recolor(0x3324D86A, Channel.SUCCESS);
    }

    private static int themeBorder() {
        return AutismTheme.recolor(BORDER, Channel.OUTLINE);
    }

    private static int themeText() {
        return AutismTheme.recolor(TEXT, Channel.TEXT);
    }

    private static int themeMuted() {
        return AutismTheme.recolor(MUTED, Channel.TEXT);
    }

    private static int themeSuccess() {
        return AutismTheme.recolor(SUCCESS, Channel.SUCCESS);
    }

    private record Row(AutismProxy proxy, int y, CompactOverlayButton check) {
    }
}
