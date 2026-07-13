package autismclient.util.multi;

import autismclient.commands.AutismCommands;
import autismclient.util.AutismAccount;
import autismclient.util.AutismAccountManager;
import autismclient.util.AutismAccountSessionSwitcher;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismProxy;
import autismclient.util.AutismMacro;
import autismclient.util.AutismMacroManager;
import autismclient.util.AutismProxyManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MultiManager implements MultiSession.Sink {
    public record StartResult(boolean ok, String message) {
        static StartResult success() {
            return new StartResult(true, "");
        }

        static StartResult error(String message) {
            return new StartResult(false, message);
        }
    }

    public record BroadcastResult(int sent, int skipped, int failed, List<String> details) {
        public String summary() {
            return "Sent " + sent + ", skipped " + skipped + ", failed " + failed;
        }
    }

    public record RetryResult(boolean ok, String message) {
        static RetryResult ok(String message) {
            return new RetryResult(true, message);
        }

        static RetryResult error(String message) {
            return new RetryResult(false, message);
        }
    }

    private record Pending(MultiSession session, InetSocketAddress address, String host, int port) {
    }

    private record ChatMsg(long seq, long time, Component component, String text, String source) {
    }

    private static final class UnifiedMsg {
        final long seq;
        final long time;
        int count = 1;
        final String text;
        final String source;
        final boolean system;
        final Component representative;
        final Map<String, Component> perAccount = new LinkedHashMap<>();

        UnifiedMsg(long seq, long time, String text, String source, boolean system, Component representative) {
            this.seq = seq;
            this.time = time;
            this.text = text;
            this.source = source == null ? "" : source;
            this.system = system;
            this.representative = representative;
        }
    }

    public record ChatLine(long seq, long time, Component render, Map<String, Component> targets, boolean system,
                           int count, String source) {
    }

    private static String groupKey(String source, String text) {
        return (source == null ? "" : source) + '\0' + text;
    }

    private static volatile MultiManager instance;
    private static final int CHAT_LIMIT = 100;

    private static final long CHAT_MERGE_MS = 2_000L;

    private static final int PROXY_SAMPLE_COUNT = 3;
    private static final int PROXY_CANDIDATE_CAP = 100;

    private static final long PROXY_SAMPLE_MAX_MS = 30_000L;

    private static final long SNAPSHOT_INTERVAL_MS = 100L;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Autism-Multi-Scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService workers = Executors.newFixedThreadPool(
        Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
        runnable -> {
            Thread thread = new Thread(runnable, "Autism-Multi-Worker");
            thread.setDaemon(true);
            return thread;
        }
    );
    private final AtomicLong generations = new AtomicLong();
    private final Map<String, MultiSession> sessions = new LinkedHashMap<>();

    private volatile List<MultiSession> sessionList = List.of();
    private volatile List<MultiSession.Snapshot> snapshotList = List.of();
    private volatile Map<String, MultiSession> sessionsById = Map.of();
    private volatile long sessionRevision;
    private volatile long uiRevision;
    private final Map<String, MultiProfile.SessionSpec> runtimeSpecs = new HashMap<>();
    private final Map<String, AutismProxy> runtimeProxies = new HashMap<>();

    private final Set<String> macroRunning = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Set<MultiSession> connecting = new HashSet<>();
    private final Map<java.util.UUID, String> resolvedIdentities = new HashMap<>();
    private final Map<String, String> lastFailedProxyIds = new HashMap<>();
    private final Map<String, MultiSession.Status> lastPostedStatus = new HashMap<>();
    private final Map<String, Long> retryTokens = new HashMap<>();
    private final Set<String> retryingAccounts = new HashSet<>();
    private final Map<String, ArrayDeque<ChatMsg>> accountChat = new HashMap<>();
    private final ArrayDeque<UnifiedMsg> unifiedChat = new ArrayDeque<>();
    private final Map<String, UnifiedMsg> recentByText = new HashMap<>();
    private long chatSeq;
    private volatile long chatRevision;

    private final ArrayDeque<String> commandHistory = new ArrayDeque<>();
    private String suggestSourceId = "";
    private int suggestId = -1;
    private String suggestText = "";

    private long generation;
    private long retrySerial;
    private boolean passwordPromptShown;
    private MultiProfile activeProfile;

    private java.util.UUID renderedProfileIdAtStart;
    private long nextStartAt;
    private long lastSnapshotPublishAt;
    private boolean active;
    private MultiViaCompat.Target viaTarget = new MultiViaCompat.Target(false, null, "Native");
    private ScheduledFuture<?> tickTask;
    private String rememberedServerAddress = "";
    private MultiViaCompat.Target rememberedServerTarget;

    private MultiManager() {
    }

    public static MultiManager get() {
        MultiManager current = instance;
        if (current != null) return current;
        synchronized (MultiManager.class) {
            if (instance == null) instance = new MultiManager();
            return instance;
        }
    }

    public static MultiManager getIfInitialized() {
        return instance;
    }

    public synchronized StartResult start(MultiProfile source) {
        if (source == null) return StartResult.error("Profile is missing");
        if (active) return StartResult.error("Disconnect the active Multi batch first");
        MultiProfile profile = new MultiProfile(source);
        profile.normalize();
        java.util.UUID renderedProfileId = currentRenderedProfileId();
        StartResult validation = validate(profile, renderedProfileId);
        if (!validation.ok()) return validation;

        ServerAddress server = ServerAddress.parseString(profile.serverAddress);
        MultiViaCompat.Target selectedVia = MultiViaCompat.captureSelectedTarget();
        if (MultiViaCompat.isAutoDetect(selectedVia)
            && profile.serverAddress.equalsIgnoreCase(rememberedServerAddress)
            && rememberedServerTarget != null
            && rememberedServerTarget.version() != null) {
            selectedVia = rememberedServerTarget;
        }
        String viaError = MultiViaCompat.validateSelectedTarget(selectedVia);
        if (!viaError.isBlank()) return StartResult.error(viaError);

        generation = generations.incrementAndGet();
        activeProfile = profile;
        renderedProfileIdAtStart = renderedProfileId;
        viaTarget = selectedVia;
        active = true;
        ensureTicking();
        nextStartAt = 0L;
        lastSnapshotPublishAt = 0L;
        sessions.clear();
        republishSessions();
        runtimeSpecs.clear();
        runtimeProxies.clear();
        macroRunning.clear();
        pending.clear();
        connecting.clear();
        resolvedIdentities.clear();
        lastFailedProxyIds.clear();
        lastPostedStatus.clear();
        retryTokens.clear();
        retryingAccounts.clear();
        passwordPromptShown = false;
        clearChat();

        long startedGeneration = generation;
        boolean auto = profile.proxyMode == MultiProfile.ProxyMode.Auto;
        appendSystem(auto ? "Verifying proxies for " + profile.serverAddress : "Resolving " + profile.serverAddress);

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, AutismProxy> assignment = auto
                    ? verifyAndAssign(startedGeneration, profile, server.getHost(), server.getPort())
                    : Map.of();
                if (!isCurrent(startedGeneration)) return;
                InetSocketAddress resolved = ServerNameResolver.DEFAULT.resolveAddress(server)
                    .map(ResolvedServerAddress::asInetSocketAddress)
                    .orElse(null);
                Minecraft.getInstance().execute(() ->
                    buildAndStartSessions(startedGeneration, profile, assignment, resolved, server, auto));
            } catch (Throwable error) {
                String message = "Start failed: " + singleLine(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage(), 120);
                Minecraft.getInstance().execute(() -> buildFailedSessions(startedGeneration, profile, message));
            }
        }, workers);
        return StartResult.success();
    }

    private synchronized void buildAndStartSessions(long gen, MultiProfile profile, Map<String, AutismProxy> assignment,
                                                    InetSocketAddress resolved, ServerAddress server, boolean auto) {
        if (!active || generation != gen) return;
        boolean dnsOk = resolved != null;
        appendSystem(dnsOk ? "Starting " + profile.name + " on " + profile.serverAddress : "Unknown server address");
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            AutismProxy proxy = perModeProxy(profile, spec, assignment);
            boolean autoNoProxy = auto && proxy == null;
            AutismProxy snapshot = copyProxy(proxy);
            String proxyName = snapshot != null ? snapshot.displayName() : auto ? "No proxy" : "Proxy Off";
            MultiSession session = new MultiSession(
                generation, spec, snapshot, proxyName, profile.packetPolicy, profile.loginMode,
                profile.name, profile.openFormValues(spec.accountId()), this, workers, viaTarget);
            sessions.put(spec.accountId(), session);
            runtimeSpecs.put(spec.accountId(), runtimeSpecFor(profile, spec, proxy));
            runtimeProxies.put(spec.accountId(), snapshot);
            if (!dnsOk) {
                session.failExternal("Unknown server address");
            } else if (autoNoProxy) {
                session.failExternal("No working proxy");
            } else {
                pending.addLast(new Pending(session, resolved, server.getHost(), server.getPort()));
            }
            armJoinMacro(profile, spec, session);
        }
        republishSessions();
        if (dnsOk) pump();

        if (profile.loginMode == MultiProfile.LoginMode.Custom) {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            for (MultiProfile.SessionSpec spec : profile.sessions) {
                String name = spec.macroName().isBlank() ? profile.allMacroName : spec.macroName();
                if (name != null && !name.isBlank()) names.add(name);
            }
            for (String name : names) {
                AutismMacro macro = AutismMacroManager.get().get(name);
                if (macro != null) warnMacroCompatibility(macro);
            }
        }
    }

    private boolean deferRepublish;

    private void republishSessions() {
        if (deferRepublish) return;
        sessionList = List.copyOf(sessions.values());
        sessionsById = Map.copyOf(sessions);
        publishSnapshots();
        sessionRevision++;
    }

    private synchronized void buildFailedSessions(long gen, MultiProfile profile, String reason) {
        if (!active || generation != gen || !sessions.isEmpty()) return;
        appendSystem(reason);
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            MultiSession session = new MultiSession(gen, spec, null, "Proxy Off", profile.packetPolicy,
                profile.loginMode, profile.name, profile.openFormValues(spec.accountId()),
                this, workers, viaTarget);
            sessions.put(spec.accountId(), session);
            runtimeSpecs.put(spec.accountId(), spec);
            runtimeProxies.put(spec.accountId(), null);
            armJoinMacro(profile, spec, session);
            session.failExternal(reason);
        }
        republishSessions();
    }

    private static AutismProxy perModeProxy(MultiProfile profile, MultiProfile.SessionSpec spec, Map<String, AutismProxy> assignment) {
        return switch (profile.proxyMode) {
            case Off -> null;
            case Auto -> assignment.get(spec.accountId());
            case Manual -> resolveProxy(spec);
        };
    }

    private static MultiProfile.SessionSpec runtimeSpecFor(MultiProfile profile, MultiProfile.SessionSpec spec, AutismProxy proxy) {
        return switch (profile.proxyMode) {
            case Off -> new MultiProfile.SessionSpec(spec.accountId(), "");
            case Auto -> new MultiProfile.SessionSpec(spec.accountId(), proxy == null ? "" : proxy.stableId());
            case Manual -> spec;
        };
    }

    private StartResult validate(MultiProfile profile, java.util.UUID renderedProfileId) {
        if (profile.serverAddress.isBlank()) return StartResult.error("Server address is required");
        if (profile.sessions.isEmpty()) return StartResult.error("Select at least one account");
        if (profile.sessions.size() > MultiProfile.MAX_SESSIONS) {
            return StartResult.error("Maximum " + MultiProfile.MAX_SESSIONS + " sessions");
        }
        boolean manual = profile.proxyMode == MultiProfile.ProxyMode.Manual;
        Set<String> accountIds = new HashSet<>();
        Set<String> identities = new HashSet<>();
        for (MultiProfile.SessionSpec spec : profile.sessions) {
            if (!accountIds.add(spec.accountId())) return StartResult.error("Duplicate account row");
            String identity;
            if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(spec.accountId())) {
                java.util.UUID defaultProfileId = AutismAccountSessionSwitcher.getOriginalUser().getProfileId();
                if (renderedProfileId != null && renderedProfileId.equals(defaultProfileId)) {
                    return StartResult.error("Remove the current account before launching Multi");
                }
                identity = defaultProfileId.toString();
            } else {
                AutismAccount account = AutismAccountManager.get().findById(spec.accountId());
                if (account == null) return StartResult.error("Missing account: " + spec.accountId());
                java.util.UUID knownProfileId = parseKnownProfileId(account.uuid);
                if (renderedProfileId != null && renderedProfileId.equals(knownProfileId)) {
                    return StartResult.error("Remove the current account before launching Multi");
                }
                identity = account.uuid == null || account.uuid.isBlank()
                    ? account.type.name() + ":" + account.displayName().toLowerCase(Locale.ROOT)
                    : account.uuid.toLowerCase(Locale.ROOT);
            }
            if (!identities.add(identity)) return StartResult.error("The same Minecraft identity is selected twice");

            if (manual) {
                if (spec.bestProxy()) {
                    if (selectRetryProxy(AutismProxyManager.get().all(), "", "", false) == null) {
                        return StartResult.error("No proxy for " + accountLabel(spec.accountId()));
                    }
                } else if (!spec.direct() && AutismProxyManager.get().findById(spec.proxyId()) == null) {
                    return StartResult.error("Missing proxy for " + accountLabel(spec.accountId()));
                }
            }
        }
        if (profile.proxyMode == MultiProfile.ProxyMode.Auto && !hasUsableProxy()) {
            return StartResult.error("Add proxies or set Proxy: Off");
        }
        return StartResult.success();
    }

    private static java.util.UUID currentRenderedProfileId() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.getConnection() == null || minecraft.getUser() == null) {
            return null;
        }
        return minecraft.getUser().getProfileId();
    }

    static java.util.UUID parseKnownProfileId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return com.mojang.util.UndashedUuid.fromStringLenient(value.trim());
        } catch (RuntimeException ignored) {
            try {
                return java.util.UUID.fromString(value.trim());
            } catch (RuntimeException ignoredAgain) {
                return null;
            }
        }
    }

    public static boolean isCurrentRenderedAccount(String accountId) {
        java.util.UUID rendered = currentRenderedProfileId();
        if (rendered == null || accountId == null) return false;
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) {
            return rendered.equals(AutismAccountSessionSwitcher.getOriginalUser().getProfileId());
        }
        AutismAccount account = AutismAccountManager.get().findById(accountId);
        return account != null && rendered.equals(parseKnownProfileId(account.uuid));
    }

    private static boolean hasUsableProxy() {
        for (AutismProxy proxy : AutismProxyManager.get().all()) {
            if (proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD) return true;
        }
        return false;
    }

    private static AutismProxy resolveProxy(MultiProfile.SessionSpec spec) {
        if (spec == null || spec.direct()) return null;
        if (spec.bestProxy()) return selectRetryProxy(AutismProxyManager.get().all(), "", "", false);
        return AutismProxyManager.get().findById(spec.proxyId());
    }

    private synchronized void pump() {
        if (!active || activeProfile == null) return;
        int limit = activeProfile.concurrency();
        while (connecting.size() < limit && !pending.isEmpty()) {
            Pending next = pending.removeFirst();
            connecting.add(next.session());
            long now = System.currentTimeMillis();
            long at = Math.max(now, nextStartAt);
            nextStartAt = at + activeProfile.delayMs();
            scheduler.schedule(
                () -> {
                    synchronized (MultiManager.this) {
                        if (!active || next.session().generation() != generation
                            || sessions.get(next.session().accountId()) != next.session()) {
                            connecting.remove(next.session());
                            pump();
                            return;
                        }
                    }
                    next.session().start(next.address(), next.host(), next.port());
                },
                Math.max(0L, at - now),
                TimeUnit.MILLISECONDS
            );
        }
    }

    public synchronized RetryResult retry(String accountId) {
        if (!active || activeProfile == null || accountId == null) return RetryResult.error("No batch");
        MultiSession old = sessions.get(accountId);
        if (old != null && old.connected()) return RetryResult.error("Connected");
        if (old != null && !isRetryable(old.statusValue())) return RetryResult.error("Still connecting");
        if (!retryingAccounts.add(accountId)) return RetryResult.error("Already retrying");
        long retryToken = ++retrySerial;
        retryTokens.put(accountId, retryToken);
        MultiProfile.SessionSpec spec = runtimeSpecs.get(accountId);
        if (spec == null) {
            retryingAccounts.remove(accountId);
            return RetryResult.error("No session");
        }
        ServerAddress server = ServerAddress.parseString(activeProfile.serverAddress);

        if (activeProfile.proxyMode == MultiProfile.ProxyMode.Auto) {
            List<AutismProxy> candidates = orderedAutoRetryCandidates(accountId);
            if (candidates.isEmpty()) {
                retryingAccounts.remove(accountId);
                appendSystem(accountLabel(accountId) + ": No proxy");
                return RetryResult.error("No proxy");
            }
            long retryGeneration = generation;
            int target = activeProfile.autoMaxPingMs;
            CompletableFuture.runAsync(() -> {
                try {
                    retryAutoAsync(retryGeneration, retryToken, accountId, candidates, server, target);
                } catch (Throwable error) {
                    synchronized (MultiManager.this) {
                        if (!active || generation != retryGeneration
                            || retryTokens.getOrDefault(accountId, -1L) != retryToken) return;
                        retryingAccounts.remove(accountId);
                        appendSystem(accountLabel(accountId) + ": Retry failed");
                    }
                }
            }, workers);
            appendSystem(accountLabel(accountId) + ": Verifying proxy");
            return RetryResult.ok("Verifying proxy");
        }

        AutismProxy previous = runtimeProxies.get(accountId);
        String currentProxyId = previous == null ? spec.proxyId() : previous.stableId();
        boolean proxyEnabled = activeProfile.proxyMode == MultiProfile.ProxyMode.Manual && !spec.direct();
        if (!proxyEnabled) currentProxyId = "";
        String lastFailedProxyId = lastFailedProxyIds.getOrDefault(accountId, currentProxyId == null ? "" : currentProxyId);
        AutismProxy selectedProxy = proxyEnabled ? selectRetryProxy(AutismProxyManager.get().all(), currentProxyId, lastFailedProxyId, false) : null;
        if (selectedProxy == null && proxyEnabled) {
            retryingAccounts.remove(accountId);
            appendSystem(accountLabel(accountId) + ": No proxy");
            return RetryResult.error("No proxy");
        }

        MultiProfile.SessionSpec retrySpec = new MultiProfile.SessionSpec(
            accountId,
            proxyEnabled && selectedProxy != null ? selectedProxy.stableId() : ""
        );
        AutismProxy proxy = copyProxy(selectedProxy);
        runtimeSpecs.put(accountId, retrySpec);
        runtimeProxies.put(accountId, proxy);
        MultiSession replacement = new MultiSession(
            generation,
            retrySpec,
            proxy,
            proxy == null ? "Proxy Off" : proxy.displayName(),
            activeProfile.packetPolicy,
            activeProfile.loginMode,
            activeProfile.name,
            activeProfile.openFormValues(accountId),
            this,
            workers,
            viaTarget
        );
        pending.removeIf(next -> next.session() == old);
        connecting.remove(old);
        sessions.put(accountId, replacement);
        if (old != null) old.disconnect("Retrying");
        rearmMacroIfRunning(accountId, replacement);
        republishSessions();
        long retryGeneration = generation;
        CompletableFuture.supplyAsync(
            () -> ServerNameResolver.DEFAULT.resolveAddress(server)
                .map(ResolvedServerAddress::asInetSocketAddress)
                .orElse(null),
            workers
        ).whenComplete((resolved, error) -> {
            synchronized (MultiManager.this) {
                if (!active || generation != retryGeneration || sessions.get(accountId) != replacement
                    || retryTokens.getOrDefault(accountId, -1L) != retryToken) return;
                retryingAccounts.remove(accountId);
                if (error != null || resolved == null) {
                    replacement.failExternal("Unknown server address");
                    return;
                }
                pending.addLast(new Pending(replacement, resolved, server.getHost(), server.getPort()));
                pump();
            }
        });
        String label = proxy == null ? "Proxy Off" : proxy.displayName();
        String message = "Retry: " + singleLine(label, 32);
        appendSystem(accountLabel(accountId) + ": " + message);
        return RetryResult.ok(message);
    }

    private synchronized List<AutismProxy> orderedAutoRetryCandidates(String accountId) {
        String lastFailed = lastFailedProxyIds.getOrDefault(accountId, "");
        Set<String> inUseByOthers = new HashSet<>();
        for (Map.Entry<String, AutismProxy> entry : runtimeProxies.entrySet()) {
            if (!entry.getKey().equals(accountId) && entry.getValue() != null) inUseByOthers.add(entry.getValue().stableId());
        }
        List<AutismProxy> all = new ArrayList<>();
        for (AutismProxy proxy : AutismProxyManager.get().all()) {
            if (proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD) all.add(proxy);
        }
        all.sort(Comparator
            .comparingInt(MultiManager::retryRank)
            .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L ? proxy.latency : Long.MAX_VALUE));
        List<AutismProxy> primary = new ArrayList<>();
        List<AutismProxy> fallback = new ArrayList<>();
        for (AutismProxy proxy : all) {
            String id = proxy.stableId();
            if (id.equals(lastFailed) || inUseByOthers.contains(id)) fallback.add(proxy);
            else primary.add(proxy);
        }
        primary.addAll(fallback);
        return primary;
    }

    private void retryAutoAsync(long gen, long retryToken, String accountId, List<AutismProxy> candidates,
                                ServerAddress server, int target) {
        int timeout = probeTimeoutMs(target);
        Sample best = null;
        int tries = 0;
        for (AutismProxy candidate : candidates) {
            if (!isCurrentRetry(gen, retryToken, accountId)) return;
            if (tries++ >= 6) break;
            Sample sample = sampleOne(gen, candidate, server.getHost(), server.getPort(), timeout);
            if (sample == null) return;
            if (!sample.ok()) continue;
            if (sample.pingMs() <= target) {
                best = sample;
                break;
            }
            if (best == null || sample.pingMs() < best.pingMs()) best = sample;
        }
        if (!isCurrentRetry(gen, retryToken, accountId)) return;
        InetSocketAddress resolved = ServerNameResolver.DEFAULT.resolveAddress(server)
            .map(ResolvedServerAddress::asInetSocketAddress)
            .orElse(null);
        AutismProxy chosenFinal = best == null ? null : best.proxy();
        Minecraft.getInstance().execute(() -> applyAutoRetry(gen, retryToken, accountId, chosenFinal, resolved, server));
    }

    private synchronized void applyAutoRetry(long gen, long retryToken, String accountId, AutismProxy chosen,
                                             InetSocketAddress resolved, ServerAddress server) {
        if (!active || generation != gen || activeProfile == null
            || retryTokens.getOrDefault(accountId, -1L) != retryToken) return;
        retryingAccounts.remove(accountId);
        MultiSession old = sessions.get(accountId);
        if (old != null && old.connected()) return;
        if (chosen == null) {
            appendSystem(accountLabel(accountId) + ": No working proxy");
            if (old != null) old.failExternal("No working proxy");
            return;
        }
        if (resolved == null) {
            appendSystem("Unknown server address");
            if (old != null) old.failExternal("Unknown server address");
            return;
        }
        pending.removeIf(next -> next.session() == old);
        connecting.remove(old);
        AutismProxy proxy = copyProxy(chosen);
        MultiProfile.SessionSpec retrySpec = new MultiProfile.SessionSpec(accountId, chosen.stableId());
        runtimeSpecs.put(accountId, retrySpec);
        runtimeProxies.put(accountId, proxy);
        MultiSession replacement = new MultiSession(
            generation, retrySpec, proxy, proxy.displayName(), activeProfile.packetPolicy,
            activeProfile.loginMode, activeProfile.name, activeProfile.openFormValues(accountId), this, workers, viaTarget);
        sessions.put(accountId, replacement);
        if (old != null) old.disconnect("Retrying");
        rearmMacroIfRunning(accountId, replacement);
        republishSessions();
        pending.addLast(new Pending(replacement, resolved, server.getHost(), server.getPort()));
        pump();
        appendSystem(accountLabel(accountId) + ": Retry " + singleLine(proxy.displayName(), 32));
    }

    public synchronized RetryResult retryAllDisconnected() {
        if (!active || activeProfile == null) return RetryResult.error("No batch");
        int attempted = 0;
        deferRepublish = true;
        try {
            for (String accountId : new ArrayList<>(sessions.keySet())) {
                MultiSession session = sessions.get(accountId);
                if (session != null && isRetryable(session.statusValue()) && retry(accountId).ok()) attempted++;
            }
        } finally {
            deferRepublish = false;
        }
        republishSessions();
        return attempted == 0 ? RetryResult.error("Nothing to retry") : RetryResult.ok("Retrying " + attempted);
    }

    private Map<String, AutismProxy> verifyAndAssign(long gen, MultiProfile profile, String host, int port) {
        List<AutismProxy> candidates = new ArrayList<>();
        for (AutismProxy proxy : AutismProxyManager.get().all()) {
            if (proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD) candidates.add(proxy);
        }
        candidates.sort(Comparator
            .comparingInt(MultiManager::retryRank)
            .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L ? proxy.latency : Long.MAX_VALUE));
        Map<String, AutismProxy> assignment = new LinkedHashMap<>();
        if (candidates.isEmpty()) {
            post(gen, "No proxies to verify");
            return assignment;
        }
        int needed = profile.sessions.size();
        int target = profile.autoMaxPingMs;
        List<AutismProxy> poolList = new ArrayList<>(candidates.subList(0, Math.min(candidates.size(), PROXY_CANDIDATE_CAP)));
        int timeout = probeTimeoutMs(target);

        int wantUnderTarget = Math.min(poolList.size(), Math.max(needed + 3, 8));
        {

            ExecutorCompletionService<Sample> service = new ExecutorCompletionService<>(workers);
            List<Future<Sample>> probeTasks = new ArrayList<>(poolList.size());
            for (AutismProxy candidate : poolList) {
                probeTasks.add(service.submit(() -> sampleOne(gen, candidate, host, port, timeout)));
            }
            post(gen, "Testing " + poolList.size() + (poolList.size() == 1 ? " proxy" : " proxies") + " (target " + target + "ms)");
            long deadline = System.currentTimeMillis() + PROXY_SAMPLE_MAX_MS;
            List<Sample> working = new ArrayList<>();
            int underTarget = 0;
            for (int i = 0; i < poolList.size(); i++) {
                if (!isCurrent(gen)) break;
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) break;
                Future<Sample> done;
                try {
                    done = service.poll(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (done == null) break;
                Sample sample;
                try {
                    sample = done.get();
                } catch (Exception ignored) {
                    continue;
                }
                if (sample == null || !sample.ok()) continue;
                working.add(sample);
                if (sample.pingMs() <= target && ++underTarget >= wantUnderTarget) break;
            }
            if (working.isEmpty()) {
                cancelProbeTasks(probeTasks);
                post(gen, "No working proxy found");
                return assignment;
            }
            working.sort(Comparator.comparingLong(Sample::pingMs));
            int distinct = working.size();
            int index = 0;
            for (MultiProfile.SessionSpec spec : profile.sessions) {
                assignment.put(spec.accountId(), working.get(index % distinct).proxy());
                index++;
            }
            long best = working.getFirst().pingMs();
            post(gen, "Verified " + distinct + (distinct == 1 ? " proxy" : " proxies") + " (best " + best + "ms)");
            if (best > target) post(gen, "None under " + target + "ms; using best (" + best + "ms)");
            if (distinct < needed) post(gen, "Only " + distinct + " verified; some accounts share");
            cancelProbeTasks(probeTasks);
            return assignment;
        }
    }

    private static void cancelProbeTasks(List<? extends Future<?>> tasks) {
        if (tasks == null) return;
        for (Future<?> task : tasks) if (task != null && !task.isDone()) task.cancel(true);
    }

    private Sample sampleOne(long gen, AutismProxy proxy, String host, int port, int timeout) {
        long[] pings = new long[PROXY_SAMPLE_COUNT];
        for (int i = 0; i < PROXY_SAMPLE_COUNT; i++) {
            if (!isCurrent(gen)) return null;
            MultiProxyVerifier.Result result = MultiProxyVerifier.verify(proxy, host, port, timeout);
            if (!result.ok()) return new Sample(proxy, false, 0L);
            pings[i] = result.latencyMs();
        }
        Arrays.sort(pings);
        return new Sample(proxy, true, pings[PROXY_SAMPLE_COUNT / 2]);
    }

    private static int probeTimeoutMs(int targetPingMs) {
        return Math.max(700, Math.min(1500, targetPingMs * 3));
    }

    private synchronized boolean isCurrent(long gen) {
        return active && generation == gen;
    }

    private synchronized boolean isCurrentRetry(long gen, long retryToken, String accountId) {
        return active && generation == gen && retryTokens.getOrDefault(accountId, -1L) == retryToken;
    }

    private void post(long gen, String message) {
        synchronized (this) {
            if (active && generation == gen) appendSystem(message);
        }
    }

    private record Sample(AutismProxy proxy, boolean ok, long pingMs) {
    }

    record MacroFinish(String macroName, String reason) {
    }

    public synchronized void disconnectSession(String accountId) {
        MultiSession session = sessions.get(accountId);
        if (session == null) return;

        retryTokens.put(accountId, ++retrySerial);
        retryingAccounts.remove(accountId);
        macroRunning.remove(accountId);
        pending.removeIf(next -> next.session() == session);
        connecting.remove(session);
        session.disconnect("Disconnected by user");
        pump();
    }

    public static boolean isRetryable(MultiSession.Status status) {
        return status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED;
    }

    public synchronized void disconnectAll(String reason) {
        if (!active && sessions.isEmpty()) return;
        generation = generations.incrementAndGet();
        active = false;
        if (tickTask != null) {
            tickTask.cancel(false);
            tickTask = null;
        }
        pending.clear();
        connecting.clear();
        resolvedIdentities.clear();
        lastFailedProxyIds.clear();
        lastPostedStatus.clear();
        for (MultiSession session : sessions.values()) session.disconnect(reason == null ? "Multi stopped" : reason);
        sessions.clear();
        republishSessions();
        runtimeSpecs.clear();
        runtimeProxies.clear();
        macroRunning.clear();
        retryTokens.clear();
        retryingAccounts.clear();
        activeProfile = null;
        renderedProfileIdAtStart = null;
        clearChat();
    }

    private void clearChat() {
        accountChat.clear();
        unifiedChat.clear();
        recentByText.clear();
        chatSeq = 0;
        chatRevision++;
        commandHistory.clear();
        suggestSourceId = "";
        suggestId = -1;
        suggestText = "";
    }

    private static final int HISTORY_LIMIT = 50;

    public synchronized void pushHistory(String line) {
        String value = line == null ? "" : line.trim();
        if (value.isEmpty()) return;
        commandHistory.remove(value);
        commandHistory.addLast(value);
        while (commandHistory.size() > HISTORY_LIMIT) commandHistory.removeFirst();
    }

    public synchronized List<String> commandHistory() {
        return List.copyOf(commandHistory);
    }

    public record SuggestionResult(int start, int length, List<String> entries) {
    }

    public synchronized void requestSuggestions(String command, Set<String> scope) {
        if (command == null) return;
        MultiSession source = representativeReady(scope);
        if (source == null) {
            suggestSourceId = "";
            suggestId = -1;
            suggestText = "";
            return;
        }
        int id = source.requestSuggestions(command);
        if (id < 0) {
            suggestSourceId = "";
            suggestId = -1;
            suggestText = "";
            return;
        }
        suggestSourceId = source.accountId();
        suggestId = id;
        suggestText = command;
    }

    public synchronized SuggestionResult suggestions(String command) {
        if (command == null || !command.equals(suggestText)) return null;
        MultiSession source = sessions.get(suggestSourceId);
        if (source == null) return null;
        MultiSession.Suggest suggest = source.suggestion(suggestId);
        if (suggest == null) return null;
        return new SuggestionResult(suggest.start(), suggest.length(), suggest.entries());
    }

    private MultiSession representativeReady(Set<String> scope) {
        if (scope != null && !scope.isEmpty()) {
            for (String id : scope) {
                MultiSession session = sessions.get(id);
                if (session != null && session.ready()) return session;
            }
        }
        for (MultiSession session : sessions.values()) {
            if (session.ready()) return session;
        }
        return null;
    }

    public void shutdown() {
        disconnectAll("Game closed");
        workers.shutdownNow();
        scheduler.shutdownNow();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void rememberSelectedServer(net.minecraft.client.multiplayer.ServerData serverData) {
        if (serverData == null) {
            rememberedServerAddress = "";
            rememberedServerTarget = null;
            return;
        }
        rememberedServerAddress = serverData.ip == null ? "" : serverData.ip.trim();
        rememberedServerTarget = MultiViaCompat.captureServerTarget(serverData);
    }

    public int connectedCount() {
        int count = 0;
        for (MultiSession session : sessionList) {
            if (session.connected()) count++;
        }
        return count;
    }

    public int readyCount() {
        int count = 0;
        for (MultiSession session : sessionList) {
            if (session.ready()) count++;
        }
        return count;
    }

    public synchronized MultiProfile activeProfile() {
        return activeProfile == null ? null : new MultiProfile(activeProfile);
    }

    public synchronized void updatePolicy(MultiPacketPolicy policy) {
        if (!active || activeProfile == null || policy == null) return;
        activeProfile.packetPolicy = new MultiPacketPolicy(policy);
        for (MultiSession session : sessions.values()) session.applyPolicy(policy);
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void updateQuickAction(int index, MultiQuickAction action) {
        if (!active || activeProfile == null) return;
        activeProfile.setQuickAction(index, action);
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void clearQuickAction(int index) {
        updateQuickAction(index, new MultiQuickAction());
    }

    public synchronized void resetQuickActions() {
        if (!active || activeProfile == null) return;
        activeProfile.resetQuickActions();
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void assignAllMacro(String macroName) {
        if (activeProfile == null) return;
        String name = macroName == null ? "" : macroName.trim();
        if (name.equals(activeProfile.allMacroName)) return;
        activeProfile.allMacroName = name;
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
    }

    public synchronized void assignMacro(String accountId, String macroName) {
        if (accountId == null) return;
        assignMacroOnScope(java.util.Set.of(accountId), macroName);
    }

    public synchronized int assignMacroOnScope(Set<String> accountIds, String macroName) {
        if (activeProfile == null || accountIds == null || accountIds.isEmpty()) return 0;
        String name = macroName == null ? "" : macroName.trim();
        List<MultiProfile.SessionSpec> specs = activeProfile.sessions;
        int changed = 0;
        for (int i = 0; i < specs.size(); i++) {
            MultiProfile.SessionSpec spec = specs.get(i);
            if (!accountIds.contains(spec.accountId()) || spec.macroName().equals(name)) continue;
            specs.set(i, spec.withMacro(name));
            changed++;
        }
        if (changed > 0) {
            MultiProfileManager.get().put(activeProfile);
            uiRevision++;
        }
        return changed;
    }

    public synchronized String allMacroName() {
        return activeProfile == null ? "" : activeProfile.allMacroName;
    }

    public synchronized boolean hasAnyAssignedMacro() {
        if (activeProfile == null) return false;
        if (!activeProfile.allMacroName.isBlank()) return true;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (!spec.macroName().isBlank()) return true;
        }
        return false;
    }

    public synchronized String effectiveMacroName(String accountId) {
        if (activeProfile == null) return "";
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (spec.accountId().equals(accountId)) {
                return spec.macroName().isBlank() ? activeProfile.allMacroName : spec.macroName();
            }
        }
        return activeProfile.allMacroName;
    }

    public synchronized List<String> macroCompatibility(String macroName) {
        if (macroName == null || macroName.isBlank()) return List.of();
        AutismMacro macro = AutismMacroManager.get().get(macroName);
        if (macro == null) return List.of("Macro not found");
        return List.copyOf(MultiMacroSupport.analyze(macro));
    }

    public synchronized BroadcastResult runMacroOnScope(Set<String> scope) {
        return runMacroOnScope(scope, false);
    }

    public synchronized BroadcastResult runMacroOnScope(Set<String> scope, boolean idleOnly) {
        Map<String, AutismMacro> snapshots = new HashMap<>();
        Set<String> analyzed = new java.util.HashSet<>();
        BroadcastResult result = broadcastSessionAction("Run macro", scope, session -> {
            if (idleOnly && session.isMacroRunning()) return "Macro already running; not restarted";
            String name = effectiveMacroName(session.accountId());
            if (name == null || name.isBlank()) return "No macro assigned";
            AutismMacro copy = snapshots.computeIfAbsent(name, n -> {
                AutismMacro found = AutismMacroManager.get().get(n);
                return found == null ? null : found.deepCopy();
            });
            if (copy == null) return "Macro not found: " + name;
            if (analyzed.add(name)) warnMacroCompatibility(copy);
            macroRunning.add(session.accountId());
            MultiSession.Status status = session.statusValue();
            if (status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED) {
                return "Session is not connected; queued for retry";
            }
            session.startAssignedMacro(copy);
            return "Sent";
        });
        return result;
    }

    private void warnMacroCompatibility(AutismMacro macro) {
        List<String> warnings = MultiMacroSupport.analyze(macro);
        if (warnings.isEmpty()) return;
        String name = singleLine(macro.name, 40);
        appendSystem("Warning: macro \"" + name + "\" - " + warnings.size()
            + " item(s) may not run as intended:");
        for (String line : warnings) appendSystem(line);
    }

    public synchronized BroadcastResult runMacroDirect(AutismMacro macro) {
        if (macro == null || macro.actions.isEmpty()) {
            return new BroadcastResult(0, 0, 1, List.of("Macro has no actions"));
        }
        AutismMacro copy = macro.deepCopy();
        if (copy.name == null || copy.name.isBlank()) copy.name = "Editor macro";
        warnMacroCompatibility(copy);
        return broadcastSessionAction("Run for Multi", null, session -> {
            MultiSession.Status status = session.statusValue();
            if (status == MultiSession.Status.FAILED || status == MultiSession.Status.DISCONNECTED) {
                return "Session is not connected";
            }
            session.startAssignedMacro(copy);
            return "Sent";
        });
    }

    public synchronized BroadcastResult stopMacroOnScope(Set<String> scope) {
        return broadcastSessionAction("Stop macro", scope, session -> {
            macroRunning.remove(session.accountId());
            session.stopMacro();
            return "Sent";
        });
    }

    private void rearmMacroIfRunning(String accountId, MultiSession session) {
        if (!macroRunning.contains(accountId)) return;
        String name = effectiveMacroName(accountId);
        if (name == null || name.isBlank()) return;
        AutismMacro macro = AutismMacroManager.get().get(name);
        if (macro == null) return;

        if (activeProfile != null && activeProfile.loginMode == MultiProfile.LoginMode.Custom) {
            session.startLoginMacro(macro.deepCopy());
        } else {
            session.startAssignedMacro(macro.deepCopy());
        }
    }

    private void armJoinMacro(MultiProfile profile, MultiProfile.SessionSpec spec, MultiSession session) {
        if (profile == null || spec == null || session == null) return;
        if (profile.loginMode != MultiProfile.LoginMode.Custom) return;
        String name = spec.macroName().isBlank() ? profile.allMacroName : spec.macroName();
        if (name == null || name.isBlank()) return;
        AutismMacro macro = AutismMacroManager.get().get(name);
        if (macro == null) return;
        macroRunning.add(spec.accountId());
        session.startLoginMacro(macro.deepCopy());
    }

    public List<MultiSession.Snapshot> snapshots() {
        return snapshotList;
    }

    private void publishSnapshots() {
        List<MultiSession> live = sessionList;
        if (live.isEmpty()) {
            snapshotList = List.of();
            return;
        }
        List<MultiSession.Snapshot> snapshots = new ArrayList<>(live.size());
        for (MultiSession session : live) snapshots.add(session.snapshot());
        snapshotList = List.copyOf(snapshots);
    }

    public long sessionRevision() {
        return sessionRevision;
    }

    public long uiRevision() {
        return uiRevision;
    }

    public long chatRevision() {
        return chatRevision;
    }

    public synchronized List<ChatLine> chatView(Set<String> scope) {
        if (scope == null || scope.isEmpty()) {
            List<ChatLine> out = new ArrayList<>(unifiedChat.size());
            for (UnifiedMsg m : unifiedChat) {
                out.add(new ChatLine(m.seq, m.time, m.representative,
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(m.perAccount)), m.system, m.count, m.source));
            }
            return out;
        }
        if (scope.size() == 1) {
            String id = scope.iterator().next();
            ArrayDeque<ChatMsg> acc = accountChat.get(id);
            if (acc == null) return List.of();
            List<ChatLine> out = new ArrayList<>(acc.size());
            for (ChatMsg m : acc) out.add(new ChatLine(m.seq, m.time, m.component, Map.of(id, m.component), false, 1, m.source()));
            return out;
        }

        record Owned(String id, ChatMsg msg) {
        }
        List<Owned> gathered = new ArrayList<>();
        for (String id : scope) {
            ArrayDeque<ChatMsg> acc = accountChat.get(id);
            if (acc == null) continue;
            for (ChatMsg m : acc) gathered.add(new Owned(id, m));
        }
        gathered.sort(Comparator.comparingLong(o -> o.msg().seq()));
        Map<String, UnifiedMsg> recent = new HashMap<>();
        List<UnifiedMsg> ordered = new ArrayList<>();
        for (Owned o : gathered) {
            String text = o.msg().text();
            String src = o.msg().source();
            String key = groupKey(src, text);
            UnifiedMsg prev = recent.get(key);
            if (prev != null && !text.isBlank() && withinChatMergeWindow(prev.time, o.msg().time())) {
                prev.count++;
                prev.perAccount.putIfAbsent(o.id(), o.msg().component());
            } else {
                UnifiedMsg m = new UnifiedMsg(o.msg().seq(), o.msg().time(), text, src, false, o.msg().component());
                m.perAccount.put(o.id(), o.msg().component());
                ordered.add(m);
                if (!text.isBlank()) recent.put(key, m);
            }
        }
        int startIndex = Math.max(0, ordered.size() - CHAT_LIMIT);
        List<ChatLine> out = new ArrayList<>(ordered.size() - startIndex);
        for (int i = startIndex; i < ordered.size(); i++) {
            UnifiedMsg m = ordered.get(i);
            out.add(new ChatLine(m.seq, m.time, m.representative,
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(m.perAccount)), false, m.count, m.source));
        }
        return out;
    }

    public synchronized String sendCommandTo(String accountId, String command) {
        MultiSession session = sessions.get(accountId);
        if (session == null) return "No session";
        try {
            return session.sendConsoleLine(command);
        } catch (RuntimeException error) {
            return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
    }

    public synchronized BroadcastResult broadcastConsole(String line) {
        return broadcastConsole(line, null);
    }

    public synchronized BroadcastResult broadcastConsole(String line, Set<String> targets) {

        if (line != null && AutismCommands.isAutismCommandMessage(line.trim())) return runClientCommand(line, targets);
        if (sessions.isEmpty()) return sessionsStartingResult("Send");
        boolean scoped = targets != null && !targets.isEmpty();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !targets.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            String result;
            try {
                result = session.sendConsoleLine(line);
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if ("Sent".equals(result)) {
                sent++;
            } else if (result.contains("not ready") || result.contains("Blocked") || result.contains("key") || result.contains("Rate limited")) {
                skipped++;
                details.add(session.snapshot().accountName() + ": " + result);
            } else {
                failed++;
                details.add(session.snapshot().accountName() + ": " + result);
            }
        }
        BroadcastResult result = new BroadcastResult(sent, skipped, failed, List.copyOf(details));
        if (failed > 0) {
            appendSystem(result.summary());
            appendResultDetails(details);
        }
        return result;
    }

    public synchronized BroadcastResult runClientCommand(String line, Set<String> targets) {
        String body = AutismCommands.commandBody(line).trim();
        if (body.isEmpty()) {
            appendSystem("Empty command");
            return new BroadcastResult(0, 0, 0, List.of());
        }
        if (sessions.isEmpty()) return sessionsStartingResult("Command");
        int space = body.indexOf(' ');
        String name = (space < 0 ? body : body.substring(0, space)).toLowerCase(java.util.Locale.ROOT);
        String args = space < 0 ? "" : body.substring(space + 1).trim();

        String deny = MultiClientCommands.denyReason(name);
        if (deny != null) {

            appendSystem(name + ": " + deny);
            return new BroadcastResult(0, 0, 0, List.of());
        }

        boolean scoped = targets != null && !targets.isEmpty();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !targets.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            String result;
            try {
                result = session.runClientAction(name, args);
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if ("Sent".equals(result)) {
                sent++;
            } else if (result.contains("not ready") || result.contains("Blocked") || result.contains("Rate limited")) {
                skipped++;
                details.add(session.snapshot().accountName() + ": " + result);
            } else {
                failed++;
                details.add(session.snapshot().accountName() + ": " + result);
            }
        }
        BroadcastResult result = new BroadcastResult(sent, skipped, failed, List.copyOf(details));
        if (failed > 0) {
            appendSystem(name + " -> " + result.summary());
            appendResultDetails(details);
        }
        return result;
    }

    public synchronized BroadcastResult broadcastMovementNow() {
        return broadcastMovementNow(null);
    }

    public synchronized BroadcastResult broadcastMovementNow(Set<String> targets) {
        return broadcastSessionAction("Move", targets, MultiSession::sendImmediateMoveLook);
    }

    private record PreparedStep(Class<? extends Packet<?>> packetClass, String arguments) {
    }

    public synchronized BroadcastResult broadcastQuickAction(MultiQuickAction action) {
        return broadcastQuickAction(action, null);
    }

    public synchronized BroadcastResult broadcastQuickAction(MultiQuickAction action, Set<String> targets) {
        if (action == null || action.empty()) {
            int skipped = targets == null || targets.isEmpty() ? sessions.size()
                : (int) targets.stream().filter(sessions::containsKey).count();
            BroadcastResult result = new BroadcastResult(0, skipped, 0, List.of("Empty slot"));
            appendSystem("Empty slot");
            return result;
        }
        MultiQuickAction sendAction = new MultiQuickAction(action);
        List<PreparedStep> prepared = new ArrayList<>();
        for (MultiQuickAction.Step step : sendAction.steps) {
            Class<? extends Packet<?>> packetClass = resolvePacket(step.packetClass());
            if (packetClass == null) {
                BroadcastResult result = new BroadcastResult(0, 0, sessions.size(), List.of("Missing packet"));
                appendSystem("Missing packet");
                return result;
            }
            prepared.add(new PreparedStep(packetClass, step.arguments()));
        }

        return broadcastSessionAction(sendAction.label(0), targets, session -> {
            for (PreparedStep step : prepared) {
                String result = session.sendManual(step.packetClass(), step.arguments());
                if (!"Sent".equals(result)) return result;
            }
            return "Sent";
        });
    }

    public synchronized BroadcastResult broadcastManual(Class<? extends Packet<?>> packetClass, String arguments) {
        return broadcastSessionAction("Packet", session -> session.sendManual(packetClass, arguments));
    }

    private BroadcastResult broadcastSessionAction(String label, java.util.function.Function<MultiSession, String> sender) {
        return broadcastSessionAction(label, null, sender);
    }

    private BroadcastResult broadcastSessionAction(String label, Set<String> targets,
                                                   java.util.function.Function<MultiSession, String> sender) {
        if (sessions.isEmpty()) return sessionsStartingResult(label);
        boolean scoped = targets != null && !targets.isEmpty();
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        List<String> details = new ArrayList<>();
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            if (scoped && !targets.contains(entry.getKey())) continue;
            MultiSession session = entry.getValue();
            String result;
            try {
                result = sender.apply(session);
            } catch (RuntimeException error) {
                result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            }
            if ("Sent".equals(result)) {
                sent++;
            } else if (isSkippedSendResult(result)) {
                skipped++;
                details.add(session.snapshot().accountName() + ": " + result);
            } else {
                failed++;
                details.add(session.snapshot().accountName() + ": " + result);
            }
        }
        BroadcastResult result = new BroadcastResult(sent, skipped, failed, List.copyOf(details));

        if (failed > 0) {
            appendSystem(singleLine(label, 24) + ": " + result.summary());
            appendResultDetails(details);
        }
        return result;
    }

    private BroadcastResult sessionsStartingResult(String label) {
        int waiting = activeProfile == null ? 0 : activeProfile.sessions.size();
        BroadcastResult result = new BroadcastResult(0, waiting, 0, List.of("Sessions are still starting"));
        return result;
    }

    private void appendResultDetails(List<String> details) {
        if (details == null || details.isEmpty()) return;
        int shown = Math.min(5, details.size());
        for (int i = 0; i < shown; i++) appendSystem(details.get(i));
        if (details.size() > shown) appendSystem((details.size() - shown) + " more sessions omitted");
    }

    public synchronized BroadcastResult useOnScope(Set<String> scope) {
        return broadcastSessionAction("Use", scope, MultiSession::useItem);
    }

    public synchronized BroadcastResult closeOnScope(Set<String> scope) {
        return broadcastSessionAction("Close", scope, MultiSession::closeContainer);
    }

    public synchronized BroadcastResult closeSilentOnScope(Set<String> scope) {
        return broadcastSessionAction("Close (silent)", scope, MultiSession::closeSilent);
    }

    public MultiSession.MenuView menuView(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? null : session.menuView();
    }

    public MultiSession.MenuView inventoryView(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? null : session.inventoryView();
    }

    public long menuRevision(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.menuRevision();
    }

    public String clickBotSlot(String accountId, int handler, MultiClientCommands.ClickSpec spec) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null || spec == null) return "No session";
        return session.clickSlot(handler, spec.button(), spec.input());
    }

    public int hotbarIndexForHandler(String accountId, int handler) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.hotbarIndexOfHandler(handler);
    }

    public int selectedHotbarHandler(String accountId) {
        MultiSession session = sessionsById.get(accountId);
        return session == null ? -1 : session.selectedHotbarHandler();
    }

    public String selectBotHotbar(String accountId, int index) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        return session.runClientAction("change-slot", String.valueOf(Math.max(1, Math.min(9, index + 1))));
    }

    public String useBotHotbar(String accountId, int index) {
        MultiSession session = sessionsById.get(accountId);
        if (session == null) return "No session";
        String selected = session.runClientAction("change-slot", String.valueOf(Math.max(1, Math.min(9, index + 1))));
        if (!"Sent".equals(selected)) return selected;
        return session.runClientAction("use", "");
    }

    private static boolean isSkippedSendResult(String result) {
        if (result == null) return false;
        return result.contains("not ready")
            || result.contains("Blocked")
            || result.contains("headless-safe")
            || result.contains("Rate limited")
            || result.contains("already running")
            || result.contains("Position is not ready")
            || result.contains("Signing key");
    }

    @Override
    public synchronized String identityRejection(MultiSession session, java.util.UUID profileId) {
        if (session == null || profileId == null || session.generation() != generation
            || sessions.get(session.accountId()) != session) return "Stale Multi session";
        return identityRejection(renderedProfileIdAtStart, resolvedIdentities, session.accountId(), profileId);
    }

    static String identityRejection(java.util.UUID renderedProfileId, Map<java.util.UUID, String> resolved,
                                    String accountId, java.util.UUID profileId) {
        if (profileId == null || accountId == null || resolved == null) return "Stale Multi session";
        if (renderedProfileId != null && renderedProfileId.equals(profileId)) return "Account is already used by the rendered client";
        String existing = resolved.putIfAbsent(profileId, accountId);
        return existing == null || existing.equals(accountId)
            ? ""
            : "Duplicate Minecraft identity in this batch";
    }

    public synchronized void replaceMacroReference(String oldName, String newName) {
        if (activeProfile == null || oldName == null || oldName.isBlank()) return;
        String replacement = newName == null ? "" : newName.trim();
        List<String> affected = new ArrayList<>();
        for (String accountId : sessions.keySet()) {
            if (oldName.equals(effectiveMacroName(accountId))) affected.add(accountId);
        }
        if (activeProfile.replaceMacroReference(oldName, replacement)) {
            MultiProfileManager.get().put(activeProfile);
            uiRevision++;
        }
        if (replacement.isBlank()) {
            for (String accountId : affected) {
                macroRunning.remove(accountId);
                MultiSession session = sessions.get(accountId);
                if (session != null) session.stopMacro();
            }
        }
    }

    @Override
    public synchronized void stateChanged(MultiSession session) {
        if (session == null || session.generation() != generation
            || sessions.get(session.accountId()) != session) return;
        MultiSession.Status current = session.statusValue();
        String accountId = session.accountId();
        MultiSession.Status previous = lastPostedStatus.put(accountId, current);
        if (previous != current && (current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED)) {
            AutismProxy proxy = runtimeProxies.get(accountId);
            lastFailedProxyIds.put(accountId, proxy == null ? "" : proxy.stableId());

            String reason = singleLine(session.detailText(), 160);
            if (!reason.isBlank()) appendSystem(accountLabel(accountId) + ": " + reason);
        }
        if ((current == MultiSession.Status.READY || current == MultiSession.Status.FAILED || current == MultiSession.Status.DISCONNECTED)
            && connecting.remove(session)) {
            pump();
        }
        if (current == MultiSession.Status.READY || current == MultiSession.Status.FAILED
            || current == MultiSession.Status.DISCONNECTED) retryingAccounts.remove(accountId);
    }

    @Override
    public void chat(MultiSession session, Component component) {
        if (session == null || component == null || session.generation() != generation) return;

        String text = singleLine(component.getString(), 512);
        Component visible = sanitizeComponent(component, 512);
        String source = singleLine(session.currentMacroName(), 32);
        recordChat(session, text, visible, source);
    }

    private synchronized void recordChat(MultiSession session, String text, Component visible, String source) {
        if (session.generation() != generation || sessions.get(session.accountId()) != session) return;
        String accountId = session.accountId();
        long now = System.currentTimeMillis();
        long seq = ++chatSeq;

        ArrayDeque<ChatMsg> account = accountChat.computeIfAbsent(accountId, key -> new ArrayDeque<>());
        account.addLast(new ChatMsg(seq, now, visible, text, source));
        while (account.size() > CHAT_LIMIT) account.removeFirst();

        if (!text.isBlank()) {
            pruneRecent(now);
            String key = groupKey(source, text);
            UnifiedMsg existing = recentByText.get(key);
            if (existing != null && !existing.system && withinChatMergeWindow(existing.time, now)) {
                existing.count++;
                existing.perAccount.putIfAbsent(accountId, visible);
            } else {
                UnifiedMsg msg = new UnifiedMsg(seq, now, text, source, false, visible);
                msg.perAccount.put(accountId, visible);
                pushUnified(msg);
                recentByText.put(key, msg);
            }
        }
        chatRevision++;
    }

    @Override
    public void customMenuNeedsPassword(MultiSession session, String title) {
        boolean firstAlert;
        synchronized (this) {
            if (session == null || session.generation() != generation
                || sessions.get(session.accountId()) != session) return;
            firstAlert = !passwordPromptShown;
            passwordPromptShown = true;

            if (firstAlert) {
                appendSystem("Warning: " + accountLabel(session.accountId()) + " got a login screen (\""
                    + singleLine(title, 48) + "\") but no password is stored for this profile. Set one in the popup.");
            }
        }
        if (!firstAlert) return;
        Minecraft.getInstance().execute(() -> {
            autismclient.util.AutismNotifications.error("Multi: a login screen needs a password. Set one now.");
            Minecraft mc = Minecraft.getInstance();
            if (mc.gui.screen() instanceof autismclient.gui.screen.AutismMultiPasswordPromptScreen) return;
            mc.gui.setScreen(new autismclient.gui.screen.AutismMultiPasswordPromptScreen(mc.gui.screen()));
        });
    }

    public synchronized int applyPasswordToAllAccounts(String password) {
        if (activeProfile == null || password == null || password.isBlank()) return 0;
        int updated = 0;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (activeProfile.setFormValue(spec.accountId(), "password", password)) updated++;
        }
        persistAndPushFormValues();
        return updated;
    }

    public synchronized int applyGeneratedPasswords() {
        if (activeProfile == null) return 0;
        int updated = 0;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            if (activeProfile.setFormValue(spec.accountId(), "password", generatePassword())) updated++;
        }
        persistAndPushFormValues();
        return updated;
    }

    public static String generatePassword() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        int length = 9 + random.nextInt(8);
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return out.toString();
    }

    private void persistAndPushFormValues() {
        if (activeProfile == null) return;
        MultiProfileManager.get().put(activeProfile);
        uiRevision++;
        for (Map.Entry<String, MultiSession> entry : sessions.entrySet()) {
            entry.getValue().updateFormValues(activeProfile.openFormValues(entry.getKey()));
        }
    }

    public synchronized void updateActiveFormValues(MultiProfile source) {
        if (activeProfile == null || source == null) return;
        for (MultiProfile.SessionSpec spec : activeProfile.sessions) {
            String password = source.openFormValues(spec.accountId()).get("password");
            if (password != null) activeProfile.setFormValue(spec.accountId(), "password", password);
        }
        persistAndPushFormValues();
    }

    @Override
    public boolean macroStepMet(MultiSession requester, autismclient.util.macro.WaitForMacroStepAction action) {
        List<MultiSession.MacroProgress> published = new ArrayList<>();
        for (MultiSession session : sessionList) {
            if (session == requester) continue;
            published.add(session.snapshot().macroProgress());
        }
        return publishedMacroStepMet(action, published);
    }

    static boolean publishedMacroStepMet(
        autismclient.util.macro.WaitForMacroStepAction action,
        Iterable<MultiSession.MacroProgress> published
    ) {
        if (action == null) return true;
        String target = action.macroName == null ? "" : action.macroName.trim();
        if (target.isEmpty()) return true;
        int step = Math.max(1, action.step);
        boolean found = false;
        for (MultiSession.MacroProgress progress : published) {
            if (progress == null || !target.equalsIgnoreCase(progress.macroName())) continue;
            found = true;
            boolean satisfied = switch (action.mode == null
                ? autismclient.util.macro.WaitForMacroStepAction.WaitMode.COMPLETED_STEP : action.mode) {
                case STARTED_STEP -> progress.step() >= step || !progress.running() && progress.totalSteps() >= step;
                case COMPLETED_STEP -> progress.step() >= step || !progress.running() && progress.totalSteps() >= step;
                case FINISHED -> !progress.running();
            };
            if (satisfied) return true;
        }
        return !found && action.mode == autismclient.util.macro.WaitForMacroStepAction.WaitMode.FINISHED;
    }

    private void pushUnified(UnifiedMsg msg) {
        unifiedChat.addLast(msg);
        while (unifiedChat.size() > CHAT_LIMIT) {
            UnifiedMsg removed = unifiedChat.removeFirst();
            recentByText.remove(groupKey(removed.source, removed.text), removed);
        }
    }

    private void pruneRecent(long now) {
        if (recentByText.size() < 256) return;
        recentByText.values().removeIf(m -> !withinChatMergeWindow(m.time, now));
    }

    static boolean withinChatMergeWindow(long firstCopyAt, long currentCopyAt) {
        long elapsed = currentCopyAt - firstCopyAt;
        return elapsed >= 0L && elapsed <= CHAT_MERGE_MS;
    }

    private void tick() {
        List<MultiSession> snapshot;
        long tickGeneration;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (!active) return;
            snapshot = sessionList;
            tickGeneration = generation;
        }

        for (MultiSession session : snapshot) tickOne(session, now);
        synchronized (this) {

            if (!active || generation != tickGeneration) return;
            drainMacroFinishes(snapshot);
            if (now - lastSnapshotPublishAt >= SNAPSHOT_INTERVAL_MS) {
                publishSnapshots();
                lastSnapshotPublishAt = now;
            }
        }
    }

    private void drainMacroFinishes(List<MultiSession> snapshot) {
        java.util.LinkedHashMap<String, Integer> groups = null;
        for (MultiSession session : snapshot) {
            String note = session.pollMacroFinish();
            if (note == null) continue;
            MacroFinish finish = parseMacroFinish(note);
            String macroName = finish.macroName();
            String reason = finish.reason();
            if (!"chained".equals(reason)) macroRunning.remove(session.accountId());

            String key = macroFinishVerb(reason) + '\0' + macroName;
            if (groups == null) groups = new java.util.LinkedHashMap<>();
            groups.merge(key, 1, Integer::sum);
        }
        if (groups == null) return;
        for (var e : groups.entrySet()) {
            int sep = e.getKey().indexOf('\0');
            String verb = e.getKey().substring(0, sep);
            String macroName = e.getKey().substring(sep + 1);
            int count = e.getValue();
            appendSystem("Macro \"" + macroName + "\" " + verb + " on " + count + " bot" + (count == 1 ? "" : "s") + ".");
        }
    }

    static MacroFinish parseMacroFinish(String note) {
        String value = note == null ? "" : note;
        int separator = value.indexOf(0);
        return separator >= 0
            ? new MacroFinish(value.substring(0, separator), value.substring(separator + 1))
            : new MacroFinish(value, "done");
    }

    private static String macroFinishVerb(String reason) {
        return switch (reason) {
            case "chained" -> "handed off";
            case "error" -> "stopped on an error";
            case "stopped" -> "finished (stop action)";
            default -> "finished";
        };
    }

    private static void tickOne(MultiSession session, long now) {
        try {
            session.tick(now);
        } catch (RuntimeException error) {

            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            try {
                session.failExternal("Session tick failed: " + singleLine(message, 120));
            } catch (RuntimeException ignored) {

            }
        }
    }

    private synchronized void ensureTicking() {
        if (tickTask == null || tickTask.isCancelled() || tickTask.isDone()) {
            tickTask = scheduler.scheduleAtFixedRate(this::tick, 50L, 50L, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void appendSystem(String text) {
        String safe = singleLine(text, 512);

        Component render = AutismClientMessaging.themedTag("Multi").append(AutismClientMessaging.themedBody(safe));
        UnifiedMsg msg = new UnifiedMsg(++chatSeq, System.currentTimeMillis(), safe, "", true, render);
        pushUnified(msg);
        chatRevision++;
    }

    public static String singleLine(String text, int maxChars) {
        if (text == null || text.isBlank()) return "";
        StringBuilder out = new StringBuilder(Math.min(text.length(), Math.max(16, maxChars)));
        boolean spaced = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            boolean space = Character.isISOControl(ch) || Character.isWhitespace(ch);
            if (space) {
                if (!spaced && out.length() > 0) {
                    out.append(' ');
                    spaced = true;
                }
                continue;
            }
            out.append(ch);
            spaced = false;
            if (maxChars > 3 && out.length() >= maxChars) break;
        }
        String safe = out.toString().trim();
        if (maxChars > 3 && safe.length() > maxChars - 3) safe = safe.substring(0, maxChars - 3).trim() + "...";
        return safe;
    }

    static Component sanitizeComponent(Component component, int maxChars) {
        if (component == null || maxChars <= 0) return Component.empty();
        MutableComponent safe = Component.empty();
        int[] length = {0};
        boolean[] spaced = {true};
        component.visit((style, part) -> {
            if (part == null || part.isEmpty() || length[0] >= maxChars) return Optional.empty();
            StringBuilder run = new StringBuilder(Math.min(part.length(), maxChars - length[0]));
            for (int i = 0; i < part.length() && length[0] < maxChars; i++) {
                char ch = part.charAt(i);
                if (Character.isISOControl(ch) || Character.isWhitespace(ch)) {
                    if (!spaced[0] && length[0] > 0) {
                        run.append(' ');
                        length[0]++;
                        spaced[0] = true;
                    }
                } else {
                    run.append(ch);
                    length[0]++;
                    spaced[0] = false;
                }
            }
            if (!run.isEmpty()) safe.append(Component.literal(run.toString()).withStyle(style == null ? Style.EMPTY : style));
            return Optional.empty();
        }, Style.EMPTY);
        return safe;
    }

    private static String accountLabel(String accountId) {
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) return AutismAccountSessionSwitcher.getOriginalUser().getName();
        AutismAccount account = AutismAccountManager.get().findById(accountId);
        return account == null ? accountId : account.displayName();
    }

    private static AutismProxy copyProxy(AutismProxy source) {
        if (source == null) return null;
        AutismProxy copy = new AutismProxy();
        copy.id = source.stableId();
        copy.name = source.name;
        copy.type = source.type;
        copy.address = source.address;
        copy.port = source.port;
        copy.username = source.username;
        copy.password = source.password;
        return copy;
    }

    static AutismProxy selectRetryProxy(List<AutismProxy> proxies, String currentProxyId, String lastFailedProxyId, boolean currentDirect) {
        if (currentDirect) return null;
        if (proxies == null || proxies.isEmpty()) return null;
        String current = currentProxyId == null ? "" : currentProxyId;
        String failed = lastFailedProxyId == null ? "" : lastFailedProxyId;
        AutismProxy candidate = bestRetryProxy(proxies, current, failed);
        if (candidate != null) return candidate;
        candidate = bestRetryProxy(proxies, "", failed);
        if (candidate != null) return candidate;
        return bestRetryProxy(proxies, "", "");
    }

    private static AutismProxy bestRetryProxy(List<AutismProxy> proxies, String avoidCurrent, String avoidFailed) {
        if (proxies == null || proxies.isEmpty()) return null;
        String current = avoidCurrent == null ? "" : avoidCurrent;
        String failed = avoidFailed == null ? "" : avoidFailed;
        return proxies.stream()
            .filter(MultiManager::isRetryCandidate)
            .filter(proxy -> {
                String id = proxy.stableId();
                return (current.isBlank() || !current.equals(id)) && (failed.isBlank() || !failed.equals(id));
            })
            .min(Comparator
                .comparingInt(MultiManager::retryRank)
                .thenComparingLong(proxy -> proxy.status == AutismProxy.Status.ALIVE && proxy.latency > 0L ? proxy.latency : Long.MAX_VALUE)
                .thenComparing(proxy -> proxy.displayName().toLowerCase(Locale.ROOT)))
            .orElse(null);
    }

    private static boolean isRetryCandidate(AutismProxy proxy) {
        return proxy != null && proxy.isValid() && proxy.status != AutismProxy.Status.DEAD;
    }

    private static int retryRank(AutismProxy proxy) {
        if (proxy == null || proxy.status == null) return 3;
        return switch (proxy.status) {
            case ALIVE -> 0;
            case UNCHECKED -> 1;
            case CHECKING -> 2;
            case DEAD -> 3;
        };
    }

    public static Class<? extends Packet<?>> resolvePacket(String name) {
        return AutismPacketRegistry.getPacket(name);
    }
}
