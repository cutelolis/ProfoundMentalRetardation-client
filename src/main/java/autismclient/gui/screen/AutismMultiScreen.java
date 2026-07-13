package autismclient.gui.screen;

import autismclient.gui.vanillaui.UiBounds;
import autismclient.gui.vanillaui.UiRenderer;
import autismclient.gui.vanillaui.components.Button;
import autismclient.gui.vanillaui.components.CompactDropdown;
import autismclient.gui.vanillaui.components.CompactTheme;
import autismclient.gui.vanillaui.components.UiText;
import autismclient.gui.vanillaui.components.UiTone;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismAccountType;
import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.AutismTheme;
import autismclient.util.AutismTheme.Channel;
import autismclient.util.multi.MultiManager;
import autismclient.util.multi.MultiProfile;
import autismclient.util.multi.MultiProfileManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static autismclient.gui.screen.AutismScreenPalette.BG;
import static autismclient.gui.screen.AutismScreenPalette.BORDER;
import static autismclient.gui.screen.AutismScreenPalette.BORDER_ACTIVE;
import static autismclient.gui.screen.AutismScreenPalette.ERROR;
import static autismclient.gui.screen.AutismScreenPalette.MUTED;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG;
import static autismclient.gui.screen.AutismScreenPalette.PANEL_BG_SOFT;
import static autismclient.gui.screen.AutismScreenPalette.SUCCESS;
import static autismclient.gui.screen.AutismScreenPalette.TEXT;

public final class AutismMultiScreen extends AutismScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int MARGIN = 14;
    private static final int LEFT_WIDTH = 176;
    private static final int GAP = 8;
    private static final int ROW_HEIGHT = 24;
    private static final int[] PING_OPTIONS = {50, 100, 200, 300, 500, 1000};
    private static final List<String> PACING_OPTIONS = List.of("Gentle", "Balanced", "Fast", "Immediate", "Custom");
    private static final List<String> PROXY_MODE_OPTIONS = List.of("Off", "Auto", "Manual");

    private final Screen parent;
    private final List<CompactDropdown> dropdowns = new ArrayList<>();
    private final String prefillServer;
    private final boolean openedFromActiveConsole;
    private final EnumSet<AutismAccountType> typeFilters = EnumSet.allOf(AutismAccountType.class);
    private MultiProfile draft;
    private String selectedProfileId;
    private boolean dirty;
    private long lastDirtyAt;
    private EditBox nameField;
    private EditBox serverField;
    private EditBox concurrencyField;
    private EditBox delayField;
    private String accountSearch = "";
    private boolean searchFocused;
    private int profileScroll;
    private int accountScroll;
    private String lastActiveKey = "";

    public AutismMultiScreen(Screen parent, String prefillServer) {
        this(parent, prefillServer, false);
    }

    public AutismMultiScreen(Screen parent, String prefillServer, boolean openedFromActiveConsole) {
        super(Component.literal("Multi"));
        this.parent = parent;
        this.prefillServer = prefillServer == null ? "" : prefillServer.trim();
        this.openedFromActiveConsole = openedFromActiveConsole;
        List<MultiProfile> profiles = MultiProfileManager.get().all();
        MultiProfile active = MultiManager.get().activeProfile();

        MultiProfile shared = active == null ? MultiProfileManager.get().find(MultiProfileManager.get().selectedId()) : null;
        this.draft = active != null ? active : shared != null ? shared : profiles.isEmpty() ? newProfile() : new MultiProfile(profiles.getFirst());
        this.selectedProfileId = active != null || !profiles.isEmpty() ? this.draft.id : null;
        if (selectedProfileId != null) MultiProfileManager.get().setSelectedId(selectedProfileId);
    }

    @Override
    protected void init() {
        lastActiveKey = activeKey();
        rebuildControls();
    }

    @Override
    public void tick() {
        super.tick();

        if (dirty && System.currentTimeMillis() - lastDirtyAt >= 500L) {
            commit();
        }

        String key = activeKey();
        if (!key.equals(lastActiveKey) && !(getFocused() instanceof EditBox)) {
            captureFields();
            lastActiveKey = key;
            rebuildControls();
        }
    }

    private String activeKey() {
        MultiManager manager = MultiManager.get();
        return Boolean.toString(manager.isActive());
    }

    private void rebuildControls() {
        clearWidgets();
        dropdowns.clear();
        int ix = rightX() + 12;
        int innerW = rightWidth() - 24;
        int half = (innerW - 6) / 2;
        boolean custom = draft.pacing == MultiProfile.Pacing.Custom;
        boolean auto = draft.proxyMode == MultiProfile.ProxyMode.Auto;
        boolean locked = isActiveProfile(draft.id);
        nameField = serverField = concurrencyField = delayField = null;

        if (locked) buildActiveReadOnly(ix, innerW);
        if (!locked) {

        nameField = field(ix, 40, half, "Profile name", draft.name, 64);
        serverField = field(ix + half + 6, 40, innerW - half - 6, "host:port", draft.serverAddress, 255);

        if (auto) {
            int third = (innerW - 12) / 3;
            addPacingDropdown(ix, 66, third);
            addProxyModeDropdown(ix + third + 6, 66, third);
            int pingX = ix + 2 * (third + 6);
            dropdowns.add(new CompactDropdown(pingX, 66, innerW - 2 * (third + 6), 18,
                pingOptionLabels(), pingIndex(draft.autoMaxPingMs), index -> {
                    captureFields();
                    if (index >= 0 && index < PING_OPTIONS.length) draft.autoMaxPingMs = PING_OPTIONS[index];
                    commit();
                    rebuildControls();
                }).setButtonLabelOverride("<=" + draft.autoMaxPingMs + "ms"));
        } else {
            addPacingDropdown(ix, 66, half);
            addProxyModeDropdown(ix + half + 6, 66, innerW - half - 6);
        }

        if (custom) {
            concurrencyField = field(ix, 92, half, "Accounts 1-500", Integer.toString(draft.customConcurrency), 4);
            delayField = field(ix + half + 6, 92, innerW - half - 6, "Delay 0-5000ms", Integer.toString(draft.customDelayMs), 6);
        } else {
            concurrencyField = null;
            delayField = null;
        }

        int macroY = 92 + (custom ? 26 : 0);
        String allLabel = draft.allMacroName.isBlank() ? "Macro (all): none" : "Macro (all): " + draft.allMacroName;
        addStyled(ix, macroY, innerW, 18, allLabel,
            draft.allMacroName.isBlank() ? Button.Tone.NORMAL : Button.Tone.PRIMARY, b -> pickAllMacro());

        int formY = macroY + 22;
        addStyled(ix, formY, innerW, 18, "Passwords (login)", Button.Tone.NORMAL, b -> openFormValues());

        if (!compactVertical()) addAccountFilterChips(ix, chipsY(), innerW);
        }

        int footerY = height - 32;
        int profileFooterY = compactFooter() ? height - 54 : footerY;
        addStyled(MARGIN, profileFooterY, 60, 18, "New", Button.Tone.PRIMARY, b -> createNew());
        addStyled(MARGIN + 64, profileFooterY, 76, 18, "Duplicate", Button.Tone.NORMAL, b -> duplicateDraft());

        if (MultiManager.get().isActive()) {
            int activeW = Math.min(132, Math.max(1, width - MARGIN * 2 - 146));
            AutismStyledButton activeButton = new AutismStyledButton(MARGIN + 146, profileFooterY, activeW, 18,
                Component.literal("Active Multi"), Button.Tone.SUCCESS,
                () -> "Active " + MultiManager.get().connectedCount(), b -> openActiveConsole());
            addRenderableWidget(activeButton);
        }

        int footerX = rightX() + 12;
        int footerW = Math.max(1, rightWidth() - 24);
        int footerGap = footerW >= 220 ? 6 : 3;
        boolean compactActiveReturn = openedFromActiveConsole && MultiManager.get().isActive();
        int count = compactActiveReturn ? 2 : 3;
        int footerEach = Math.max(1, (footerW - footerGap * (count - 1)) / count);
        int fx = footerX;
        if (!compactActiveReturn) {
            addStyled(fx, footerY, footerEach, 18, "Advanced", Button.Tone.NORMAL, b -> openAdvanced());
            fx += footerEach + footerGap;
        }
        addStyled(fx, footerY, footerEach, 18, "Connect", Button.Tone.SUCCESS, b -> connect());
        fx += footerEach + footerGap;
        addStyled(fx, footerY, Math.max(1, footerX + footerW - fx), 18, "Back", Button.Tone.NORMAL, b -> onClose());

        addProfileButtons();
        addAccountButtons();
    }

    private void buildActiveReadOnly(int ix, int innerW) {
        disabledLabel(ix, 40, innerW, "Profile: " + draft.name);
        disabledLabel(ix, 62, innerW, "Server: " + (draft.serverAddress.isBlank() ? "-" : draft.serverAddress));
        disabledLabel(ix, 84, innerW, draft.sessions.size() + " accounts");
        AutismStyledButton note = addStyled(ix, 110, innerW, 18, "Batch running - Disconnect to edit", Button.Tone.DANGER, b -> {});
        note.active = false;
    }

    private void disabledLabel(int x, int y, int w, String text) {
        AutismStyledButton label = addStyled(x, y, w, 18, text, Button.Tone.NORMAL, b -> {});
        label.active = false;
    }

    private EditBox field(int x, int y, int w, String hint, String value, int max) {
        EditBox box = new EditBox(font, x, y, Math.max(1, w), 18, Component.literal(hint));
        box.setMaxLength(max);
        box.setValue(value);
        box.setHint(Component.literal(hint));
        box.setResponder(v -> markDirty());
        addRenderableWidget(box);
        return box;
    }

    private void addAccountFilterChips(int ix, int y, int innerW) {
        String[] labels = {"Cracked", "Session", "Microsoft", "TheAltening"};
        AutismAccountType[] types = {AutismAccountType.Cracked, AutismAccountType.Session, AutismAccountType.Microsoft, AutismAccountType.TheAltening};
        int gap = 4;
        int chipW = (innerW - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            AutismAccountType type = types[i];
            Button.Tone tone = typeFilters.contains(type) ? Button.Tone.SUCCESS : Button.Tone.NORMAL;
            AutismStyledButton filter = new AutismStyledButton(ix + i * (chipW + gap), y, Math.max(1, chipW), 14,
                Component.literal(fitLabel(labels[i], chipW - 6)), tone, b -> {
                captureFields();
                if (!typeFilters.add(type)) typeFilters.remove(type);
                accountScroll = 0;
                rebuildControls();
            });
            addRenderableWidget(filter);
        }
    }

    private void addProfileButtons() {
        List<MultiProfile> profiles = MultiProfileManager.get().all();
        int top = 40;
        int bottom = profileListBottom();
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        profileScroll = Math.max(0, Math.min(profileScroll, Math.max(0, profiles.size() - visible)));
        int end = Math.min(profiles.size(), profileScroll + visible);
        int y = top;
        int nameW = Math.max(24, leftWidth() - 16 - 26);
        for (int i = profileScroll; i < end; i++) {
            MultiProfile profile = profiles.get(i);
            Button.Tone tone = profile.id.equals(selectedProfileId) ? Button.Tone.SUCCESS : Button.Tone.NORMAL;
            String mark = profile.id.equals(selectedProfileId) ? "> " : "";
            addStyled(MARGIN + 8, y, nameW, 20, mark + profile.name, tone, b -> selectProfileRow(profile));
            AutismStyledButton delete = addStyled(MARGIN + 8 + nameW + 4, y, 22, 20, "X", Button.Tone.DANGER,
                b -> deleteProfile(profile));
            delete.active = !isActiveProfile(profile.id);
            y += ROW_HEIGHT;
        }
    }

    private void addAccountButtons() {
        List<AccountChoice> choices = accountChoices();
        boolean manual = draft.proxyMode == MultiProfile.ProxyMode.Manual;
        boolean locked = isActiveProfile(draft.id);
        int top = accountsTop();
        int bottom = accountListBottom();
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        accountScroll = Math.max(0, Math.min(accountScroll, Math.max(0, choices.size() - visible)));
        int end = Math.min(choices.size(), accountScroll + visible);
        int x = rightX() + 12;
        int w = rightWidth() - 24;
        int y = top;
        List<ProxyOption> proxyOptions = manual ? manualProxyOptions() : List.of();
        List<String> proxyLabels = proxyOptions.stream().map(ProxyOption::label).toList();
        for (int i = accountScroll; i < end; i++) {
            AccountChoice choice = choices.get(i);
            MultiProfile.SessionSpec selected = selectedSpec(choice.id());
            Button.Tone tone = selected == null ? Button.Tone.NORMAL : Button.Tone.SUCCESS;
            boolean canToggle = !choice.current() || selected != null;
            String accountLabel = choice.label() + (choice.current() ? " (Current)" : "");
            if (manual) {
                int proxyW = Math.max(1, Math.min(128, Math.max(36, w / 3)));
                if (proxyW + 4 >= w) proxyW = Math.max(1, w / 2);
                int toggleW = Math.max(1, w - proxyW - 4);
                AutismStyledButton toggle = addStyled(x, y, toggleW, 20, accountLabel, tone, b -> toggleAccount(choice.id()));
                toggle.active = canToggle && !locked;
                if (selected == null) {
                    AutismStyledButton proxyButton = addStyled(x + toggleW + 4, y, proxyW, 20,
                        "-", Button.Tone.NORMAL, b -> { });
                    proxyButton.active = false;
                } else {
                    CompactDropdown proxyDropdown = new CompactDropdown(x + toggleW + 4, y, proxyW, 20,
                        proxyLabels, proxyOptionIndex(proxyOptions, selected.proxyId()), index -> {
                            MultiProfile.SessionSpec spec = selectedSpec(choice.id());
                            if (spec != null && index >= 0 && index < proxyOptions.size()) {
                                setSessionProxy(spec, proxyOptions.get(index).id());
                            }
                        }).setButtonLabelOverride(proxyLabel(selected.proxyId()));
                    proxyDropdown.active = !choice.current() && !locked;
                    dropdowns.add(proxyDropdown);
                }
            } else {
                AutismStyledButton toggle = addStyled(x, y, w, 20, accountLabel, tone, b -> toggleAccount(choice.id()));
                toggle.active = canToggle && !locked;
            }
            y += ROW_HEIGHT;
        }
    }

    private void captureFields() {
        if (nameField != null) draft.name = safeTrim(nameField.getValue());
        if (serverField != null) draft.serverAddress = safeTrim(serverField.getValue());
        if (concurrencyField != null) draft.customConcurrency = parseInt(concurrencyField.getValue(), draft.customConcurrency);
        if (delayField != null) draft.customDelayMs = parseInt(delayField.getValue(), draft.customDelayMs);
        draft.normalize();
    }

    private void markDirty() {
        dirty = true;
        lastDirtyAt = System.currentTimeMillis();
    }

    private void commit() {
        captureFields();
        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        dirty = false;
    }

    private void connect() {
        captureFields();
        draft.name = MultiProfileManager.get().nextAvailableName(draft.name, draft.id);
        MultiManager.StartResult result = MultiManager.get().start(draft);
        if (!result.ok()) {
            toast(MultiManager.singleLine(result.message(), 90), ERROR);
            return;
        }

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        dirty = false;
        minecraft.gui.setScreen(new AutismMultiConsoleScreen(parent));
    }

    private void openActiveConsole() {
        if (minecraft != null) minecraft.gui.setScreen(new AutismMultiConsoleScreen(parent));
    }

    private void createNew() {
        if (dirty) commit();
        draft = newProfile();

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        dirty = false;
        profileScroll = 0;
        accountScroll = 0;
        toast("New profile created.", SUCCESS);
        rebuildControls();
    }

    private void duplicateDraft() {
        if (dirty) commit();
        MultiProfile copy = new MultiProfile(draft);
        copy.id = java.util.UUID.randomUUID().toString();
        copy.name = MultiProfileManager.get().nextAvailableName(copy.name, copy.id);
        draft = copy;

        MultiProfileManager.get().put(draft);
        selectedProfileId = draft.id;
        dirty = false;
        toast("Duplicated.", SUCCESS);
        rebuildControls();
    }

    private void deleteProfile(MultiProfile profile) {
        captureFields();
        if (profile != null && isActiveProfile(profile.id)) {
            toast("Disconnect the active profile first.", ERROR);
            return;
        }
        MultiProfileManager.get().remove(profile.id);
        if (profile.id.equals(selectedProfileId) || draft.id.equals(profile.id)) {
            List<MultiProfile> remaining = MultiProfileManager.get().all();
            draft = remaining.isEmpty() ? newProfile() : new MultiProfile(remaining.getFirst());
            selectedProfileId = remaining.isEmpty() ? null : draft.id;
            dirty = false;
        }
        toast("Deleted " + MultiManager.singleLine(profile.name, 40) + ".", MUTED);
        rebuildControls();
    }

    private void openAdvanced() {
        captureFields();
        minecraft.gui.setScreen(new AutismMultiPacketPolicyScreen(this, draft.packetPolicy, policy -> {
            draft.packetPolicy = policy;
            commit();
        }, false));
    }

    private void openFormValues() {
        captureFields();
        Set<String> accounts = new java.util.LinkedHashSet<>();
        for (MultiProfile.SessionSpec spec : draft.sessions) accounts.add(spec.accountId());
        minecraft.gui.setScreen(new AutismFormValuesScreen(this, draft, accounts, updated -> {
            draft = updated;
            commit();
            rebuildControls();
        }));
    }

    private void loadProfile(MultiProfile profile) {
        if (dirty) commit();
        draft = new MultiProfile(profile);
        selectedProfileId = draft.id;
        MultiProfileManager.get().setSelectedId(draft.id);
        dirty = false;
        accountScroll = 0;
        rebuildControls();
    }

    private void selectProfileRow(MultiProfile profile) {
        if (profile == null) return;
        if (profile.id.equals(selectedProfileId)) {

            return;
        }
        loadProfile(profile);
    }

    private void addPacingDropdown(int x, int y, int width) {
        dropdowns.add(new CompactDropdown(x, y, width, 18, PACING_OPTIONS, draft.pacing.ordinal(), index -> {
            captureFields();
            if (index >= 0 && index < MultiProfile.Pacing.values().length) {
                draft.pacing = MultiProfile.Pacing.values()[index];
                commit();
                rebuildControls();
            }
        }).setButtonLabelOverride("Join speed: " + draft.pacing.name()));
    }

    private void addProxyModeDropdown(int x, int y, int width) {
        dropdowns.add(new CompactDropdown(x, y, width, 18, PROXY_MODE_OPTIONS, draft.proxyMode.ordinal(), index -> {
            captureFields();
            if (index >= 0 && index < MultiProfile.ProxyMode.values().length) {
                draft.proxyMode = MultiProfile.ProxyMode.values()[index];
                commit();
                rebuildControls();
            }
        }).setButtonLabelOverride("Proxy: " + proxyModeLabel()));
    }

    private void pickAllMacro() {
        captureFields();
        minecraft.gui.setScreen(new AutismMultiMacroPickerScreen(this, draft.allMacroName, name -> {
            draft.allMacroName = name == null ? "" : name;
            commit();
        }));
    }

    private void toggleAccount(String accountId) {
        if (isActiveProfile(draft.id)) return;
        captureFields();
        MultiProfile.SessionSpec existing = selectedSpec(accountId);
        if (existing == null && MultiManager.isCurrentRenderedAccount(accountId)) {
            toast("Current account cannot join Multi.", ERROR);
            return;
        }
        if (existing != null) {
            draft.sessions.remove(existing);
        } else if (draft.sessions.size() < MultiProfile.MAX_SESSIONS) {
            draft.sessions.add(new MultiProfile.SessionSpec(accountId, ""));
        } else {
            toast("Maximum " + MultiProfile.MAX_SESSIONS + " accounts.", ERROR);
        }
        commit();
        rebuildControls();
    }

    private void setSessionProxy(MultiProfile.SessionSpec existing, String proxyId) {
        if (isActiveProfile(draft.id)) return;
        int index = draft.sessions.indexOf(existing);
        if (index >= 0) draft.sessions.set(index,
            new MultiProfile.SessionSpec(existing.accountId(), proxyId, existing.macroName()));
        commit();
        rebuildControls();
    }

    private MultiProfile.SessionSpec selectedSpec(String accountId) {
        for (MultiProfile.SessionSpec spec : draft.sessions) {
            if (spec.accountId().equals(accountId)) return spec;
        }
        return null;
    }

    private String proxyLabel(String proxyId) {
        if (proxyId == null || proxyId.isBlank()) return "Proxy Off";
        if (MultiProfile.BEST_PROXY_ID.equals(proxyId)) return "Best Proxy";
        AutismProxy proxy = AutismProxyManager.get().findById(proxyId);
        return proxy == null ? "Missing" : proxy.displayName();
    }

    private String proxyModeLabel() {
        return switch (draft.proxyMode) {
            case Off -> "Off";
            case Auto -> "Auto";
            case Manual -> "Manual";
        };
    }

    private List<ProxyOption> manualProxyOptions() {
        List<ProxyOption> options = new ArrayList<>();
        options.add(new ProxyOption("", "Proxy Off"));
        List<AutismProxy> proxies = AutismProxyManager.get().all().stream()
            .filter(proxy -> proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD)
            .sorted(java.util.Comparator.comparing(AutismProxy::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        if (!proxies.isEmpty()) options.add(new ProxyOption(MultiProfile.BEST_PROXY_ID, "Best Proxy"));
        for (AutismProxy proxy : proxies) options.add(new ProxyOption(proxy.stableId(), proxy.displayName()));
        return options;
    }

    private static int proxyOptionIndex(List<ProxyOption> options, String proxyId) {
        String id = proxyId == null ? "" : proxyId;
        for (int i = 0; i < options.size(); i++) if (options.get(i).id().equals(id)) return i;
        return 0;
    }

    private List<AccountChoice> accountChoices() {
        List<AccountChoice> choices = new ArrayList<>();
        Set<String> known = new HashSet<>();
        choices.add(new AccountChoice(MultiProfile.DEFAULT_ACCOUNT_ID,
            AutismAccountSessionSwitcher.getOriginalUser().getName() + " (Default)",
            MultiManager.isCurrentRenderedAccount(MultiProfile.DEFAULT_ACCOUNT_ID)));
        known.add(MultiProfile.DEFAULT_ACCOUNT_ID);
        String query = accountSearch.toLowerCase(Locale.ROOT);
        for (AutismAccount account : AutismAccountManager.get().all()) {
            known.add(account.stableId());
            AutismAccountType type = account.type == null ? AutismAccountType.Cracked : account.type;
            if (!typeFilters.contains(type)) continue;
            String label = account.displayName() + " (" + type.name() + ")";
            if (!query.isEmpty() && !label.toLowerCase(Locale.ROOT).contains(query)) continue;
            choices.add(new AccountChoice(account.stableId(), label, MultiManager.isCurrentRenderedAccount(account.stableId())));
        }
        for (MultiProfile.SessionSpec spec : draft.sessions) {
            if (known.add(spec.accountId())) {
                choices.add(new AccountChoice(spec.accountId(), "Missing account: " + spec.accountId(), false));
            }
        }
        return choices;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (searchFocused) {
            int cp = event.codepoint();
            if (cp >= 0x20 && cp != 0x7F && accountSearch.length() < 48) {
                accountSearch += (char) cp;
                accountScroll = 0;
                rebuildControls();
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && CompactDropdown.closeOpenMenu(dropdowns)) return true;
        if (searchFocused) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!accountSearch.isEmpty()) {
                    accountSearch = accountSearch.substring(0, accountSearch.length() - 1);
                    accountScroll = 0;
                    rebuildControls();
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (CompactDropdown.mouseScrolled(dropdowns, mouseX, mouseY, vertical)) return true;
        if (mouseX < MARGIN + leftWidth()) {
            captureFields();
            profileScroll = Math.max(0, profileScroll + (vertical < 0 ? 1 : -1));
            rebuildControls();
            return true;
        }
        if (mouseX >= rightX() && mouseY >= accountsTop() - 4) {
            captureFields();
            accountScroll = Math.max(0, accountScroll + (vertical < 0 ? 1 : -1));
            rebuildControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (CompactDropdown.mouseClicked(dropdowns, event.x(), event.y(), event.button())) return true;
        if (event.button() == 0 && searchBox().contains((int) event.x(), (int) event.y())) {
            captureFields();
            searchFocused = true;
            clearInputFocus();
            return true;
        }
        searchFocused = false;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (CompactDropdown.mouseReleased(dropdowns)) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (CompactDropdown.mouseDragged(dropdowns, event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        UiRenderer.rect(graphics, UiBounds.of(0, 0, width, height), themeBg());
        int leftPanelHeight = Math.max(1, profileListBottom() + 6 - 14);
        int rightPanelHeight = Math.max(1, accountListBottom() + 6 - 14);
        UiRenderer.frame(graphics, UiBounds.of(MARGIN, 14, leftWidth(), leftPanelHeight), themePanelSoft(), themeBorder());
        UiRenderer.frame(graphics, UiBounds.of(rightX(), 14, rightWidth(), rightPanelHeight), themePanel(), themeBorder());
        drawText(graphics, "Profiles", MARGIN + 10, 24, themeText());
        drawText(graphics, profileStateLabel(), rightX() + 12, 24, themeText());

        int ix = rightX() + 12;
        int innerW = rightWidth() - 24;
        drawText(graphics, "Accounts", ix, accountsBaseY(), themeText());
        drawRight(graphics, draft.sessions.size() + " selected", ix + innerW, accountsBaseY(), themeMuted());
        renderSearchBox(graphics);

        boolean menuOpen = CompactDropdown.isMenuOpen(dropdowns);
        int mx = menuOpen ? Integer.MIN_VALUE : mouseX;
        int my = menuOpen ? Integer.MIN_VALUE : mouseY;
        super.extractRenderState(graphics, mx, my, delta);
        CompactDropdown.renderAll(graphics, font, dropdowns, mouseX, mouseY);
    }

    private void renderSearchBox(GuiGraphicsExtractor graphics) {
        UiBounds box = searchBox();
        UiRenderer.frame(graphics, box, themePanelSoft(), searchFocused ? AutismTheme.recolor(BORDER_ACTIVE, Channel.OUTLINE) : themeBorder());
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        if (accountSearch.isEmpty() && !searchFocused) {
            UiText.draw(graphics, font, "Search accounts...", fontId, themeMuted(), box.x() + 5, box.y() + 4, false);
        } else {
            String shown = UiText.trimToWidthEllipsis(font, accountSearch, box.width() - 12, fontId, themeText());
            UiText.draw(graphics, font, searchFocused ? shown + "_" : shown, fontId, themeText(), box.x() + 5, box.y() + 4, false);
        }
    }

    private UiBounds searchBox() {
        int ix = rightX() + 12;
        return UiBounds.of(ix, accountsBaseY() + 12, rightWidth() - 24, 16);
    }

    private String profileStateLabel() {
        if (selectedProfileId == null) return "New profile";
        String name = MultiManager.singleLine(draft.name, 28);
        return "Editing " + name + (dirty ? " (changed)" : "");
    }

    private int accountsBaseY() {
        return 92 + (draft.pacing == MultiProfile.Pacing.Custom ? 26 : 0) + 52;
    }

    private int chipsY() {
        return accountsBaseY() + 32;
    }

    private int accountsTop() {
        return accountsBaseY() + (compactVertical() ? 32 : 52);
    }

    @Override
    public void onClose() {
        if (dirty) commit();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private AutismStyledButton addStyled(int x, int y, int w, int h, String text, Button.Tone tone,
                                         net.minecraft.client.gui.components.Button.OnPress press) {
        Button.Tone interactiveTone = tone == Button.Tone.NORMAL ? Button.Tone.SECONDARY : tone;
        AutismStyledButton button = new AutismStyledButton(x, y, Math.max(1, w), h,
            Component.literal(fitLabel(text, w - 6)), interactiveTone, press);
        addRenderableWidget(button);
        return button;
    }

    private String fitLabel(String text, int width) {
        return UiText.trimToWidthEllipsis(font, MultiManager.singleLine(text, 72), Math.max(1, width),
            THEME.fontFor(UiTone.BODY), themeText());
    }

    private void clearInputFocus() {
        if (nameField != null) nameField.setFocused(false);
        if (serverField != null) serverField.setFocused(false);
        if (concurrencyField != null) concurrencyField.setFocused(false);
        if (delayField != null) delayField.setFocused(false);
        this.setFocused(null);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        UiText.draw(graphics, font, MultiManager.singleLine(text, 120), fontId, color, x, y, false);
    }

    private void drawRight(GuiGraphicsExtractor graphics, String text, int right, int y, int color) {
        Identifier fontId = THEME.fontFor(UiTone.BODY);
        int w = UiText.width(font, text, fontId, color);
        UiText.draw(graphics, font, text, fontId, color, right - w, y, false);
    }

    private static int themeBg() {
        return AutismTheme.recolor(BG, Channel.BACKDROP);
    }

    private static int themePanel() {
        return AutismTheme.recolor(PANEL_BG, Channel.BUTTON);
    }

    private static int themePanelSoft() {
        return AutismTheme.recolor(PANEL_BG_SOFT, Channel.BUTTON);
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

    private MultiProfile newProfile() {
        MultiProfile profile = new MultiProfile();
        profile.name = MultiProfileManager.get().nextAvailableName("New profile", profile.id);
        profile.serverAddress = prefillServer;

        if (!MultiManager.isCurrentRenderedAccount(MultiProfile.DEFAULT_ACCOUNT_ID)) {
            profile.sessions.add(new MultiProfile.SessionSpec(MultiProfile.DEFAULT_ACCOUNT_ID, ""));
        } else {
            for (AutismAccount account : AutismAccountManager.get().all()) {
                if (!MultiManager.isCurrentRenderedAccount(account.stableId())) {
                    profile.sessions.add(new MultiProfile.SessionSpec(account.stableId(), ""));
                    break;
                }
            }
        }
        return profile;
    }

    private boolean isActiveProfile(String profileId) {
        if (profileId == null || !MultiManager.get().isActive()) return false;
        MultiProfile active = MultiManager.get().activeProfile();
        return active != null && profileId.equals(active.id);
    }

    private int rightX() {
        return MARGIN + leftWidth() + GAP;
    }

    private int rightWidth() {
        return Math.max(1, width - rightX() - MARGIN);
    }

    private int leftWidth() {
        int roomAfterMinimumEditor = width - MARGIN * 2 - GAP - 180;
        return Math.max(90, Math.min(LEFT_WIDTH, roomAfterMinimumEditor));
    }

    private boolean compactFooter() {
        return width < 620;
    }

    private boolean compactVertical() {
        return height < 300;
    }

    private int profileListBottom() {
        return height - (compactFooter() ? 70 : 48);
    }

    private int accountListBottom() {
        return height - 48;
    }

    private static List<String> pingOptionLabels() {
        List<String> labels = new ArrayList<>(PING_OPTIONS.length);
        for (int option : PING_OPTIONS) labels.add(option + "ms");
        return labels;
    }

    private static int pingIndex(int value) {
        int index = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < PING_OPTIONS.length; i++) {
            int distance = Math.abs(PING_OPTIONS[i] - value);
            if (distance < best) {
                best = distance;
                index = i;
            }
        }
        return index;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record AccountChoice(String id, String label, boolean current) {
    }

    private record ProxyOption(String id, String label) {
    }
}
