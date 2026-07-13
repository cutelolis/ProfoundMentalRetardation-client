package autismclient.modules;

import autismclient.api.module.BoolSetting;
import autismclient.api.module.IntSetting;
import autismclient.api.module.StringSetting;
import autismclient.util.AntiVanishText;
import autismclient.util.AntiVanishHeuristics;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismNotifications;
import autismclient.util.AutismPlayerScanner;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class AntiVanishModule extends Module {
    private static final List<String> BUILTIN_RANKS = List.of(
        "owner", "co owner", "admin", "administrator", "head admin", "sr admin",
        "developer", "dev", "head developer", "manager", "maintainer", "coordinator",
        "moderator", "mod", "srmod", "head mod", "trial mod", "jrmod",
        "helper", "srhelper", "jrhelper", "builder", "designer", "support",
        "staff", "srstaff", "operator", "superop", "senior", "lead", "chief",
        "director", "supervisor", "game master", "gamemaster",
        "dueno", "dueño", "propietario", "creador", "fundador", "administrador",
        "desarrollador", "ayudante", "soporte", "lider", "líder", "jefe", "encargado"
    );
    private static final long SIGNAL_WINDOW_MS = 15_000L;
    private static final long DETECTION_TTL_MS = 20_000L;
    private static final long SELF_BREAK_TTL_MS = 6_000L;
    private static final long CRITICAL_COOLDOWN_MS = 12_000L;
    private static final long ANNOUNCE_COOLDOWN_MS = 1_500L;
    private static final long ANNOUNCE_WINDOW_MS = 4_000L;
    private static final int MAX_ANNOUNCE_PER_WINDOW = 6;
    private static final int MAX_OBSERVATIONS_PER_TICK = 512;
    private static final int CRITICAL_SCORE = 35;
    private static final long PLACE_SOUND_MATCH_MS = 700L;
    private static final double PLACE_SOUND_MATCH_SQ = 4.0;
    private static final long CONTAINER_SELF_GRACE_MS = 2_500L;

    private final ConcurrentLinkedQueue<Observation> observations = new ConcurrentLinkedQueue<>();
    private final Map<UUID, KnownPlayer> knownPlayers = new HashMap<>();
    private final Map<String, Detection> detections = new LinkedHashMap<>();
    private final Map<String, Long> signalCooldowns = new HashMap<>();
    private final Map<String, Long> announceCooldowns = new HashMap<>();
    private final Deque<Long> announceTimes = new ArrayDeque<>();

    private final Map<Long, Long> selfBrokenBlocks = new HashMap<>();
    private final Map<UUID, Long> confirmedDepartures = new HashMap<>();
    private final Map<Long, Long> automatedMechanisms = new HashMap<>();
    private final Map<String, Deque<Long>> weakParticleBursts = new HashMap<>();
    private final Map<Long, Deque<Long>> chunkResends = new HashMap<>();
    private final Deque<Signal> signals = new ArrayDeque<>();
    private final Deque<Long> cameraCorrections = new ArrayDeque<>();
    private final Deque<ExplosionEvent> recentExplosions = new ArrayDeque<>();

    private final Deque<PendingPlace> pendingPlaces = new ArrayDeque<>();
    private final Deque<PosTime> recentPlaceSounds = new ArrayDeque<>();
    private final Deque<PendingVanish> pendingVanishes = new ArrayDeque<>();
    private final Set<Long> seenChunks = new HashSet<>();

    private final Deque<RecentMessage> recentMessages = new ArrayDeque<>();
    private boolean serverSendsLeaveMessages = true;

    private final Set<Integer> completionRequestIds = new HashSet<>();
    private volatile List<String> pendingCompletionNames;
    private int nextCompletionId = 30000;
    private static final long RECENT_MESSAGE_TTL_MS = 8_000L;

    private static final long TRUSTED_LISTED_MS = 1_500L;
    private final Map<UUID, Long> listedSinceMs = new HashMap<>();

    private Object lastLevel;
    private volatile Vec3 lastPosition;
    private volatile float lastYaw;
    private volatile float lastPitch;
    private int stationaryTicks;
    private int tickCounter;
    private volatile int localPlayerId = Integer.MIN_VALUE;
    private boolean rankBaselineReady;
    private volatile long lastLocalActionMs;
    private long lastContainerActivityMs;
    private long lastServerCorrectionMs;
    private long lastCriticalMs;
    private long criticalUntilMs;
    private int currentScore;
    private String criticalSummary = "";
    private String lastTrigger = "";

    public AntiVanishModule() {
        super("anti-vanish", "Anti Vanish", ModuleCategory.PLAYER, "Detects vanished players.");

        add(new BoolSetting("vanish-tracker", "Vanish Tracker", true).group("Detection")
            .description("Detect TAB disappearances.").build());
        add(new BoolSetting("rank-detection", "Rank Detection", true).group("Detection")
            .description("Scan formatted ranks.").build());
        add(new BoolSetting("completion-probe", "Completion Probe", false).group("Detection")
            .description("Active tab probe.").build());
        add(new StringSetting("probe-command", "Probe Command", "minecraft:msg").group("Detection")
            .description("Command for probe.").build());

        add(new BoolSetting("sound-sensor", "Suspicious Sounds", true).group("Sensors")
            .description("Detect unexplained sounds.").build());
        add(new BoolSetting("particle-sensor", "Ghost Particles", true).group("Sensors")
            .description("Detect ghost particles.").build());
        add(new BoolSetting("block-sensor", "Block Updates", true).group("Sensors")
            .description("Detect unseen interactions.").build());
        add(new BoolSetting("invisible-sensor", "Invisible Entities", true).group("Sensors")
            .description("Detect invisible players.").build());
        add(new BoolSetting("camera-sensor", "Camera Aberrations", true).group("Sensors")
            .description("Detect forced camera resets.").build());
        add(new BoolSetting("chunk-sensor", "Chunk Re-sends", true).group("Sensors")
            .description("Detect nearby chunk resends.").build());
        add(new IntSetting("range", "Detection Range", 64, 8, 160, 8).group("Sensors")
            .description("How far to watch for sounds / block breaks / interactions (blocks).").build());

        add(new BoolSetting("critical-alert", "Critical Alert", true).group("Alerts")
            .description("Combine recent signals.").build());
        add(new BoolSetting("alert-sound", "Warning Sound", true).group("Alerts")
            .description("Play critical warning.").build());
        add(new BoolSetting("hud-list", "Vanish HUD", true).group("Alerts")
            .description("Show detections.").build());
        add(new BoolSetting("chat-alerts", "Chat Alerts", true).group("Alerts")
            .description("Log each detection to chat.").build());
    }

    @Override
    public String info() {
        return currentScore > 0 ? Integer.toString(currentScore) : "";
    }

    @Override
    public void onEnable() {
        resetRuntime();
        if (MC.player != null && MC.level != null) {
            lastLevel = MC.level;
            lastPosition = MC.player.position();
            lastYaw = MC.player.getYRot();
            lastPitch = MC.player.getXRot();
            localPlayerId = MC.player.getId();
            trackListedPlayers();
            scanRanks(false);
        }
    }

    @Override
    public void onDisable() {
        resetRuntime();
    }

    @Override
    public void onGameJoin() {
        resetRuntime();
    }

    @Override
    public void onGameLeft() {
        resetRuntime();
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || MC.getConnection() == null) {
            resetRuntime();
            return;
        }
        if (lastLevel != MC.level) {
            resetRuntime();
            lastLevel = MC.level;
        }

        tickCounter++;
        localPlayerId = MC.player.getId();

        if (MC.player.containerMenu != MC.player.inventoryMenu) lastContainerActivityMs = System.currentTimeMillis();
        updateStationaryState();
        drainObservations();
        processPendingPlaces();
        processPendingVanishes();
        processCompletionProbe();
        if (bool("completion-probe") && tickCounter % 100 == 0) sendCompletionProbe();
        if (tickCounter % 10 == 0) {
            trackListedPlayers();
            scanRanks(rankBaselineReady);
        }
        if (tickCounter % 5 == 0 && bool("invisible-sensor")) scanInvisiblePlayers();
        pruneState();
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet == null) return false;
        if (packet instanceof ServerboundPlayerActionPacket action) {
            lastLocalActionMs = System.currentTimeMillis();

            ServerboundPlayerActionPacket.Action a = action.getAction();
            if ((a == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                || a == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK
                || a == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) && action.getPos() != null) {
                selfBrokenBlocks.put(action.getPos().asLong(), System.currentTimeMillis() + SELF_BREAK_TTL_MS);
            }
            return false;
        }
        String name = packet.getClass().getSimpleName();
        if (name.equals("ServerboundUseItemOnPacket")
            || name.equals("ServerboundUseItemPacket")
            || name.equals("ServerboundInteractPacket")
            || name.equals("ServerboundSwingPacket")) {
            lastLocalActionMs = System.currentTimeMillis();
        }
        return false;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {

            if (remove.profileIds().size() < 4) {
                for (UUID id : remove.profileIds()) observations.offer(Observation.tabRemove(id));
            }
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info
            && info.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED)) {
            int unlisted = 0;
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                if (entry != null && !entry.listed()) unlisted++;
            }

            if (unlisted > 0 && unlisted < 4) {
                for (ClientboundPlayerInfoUpdatePacket.Entry entry : info.entries()) {
                    if (entry != null && !entry.listed()) observations.offer(Observation.tabHide(entry.profileId()));
                }
            }
        } else if (packet instanceof ClientboundSystemChatPacket chat) {
            String text = chat.content() == null ? "" : chat.content().getString();
            if (!text.isBlank()) observations.offer(Observation.systemChat(text));
            String departedName = departedPlayerName(chat.content());
            if (!departedName.isBlank()) observations.offer(Observation.playerLeft(departedName));
        } else if (packet instanceof ClientboundCommandSuggestionsPacket suggestions
            && completionRequestIds.remove(suggestions.id())) {

            List<String> names = new ArrayList<>();
            for (Suggestion suggestion : suggestions.toSuggestions().getList()) {
                String text = suggestion.getText();
                if (text != null && !text.isBlank()) names.add(text.trim());
            }
            pendingCompletionNames = names;
            return true;
        } else if (packet instanceof ClientboundSetEntityDataPacket metadata) {
            observations.offer(Observation.entityMetadata(metadata.id()));
        } else if (packet instanceof ClientboundPlayerPositionPacket position) {
            observations.offer(cameraObservation(position));
        } else if (packet instanceof ClientboundExplodePacket explosion) {
            observations.offer(Observation.explosion(explosion.center(), explosion.radius()));
        } else if (packet instanceof ClientboundLevelEventPacket levelEvent
            && levelEvent.getType() == LevelEvent.PARTICLES_DESTROY_BLOCK) {

            BlockState broken = Block.stateById(levelEvent.getData());
            Identifier id = broken == null ? null : BuiltInRegistries.BLOCK.getKey(broken.getBlock());
            observations.offer(Observation.block(ObservationType.BLOCK_BREAK, levelEvent.getPos(),
                id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundSoundEntityPacket sound) {
            observations.offer(Observation.entitySound(sound.getId(), soundId(sound.getSound().value().location())));
        } else if (packet instanceof ClientboundLevelParticlesPacket particles) {
            Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(particles.getParticle().getType());
            observations.offer(Observation.position(ObservationType.PARTICLE, particles.getX(), particles.getY(),
                particles.getZ(), id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundBlockEventPacket blockEvent) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(blockEvent.getBlock());
            observations.offer(Observation.block(ObservationType.BLOCK_EVENT, blockEvent.getPos(),
                id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(blockUpdate.getBlockState().getBlock());
            observations.offer(Observation.block(ObservationType.BLOCK_UPDATE, blockUpdate.getPos(),
                id == null ? "" : id.toString()));
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            int[] accepted = {0};
            sectionUpdate.runUpdates((pos, state) -> {
                if (accepted[0] >= 64) return;
                Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                String blockId = id == null ? "" : id.toString();
                if (!AntiVanishHeuristics.potentialInteractiveBlock(blockId)) return;
                accepted[0]++;
                observations.offer(Observation.block(ObservationType.BLOCK_UPDATE, pos, blockId));
            });
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunk) {
            observations.offer(Observation.chunk(chunk.getX(), chunk.getZ()));
        }
        return false;
    }

    @Override
    public void onSoundPacket(ClientboundSoundPacket packet) {
        if (packet == null || packet.getSound() == null || packet.getSound().value() == null) return;
        observations.offer(Observation.position(ObservationType.POSITIONAL_SOUND, packet.getX(), packet.getY(),
            packet.getZ(), soundId(packet.getSound().value().location())));
    }

    public static boolean shouldShowHud() {
        AntiVanishModule module = instance();
        return module != null && module.isEnabled() && module.bool("hud-list") && module.hasHudContent();
    }

    private boolean hasHudContent() {
        long now = System.currentTimeMillis();
        if (now < criticalUntilMs) return true;
        for (Detection detection : detections.values()) {
            if (detection.expiresAt > now && detectionWorthShowing(detection)) return true;
        }
        return false;
    }

    private static boolean detectionWorthShowing(Detection detection) {
        if (detection == null) return false;
        String name = detection.name == null ? "" : detection.name.trim();
        if (!name.isBlank() && !"Unknown".equalsIgnoreCase(name) && !"You".equalsIgnoreCase(name)
            && !"CRITICAL".equalsIgnoreCase(name)) {
            return true;
        }
        String reason = detection.reason == null ? "" : detection.reason.toLowerCase(Locale.ROOT);
        return reason.contains("rank detection:");
    }

    private final Map<String, String> tagByName = new HashMap<>();
    private long lastTagScanMs = 0L;

    private void refreshTags() {
        long now = System.currentTimeMillis();
        if (now - lastTagScanMs < 750L) return;
        lastTagScanMs = now;
        try {
            Map<String, String> next = new HashMap<>();
            for (AutismPlayerScanner.ScannedPlayer p : AutismPlayerScanner.scan(MC)) {
                if (p.hasPrefix()) next.put(p.name().toLowerCase(Locale.ROOT), p.prefix());
            }
            tagByName.clear();
            tagByName.putAll(next);
        } catch (Throwable ignored) {  }
    }

    public static String hudTag(HudEntry entry) {
        if (entry == null) return "WATCH";
        if ("CRITICAL".equalsIgnoreCase(entry.name())) return "ALERT";
        String reason = entry.reason() == null ? "" : entry.reason();
        String lower = reason.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("rank detection:");
        if (idx >= 0) {
            AntiVanishModule module = instance();
            String name = entry.name() == null ? "" : entry.name().trim();
            if (module != null && !name.isBlank()) {
                String glyph = module.tagByName.get(name.toLowerCase(Locale.ROOT));
                if (glyph != null && !glyph.isBlank()) return glyph.length() <= 12 ? glyph : glyph.substring(0, 12);
            }
            String word = reason.substring(idx + "rank detection:".length()).trim();
            if (!word.isBlank()) {
                String up = word.toUpperCase(Locale.ROOT);
                return up.length() <= 12 ? up : up.substring(0, 12);
            }
            return "RANK";
        }
        if (lower.startsWith("vanish event")) return "VANISH";
        if (lower.startsWith("invisible entity")) return "INVIS";
        if (lower.startsWith("suspicious sound")) return "SOUND";
        if (lower.startsWith("camera aberration")) return "CAMERA";
        if (lower.startsWith("ghost particle")) return "PARTICLE";
        if (lower.startsWith("block")) return "BLOCK";
        if (lower.startsWith("chunk")) return "CHUNK";
        return "WATCH";
    }

    public static String hudValue(HudEntry entry) {
        if (entry == null) return "Staff";
        if ("CRITICAL".equalsIgnoreCase(entry.name())) {
            AntiVanishModule module = instance();
            String summary = module == null ? "" : module.criticalSummary;
            if (summary == null || summary.isBlank()) return "watching";
            return summary;
        }
        return compactHudName(entry);
    }

    private static String shortSignal(SignalType type) {
        return switch (type) {
            case VANISH -> "Vanish";
            case CAMERA -> "Camera";
            case INVISIBLE -> "Invisible";
            case PARTICLE -> "Particles";
            case SOUND -> "Sounds";
            case BLOCK -> "Blocks";
            case CHUNK -> "Chunks";
        };
    }

    public static boolean criticalActive() {
        AntiVanishModule module = instance();
        return module != null && module.isEnabled() && System.currentTimeMillis() < module.criticalUntilMs;
    }

    public static String criticalSummary() {
        AntiVanishModule module = instance();
        return module == null ? "" : module.criticalSummary;
    }

    public static List<HudEntry> hudEntries() {
        AntiVanishModule module = instance();
        return module == null ? List.of() : module.hudSnapshot();
    }

    public static String compactHudName(HudEntry entry) {
        if (entry == null) return "Staff";
        String name = entry.name() == null ? "" : entry.name().trim();
        if (name.isBlank() || "CRITICAL".equalsIgnoreCase(name) || "Unknown".equalsIgnoreCase(name)
            || "You".equalsIgnoreCase(name)) return "Staff";
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    public static String compactHudReason(HudEntry entry) {
        if (entry == null) return "";
        String reason = entry.reason() == null ? "" : entry.reason().trim();
        String lower = reason.toLowerCase(Locale.ROOT);
        if ("CRITICAL".equalsIgnoreCase(entry.name())) return "WATCH";
        if (lower.startsWith("vanish event")) return "Vanish";
        if (lower.startsWith("rank detection")) return "Rank";
        if (lower.startsWith("invisible entity")) return "Invis";
        if (lower.startsWith("entity packet spike")) return "Packets";
        if (lower.startsWith("camera aberration")) return "Camera";
        if (lower.startsWith("ghost particle")) return "Particle";
        if (lower.startsWith("suspicious sound")) return "Sound";
        if (lower.startsWith("block")) return "Block";
        if (lower.startsWith("chunk")) return "Chunk";
        return reason.length() <= 10 ? reason : reason.substring(0, 10);
    }

    private static AntiVanishModule instance() {
        Module module = ModuleRegistry.get("anti-vanish");
        return module instanceof AntiVanishModule antiVanish ? antiVanish : null;
    }

    public static String censusSummary() {
        AntiVanishModule module = instance();
        if (module == null || !module.isEnabled()) return "off";
        return "obs=" + module.observations.size()
            + " known=" + module.knownPlayers.size()
            + " det=" + module.detections.size()
            + " chunks=" + module.seenChunks.size()
            + " pendingVanish=" + module.pendingVanishes.size()
            + " msgs=" + module.recentMessages.size();
    }

    private void updateStationaryState() {
        Vec3 position = MC.player.position();
        if (lastPosition != null) {
            double dx = position.x - lastPosition.x;
            double dz = position.z - lastPosition.z;
            if (dx * dx + dz * dz < 0.0004) stationaryTicks++;
            else stationaryTicks = 0;
        }
        lastPosition = position;
        lastYaw = MC.player.getYRot();
        lastPitch = MC.player.getXRot();
    }

    private Observation cameraObservation(ClientboundPlayerPositionPacket packet) {
        Vec3 base = lastPosition;
        Vec3 target = packet.change().position();
        Set<Relative> relatives = packet.relatives();
        double displacement = Double.POSITIVE_INFINITY;
        if (base != null && target != null) {
            double x = relatives.contains(Relative.X) ? base.x + target.x : target.x;
            double y = relatives.contains(Relative.Y) ? base.y + target.y : target.y;
            double z = relatives.contains(Relative.Z) ? base.z + target.z : target.z;
            displacement = base.distanceTo(new Vec3(x, y, z));
        }
        float targetYaw = relatives.contains(Relative.Y_ROT) ? lastYaw + packet.change().yRot() : packet.change().yRot();
        float targetPitch = relatives.contains(Relative.X_ROT) ? lastPitch + packet.change().xRot() : packet.change().xRot();
        double rotation = Math.hypot(wrapDegrees(targetYaw - lastYaw), targetPitch - lastPitch);
        return Observation.cameraCorrection(displacement, rotation);
    }

    private void drainObservations() {
        for (int i = 0; i < MAX_OBSERVATIONS_PER_TICK; i++) {
            Observation observation = observations.poll();
            if (observation == null) break;
            processObservation(observation);
        }
        while (observations.size() > 4096) observations.poll();
    }

    private void processObservation(Observation observation) {
        switch (observation.type) {
            case TAB_REMOVE -> handleTabRemoval(observation.profileId);
            case TAB_HIDE -> handleTabHidden(observation.profileId);
            case PLAYER_LEFT -> { serverSendsLeaveMessages = true; confirmDeparture(observation.detail); }
            case SYSTEM_CHAT -> cacheRecentMessage(observation.detail);
            case ENTITY_METADATA -> inspectInvisibleEntity(observation.entityId);
            case POSITIONAL_SOUND -> { rememberPlaceSound(observation); inspectPositionalSound(observation); }
            case ENTITY_SOUND -> inspectEntitySound(observation);
            case PARTICLE -> inspectParticle(observation);
            case BLOCK_EVENT, BLOCK_UPDATE, BLOCK_BREAK -> inspectBlockUpdate(observation);
            case CHUNK_DATA -> inspectChunk(observation.chunkX, observation.chunkZ);
            case EXPLOSION -> rememberExplosion(observation);
            case CAMERA_CORRECTION -> inspectCameraCorrection(observation);
        }
    }

    private void trackListedPlayers() {
        if (MC.getConnection() == null) return;
        long now = System.currentTimeMillis();
        for (PlayerInfo info : MC.getConnection().getListedOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().id() == null) continue;
            UUID uuid = info.getProfile().id();
            if (MC.player != null && MC.player.getUUID().equals(uuid)) continue;
            listedSinceMs.putIfAbsent(uuid, now);
            String name = info.getProfile().name();
            if (name != null && !knownPlayers.containsKey(uuid)) {
                knownPlayers.put(uuid, new KnownPlayer(uuid, name, "", "", false));
            }
        }

        if (listedSinceMs.size() > 1024 || knownPlayers.size() > 1024) {
            Set<UUID> connected = new HashSet<>();
            for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
                if (info != null && info.getProfile() != null) connected.add(info.getProfile().id());
            }
            listedSinceMs.keySet().retainAll(connected);
            knownPlayers.entrySet().removeIf(entry -> !connected.contains(entry.getKey()) && !entry.getValue().staff);
        }
    }

    private boolean trustedListed(UUID uuid) {
        Long since = uuid == null ? null : listedSinceMs.get(uuid);
        return since != null && System.currentTimeMillis() - since >= TRUSTED_LISTED_MS;
    }

    private boolean credibleSubject(UUID uuid, String name) {
        return uuid != null
            && AutismPlayerScanner.isUsername(name)
            && trustedListed(uuid)
            && !AutismAntiBot.isConfirmedBot(uuid);
    }

    private void scanRanks(boolean notifyNew) {
        if (!bool("rank-detection") || MC.getConnection() == null) {
            rankBaselineReady = true;
            return;
        }
        long now = System.currentTimeMillis();
        Set<UUID> online = new HashSet<>();
        for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
            if (info == null || info.getProfile() == null || info.getProfile().name() == null) continue;
            UUID uuid = info.getProfile().id();
            String name = info.getProfile().name();
            if (uuid == null || MC.player.getUUID().equals(uuid)) continue;
            online.add(uuid);
            RankMatch rank = rankMatch(info, name);
            KnownPlayer previous = knownPlayers.get(uuid);
            KnownPlayer known = new KnownPlayer(uuid, name, rank.keyword, rank.source, rank.matched);
            knownPlayers.put(uuid, known);
            if (!rank.matched) continue;

            String reason = "Rank Detection: " + rank.keyword;
            upsertDetection(uuid.toString(), name, reason, 10, now + 4_000L);
            if (notifyNew && (previous == null || !previous.staff || !previous.rank.equalsIgnoreCase(rank.keyword))) {
                announceRank(name, rank.keyword);
            }
        }
        knownPlayers.entrySet().removeIf(entry -> !online.contains(entry.getKey()) && !entry.getValue().staff);
        rankBaselineReady = true;
    }

    private RankMatch rankMatch(PlayerInfo info, String name) {

        Component display = info.getTabListDisplayName();
        if (display == null) return RankMatch.NONE;
        String source = display.getString();
        String builtin = AntiVanishText.firstMatch(source, name, BUILTIN_RANKS, "Word");
        return builtin.isBlank() ? RankMatch.NONE : new RankMatch(true, builtin, source);
    }

    private void handleTabRemoval(UUID uuid) {
        if (!bool("vanish-tracker") || uuid == null || MC.player.getUUID().equals(uuid)) return;

        String name = knownName(uuid);
        if (!credibleSubject(uuid, name) || !AntiVanishText.isPlausiblePlayerName(name)) return;
        pendingVanishes.removeIf(pending -> pending.uuid.equals(uuid));
        pendingVanishes.addLast(new PendingVanish(uuid, name, tickCounter + 20));
    }

    private void handleTabHidden(UUID uuid) {
        if (!bool("vanish-tracker") || uuid == null || MC.player.getUUID().equals(uuid)) return;

        KnownPlayer known = knownPlayers.get(uuid);
        String name = known != null && known.name != null ? known.name : knownName(uuid);
        if (!credibleSubject(uuid, name) || !AntiVanishText.isPlausiblePlayerName(name)) return;

        if (recentMessageNames(name)) return;
        long now = System.currentTimeMillis();
        boolean staff = known != null && known.staff;
        String reason = staff ? "Vanish Event: staff hidden from TAB" : "Vanish Event: hidden from TAB";
        upsertDetection(uuid.toString(), name, reason, 100, now + DETECTION_TTL_MS);
        addSignal(SignalType.VANISH, name, reason, 100, 15_000L, true);
    }

    private String knownName(UUID uuid) {
        KnownPlayer known = knownPlayers.get(uuid);
        if (known != null && known.name != null && !known.name.isBlank()) return known.name;
        if (MC.getConnection() != null) {
            PlayerInfo info = MC.getConnection().getPlayerInfo(uuid);
            if (info != null && info.getProfile() != null) return info.getProfile().name();
        }
        return null;
    }

    private void confirmDeparture(String displayedName) {
        long now = System.currentTimeMillis();
        for (KnownPlayer known : knownPlayers.values()) {
            if (!AntiVanishText.containsPlayerName(displayedName, known.name)) continue;
            confirmedDepartures.put(known.uuid, now + 5_000L);
            pendingVanishes.removeIf(pending -> pending.uuid.equals(known.uuid));
            Detection detection = detections.get(known.uuid.toString());
            if (detection != null && detection.reason.startsWith("Vanish Event: no leave packet")) {
                detections.remove(known.uuid.toString());
            }
            signals.removeIf(signal -> signal.type == SignalType.VANISH
                && signal.subject.equalsIgnoreCase(known.name)
                && signal.reason.startsWith("Vanish Event: no leave packet"));
        }
    }

    private void processPendingVanishes() {
        while (!pendingVanishes.isEmpty() && pendingVanishes.peekFirst().dueTick <= tickCounter) {
            PendingVanish pending = pendingVanishes.removeFirst();
            if (MC.getConnection().getPlayerInfo(pending.uuid) != null) continue;
            long now = System.currentTimeMillis();
            if (confirmedDepartures.getOrDefault(pending.uuid, 0L) > now) continue;
            if (recentMessageNames(pending.name)) {
                serverSendsLeaveMessages = true;
                continue;
            }
            if (AutismAntiBot.isConfirmedBot(pending.uuid)) continue;
            Player remaining = MC.level.getPlayerByUUID(pending.uuid);
            if (remaining != null && !remaining.isRemoved()) {

                String reason = "Vanish Event: entity remained";
                upsertDetection(pending.uuid.toString(), pending.name, reason, 100, now + DETECTION_TTL_MS);
                addSignal(SignalType.VANISH, pending.name, reason, 100, 10_000L, true);
            } else if (serverSendsLeaveMessages) {

                String reason = "Vanish Event: no leave packet";
                upsertDetection(pending.uuid.toString(), pending.name, reason, 100, now + DETECTION_TTL_MS);
                addSignal(SignalType.VANISH, pending.name, reason, 100, 10_000L, true);
            }
        }
    }

    private void cacheRecentMessage(String text) {
        if (text == null || text.isBlank()) return;
        long now = System.currentTimeMillis();
        recentMessages.addLast(new RecentMessage(text, now));
        while (!recentMessages.isEmpty()
            && (now - recentMessages.peekFirst().atMs > RECENT_MESSAGE_TTL_MS || recentMessages.size() > 64)) {
            recentMessages.removeFirst();
        }
    }

    private boolean recentMessageNames(String name) {
        if (name == null || name.isBlank()) return false;
        long now = System.currentTimeMillis();
        for (RecentMessage message : recentMessages) {
            if (now - message.atMs > RECENT_MESSAGE_TTL_MS) continue;
            if (AntiVanishText.containsPlayerName(message.text, name)) return true;
        }
        return false;
    }

    private void sendCompletionProbe() {
        if (MC.getConnection() == null) return;
        String command = text("probe-command");
        if (command == null || command.isBlank()) command = "minecraft:msg";
        int id = nextCompletionId++;
        if (nextCompletionId > 40000) nextCompletionId = 30000;
        completionRequestIds.add(id);
        while (completionRequestIds.size() > 8) completionRequestIds.remove(completionRequestIds.iterator().next());
        try {
            MC.getConnection().send(new ServerboundCommandSuggestionPacket(id, command.trim() + " "));
        } catch (Throwable ignored) {

        }
    }

    private void processCompletionProbe() {
        List<String> current = pendingCompletionNames;
        if (current == null) return;
        pendingCompletionNames = null;
        if (!bool("completion-probe") || MC.getConnection() == null || MC.player == null) return;
        Set<String> tabNames = new HashSet<>();
        for (PlayerInfo info : MC.getConnection().getOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                tabNames.add(info.getProfile().name().toLowerCase(Locale.ROOT));
            }
        }
        String self = MC.player.getName().getString();
        List<String> hidden = new ArrayList<>();
        for (String name : current) {
            if (!AntiVanishText.isPlausiblePlayerName(name)) continue;
            if (name.equalsIgnoreCase(self)) continue;
            if (tabNames.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (recentMessageNames(name)) continue;
            hidden.add(name);
        }

        if (hidden.isEmpty() || hidden.size() > 3) return;
        long now = System.currentTimeMillis();
        for (String name : hidden) {
            String reason = "Vanish Event: hidden but targetable";
            upsertDetection(name, name, reason, 90, now + DETECTION_TTL_MS);
            addSignal(SignalType.VANISH, name, reason, 90, 10_000L, true);
        }
    }

    private static String departedPlayerName(Component component) {
        if (component == null || !(component.getContents() instanceof TranslatableContents translated)
            || !"multiplayer.player.left".equals(translated.getKey())) return "";
        Object[] args = translated.getArgs();
        if (args.length == 0 || args[0] == null) return "";
        return args[0] instanceof Component name ? name.getString() : String.valueOf(args[0]);
    }

    private void scanInvisiblePlayers() {
        double rangeSq = sensorRangeSq();
        for (Player player : MC.level.players()) {
            if (player == null || player == MC.player || !player.isInvisible()) continue;
            if (player.distanceToSqr(MC.player) > rangeSq) continue;
            if (!realPlayer(player)) continue;
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            triggerSensor(SignalType.INVISIBLE, name, "Invisible Entity: metadata flag", 35, 5_000L);
        }
    }

    private void inspectInvisibleEntity(int entityId) {
        if (!bool("invisible-sensor")) return;
        Entity entity = MC.level.getEntity(entityId);
        if (!(entity instanceof Player player) || player == MC.player || !player.isInvisible()) return;
        if (player.distanceToSqr(MC.player) > sensorRangeSq()) return;
        if (!realPlayer(player)) return;
        String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
        triggerSensor(SignalType.INVISIBLE, name, "Invisible Entity: metadata flag", 35, 5_000L);
    }

    private boolean realPlayer(Player player) {
        String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
        return credibleSubject(player.getUUID(), name);
    }

    private void inspectPositionalSound(Observation observation) {
        if (!bool("sound-sensor") || !AntiVanishHeuristics.suspiciousSound(observation.detail)) return;
        Vec3 source = observation.position();
        long now = System.currentTimeMillis();
        if (!nearPlayer(source) || hasVisibleCause(source) || isExplosionRelated(source)
            || isPoweredMechanism(source, observation.detail) || now - lastLocalActionMs < 1_000L
            || (selfContainerActive() && isContainerSignal(observation.detail))) return;
        triggerSensor(SignalType.SOUND, locatedSubject(source), "Suspicious Sound: " + shortId(observation.detail), 14, 3_000L);
    }

    private void inspectEntitySound(Observation observation) {
        if (!bool("sound-sensor") || !AntiVanishHeuristics.suspiciousSound(observation.detail)) return;
        Entity entity = MC.level.getEntity(observation.entityId);
        if (entity instanceof Player player && player != MC.player && player.isInvisible()
            && player.distanceToSqr(MC.player) <= sensorRangeSq() && realPlayer(player)) {
            String name = player.getGameProfile() == null ? player.getName().getString() : player.getGameProfile().name();
            triggerSensor(SignalType.SOUND, name, "Suspicious Sound: invisible source", 16, 3_000L);
        }
    }

    private void inspectParticle(Observation observation) {
        if (!bool("particle-sensor") || !AntiVanishHeuristics.suspiciousParticle(observation.detail)) return;
        Vec3 source = observation.position();
        long now = System.currentTimeMillis();
        if (!nearPlayer(source) || hasVisibleCause(source) || isExplosionRelated(source)
            || now - lastLocalActionMs < 900L || hasAmbientParticleSource(source, observation.detail)) return;
        String particle = shortId(observation.detail);

        if ((particle.equals("block") || particle.contains("smoke")) && nearSelf(source, 6.25)) return;
        if ((particle.equals("block") || particle.contains("smoke")) && !particleBurstReady(particle, now)) return;
        triggerSensor(SignalType.PARTICLE, locatedSubject(source), "Ghost Particle: " + shortId(observation.detail), 16, 3_000L);
    }

    private void inspectBlockUpdate(Observation observation) {
        if (!bool("block-sensor")) return;
        Vec3 source = observation.position();
        long now = System.currentTimeMillis();
        if (!nearPlayer(source) || hasVisibleCause(source) || isExplosionRelated(source)
            || isPoweredMechanism(source, observation.detail) || now - lastLocalActionMs < 1_200L
            || recentlySelfBroke(source)) return;
        String label = classifyBlockChange(observation.type, observation.detail);
        if (label == null) return;
        if (label.equals("Block Interaction") && villagerToggledDoor(observation.detail, source)) return;
        if (label.equals("Block Interaction") && selfContainerActive() && isContainerSignal(observation.detail)) return;
        if (label.equals("Block Place")) {

            pendingPlaces.addLast(new PendingPlace(source, observation.detail, now));
            while (pendingPlaces.size() > 64) pendingPlaces.removeFirst();
            return;
        }

        String dedupKey = label + "@" + BlockPos.containing(source).asLong();
        triggerSensor(SignalType.BLOCK, locatedSubject(source), dedupKey, label + ": " + shortId(observation.detail), 13, 800L);
    }

    private static String classifyBlockChange(ObservationType type, String id) {
        String path = AntiVanishHeuristics.path(id);
        if (type == ObservationType.BLOCK_BREAK) {
            return id.isBlank() || AntiVanishHeuristics.isAir(id) || AntiVanishHeuristics.naturalBlockNoise(id)
                ? null : "Block Break";
        }
        if (type == ObservationType.BLOCK_EVENT) {
            return AntiVanishHeuristics.blockEventInteraction(path) ? "Block Interaction" : null;
        }
        if (AntiVanishHeuristics.isAir(id)) return null;
        if (AntiVanishHeuristics.blockStateInteraction(path)) return "Block Interaction";
        if (AntiVanishHeuristics.naturalBlockNoise(id)) return null;
        return "Block Place";
    }

    private boolean villagerToggledDoor(String blockId, Vec3 source) {
        String path = AntiVanishHeuristics.path(blockId);
        boolean doorLike = (path.contains("door") && !path.contains("trapdoor")) || path.contains("fence_gate");
        if (!doorLike || source == null || MC.level == null) return false;
        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity instanceof Villager && entity.position().distanceToSqr(source) <= 9.0) return true;
        }
        return false;
    }

    private void rememberPlaceSound(Observation observation) {
        if (!bool("block-sensor") || !observation.detail.contains(".place")) return;
        long now = System.currentTimeMillis();
        recentPlaceSounds.addLast(new PosTime(observation.position(), now));
        trimPlaceSounds(now);
    }

    private void trimPlaceSounds(long now) {
        while (!recentPlaceSounds.isEmpty() && now - recentPlaceSounds.peekFirst().timeMs > PLACE_SOUND_MATCH_MS + 300L) {
            recentPlaceSounds.removeFirst();
        }
        while (recentPlaceSounds.size() > 64) recentPlaceSounds.removeFirst();
    }

    private void processPendingPlaces() {
        if (pendingPlaces.isEmpty()) return;
        long now = System.currentTimeMillis();
        trimPlaceSounds(now);
        int pending = pendingPlaces.size();
        for (int i = 0; i < pending; i++) {
            PendingPlace place = pendingPlaces.pollFirst();
            if (place == null) break;
            if (now - place.timeMs > PLACE_SOUND_MATCH_MS) continue;
            if (!placeSoundNear(place.pos)) { pendingPlaces.addLast(place); continue; }
            String dedupKey = "Block Place@" + BlockPos.containing(place.pos).asLong();
            triggerSensor(SignalType.BLOCK, locatedSubject(place.pos), dedupKey,
                "Block Place: " + shortId(place.detail), 13, 800L);
        }
    }

    private boolean placeSoundNear(Vec3 pos) {
        for (PosTime sound : recentPlaceSounds) {
            if (sound.pos.distanceToSqr(pos) <= PLACE_SOUND_MATCH_SQ) return true;
        }
        return false;
    }

    private void inspectChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        boolean resend = !seenChunks.add(key);
        if (!bool("chunk-sensor") || !resend || tickCounter < 100 || stationaryTicks < 40
            || System.currentTimeMillis() - lastServerCorrectionMs < 5_000L) return;
        int playerChunkX = ((int) Math.floor(MC.player.getX())) >> 4;
        int playerChunkZ = ((int) Math.floor(MC.player.getZ())) >> 4;
        if (Math.abs(chunkX - playerChunkX) > 2 || Math.abs(chunkZ - playerChunkZ) > 2) return;
        long now = System.currentTimeMillis();
        Deque<Long> repeats = chunkResends.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        repeats.addLast(now);
        while (!repeats.isEmpty() && now - repeats.peekFirst() > 10_000L) repeats.removeFirst();
        if (repeats.size() < 2) return;
        repeats.clear();
        triggerSensor(SignalType.CHUNK, "near you", "Chunk Re-send: " + chunkX + ", " + chunkZ, 15, 15_000L);
    }

    private boolean recentlySelfBroke(Vec3 source) {
        if (source == null) return false;
        Long until = selfBrokenBlocks.get(BlockPos.containing(source).asLong());
        return until != null && until > System.currentTimeMillis();
    }

    private void inspectCameraCorrection(Observation observation) {
        long now = System.currentTimeMillis();
        lastServerCorrectionMs = now;
        if (!bool("camera-sensor") || stationaryTicks < 15 || tickCounter <= 60) return;
        double displacement = observation.x;
        double rotation = observation.y;
        boolean smallPositionReset = displacement >= 0.02 && displacement <= 1.5;
        boolean cameraJerk = rotation >= 2.0 && rotation <= 45.0;
        if (!smallPositionReset && !cameraJerk) return;
        cameraCorrections.addLast(now);
        while (!cameraCorrections.isEmpty() && now - cameraCorrections.peekFirst() > 4_000L) cameraCorrections.removeFirst();
        if (cameraCorrections.size() < 2) return;
        cameraCorrections.clear();
        String detail = cameraJerk
            ? String.format(Locale.ROOT, "Camera Aberration: %.1f° reset", rotation)
            : String.format(Locale.ROOT, "Camera Aberration: %.2fm reset", displacement);
        triggerSensor(SignalType.CAMERA, "on you", detail, 20, 8_000L);
    }

    private void rememberExplosion(Observation observation) {
        long now = System.currentTimeMillis();
        recentExplosions.addLast(new ExplosionEvent(observation.position(),
            Math.max(2.0, parseDouble(observation.detail, 4.0) + 4.0), now));
        while (recentExplosions.size() > 8) recentExplosions.removeFirst();
    }

    private boolean particleBurstReady(String particle, long now) {
        Deque<Long> burst = weakParticleBursts.computeIfAbsent(particle, ignored -> new ArrayDeque<>());
        burst.addLast(now);
        while (!burst.isEmpty() && now - burst.peekFirst() > 2_000L) burst.removeFirst();
        if (burst.size() < 2) return false;
        burst.clear();
        return true;
    }

    private void triggerSensor(SignalType type, String subject, String reason, int weight, long cooldownMs) {
        addSignal(type, subject, subject, reason, weight, cooldownMs, false);
    }

    private void triggerSensor(SignalType type, String subject, String dedupKey, String reason, int weight, long cooldownMs) {
        addSignal(type, subject, dedupKey, reason, weight, cooldownMs, false);
    }

    private void addSignal(SignalType type, String subject, String reason, int weight, long cooldownMs, boolean instant) {
        addSignal(type, subject, subject, reason, weight, cooldownMs, instant);
    }

    private void addSignal(SignalType type, String subject, String dedupKey, String reason, int weight, long cooldownMs, boolean instant) {
        long now = System.currentTimeMillis();
        String cooldownKey = type.name() + "|" + dedupKey.toLowerCase(Locale.ROOT);
        long last = signalCooldowns.getOrDefault(cooldownKey, 0L);
        if (now - last < cooldownMs) return;
        signalCooldowns.put(cooldownKey, now);
        signals.addLast(new Signal(type, subject, reason, weight, now));
        upsertDetection("signal:" + cooldownKey, subject, reason, weight, now + DETECTION_TTL_MS);
        lastTrigger = reason;
        announceTrigger(cooldownKey, subject, reason);
        evaluateCritical(instant);
    }

    private void evaluateCritical(boolean instant) {
        long now = System.currentTimeMillis();
        pruneSignals(now);
        EnumMap<SignalType, Integer> strongest = new EnumMap<>(SignalType.class);
        for (Signal signal : signals) strongest.merge(signal.type, signal.weight, Math::max);
        currentScore = strongest.values().stream().mapToInt(Integer::intValue).sum();
        if (!bool("critical-alert")) return;
        if (!instant && (strongest.size() < 2 || currentScore < CRITICAL_SCORE)) return;
        if (now - lastCriticalMs < CRITICAL_COOLDOWN_MS) return;

        lastCriticalMs = now;
        criticalUntilMs = now + 7_000L;

        criticalSummary = strongest.keySet().stream().map(AntiVanishModule::shortSignal).sorted().reduce((a, b) -> a + " + " + b).orElse(lastTrigger);

        SignalType top = strongest.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        AutismNotifications.error(top == null ? "Staff may be watching you" : "Staff watching you: " + shortSignal(top));
        if (bool("alert-sound") && MC.getSoundManager() != null) {
            MC.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0F));
        }
    }

    private void announceRank(String name, String keyword) {
        if (!bool("chat-alerts")) return;
        AutismClientMessaging.sendPrefixed("§bRank Detection: §f" + name + " §7(" + keyword + ")");
    }

    private void announceTrigger(String eventKey, String subject, String reason) {
        if (!bool("chat-alerts")) return;
        long now = System.currentTimeMillis();
        Long last = announceCooldowns.get(eventKey);
        if (last != null && now - last < ANNOUNCE_COOLDOWN_MS) return;
        while (!announceTimes.isEmpty() && now - announceTimes.peekFirst() > ANNOUNCE_WINDOW_MS) announceTimes.removeFirst();
        if (announceTimes.size() >= MAX_ANNOUNCE_PER_WINDOW) return;
        announceCooldowns.put(eventKey, now);
        announceTimes.addLast(now);
        boolean located = subject != null && !subject.isBlank()
            && !"Unknown".equalsIgnoreCase(subject) && !"CRITICAL".equalsIgnoreCase(subject);
        String where = located ? " §7(" + subject + ")" : "";
        AutismClientMessaging.sendPrefixed("§b" + reason + where);
    }

    private void upsertDetection(String key, String name, String reason, int score, long expiresAt) {
        Detection existing = detections.get(key);
        if (existing == null) {
            detections.put(key, new Detection(name, reason, score, expiresAt));
            return;
        }
        existing.name = name;
        existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
        if (score >= existing.score) {
            existing.reason = reason;
            existing.score = score;
        }
    }

    private List<HudEntry> hudSnapshot() {
        refreshTags();
        long now = System.currentTimeMillis();
        List<HudEntry> out = new ArrayList<>();
        if (now < criticalUntilMs) out.add(new HudEntry("CRITICAL", criticalSummary, 100));

        Set<String> seenRows = new HashSet<>();
        detections.values().stream()
            .filter(detection -> detection.expiresAt > now)
            .filter(AntiVanishModule::detectionWorthShowing)
            .sorted(Comparator.comparingInt((Detection detection) -> detection.score).reversed()
                .thenComparing(detection -> detection.name, String.CASE_INSENSITIVE_ORDER))
            .map(detection -> new HudEntry(detection.name, detection.reason, detection.score))
            .filter(entry -> seenRows.add(hudTag(entry) + ' ' + hudValue(entry)))
            .limit(4)
            .forEach(out::add);
        return List.copyOf(out);
    }

    private void pruneState() {
        long now = System.currentTimeMillis();
        detections.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        signalCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        announceCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L);
        selfBrokenBlocks.entrySet().removeIf(entry -> entry.getValue() <= now);
        confirmedDepartures.entrySet().removeIf(entry -> entry.getValue() <= now);
        automatedMechanisms.entrySet().removeIf(entry -> entry.getValue() <= now);
        weakParticleBursts.values().removeIf(burst -> {
            while (!burst.isEmpty() && now - burst.peekFirst() > 2_000L) burst.removeFirst();
            return burst.isEmpty();
        });
        chunkResends.values().removeIf(repeats -> {
            while (!repeats.isEmpty() && now - repeats.peekFirst() > 10_000L) repeats.removeFirst();
            return repeats.isEmpty();
        });
        while (!cameraCorrections.isEmpty() && now - cameraCorrections.peekFirst() > 4_000L) cameraCorrections.removeFirst();
        while (!recentExplosions.isEmpty() && now - recentExplosions.peekFirst().timeMs > 3_000L) recentExplosions.removeFirst();
        pruneSignals(now);
        EnumMap<SignalType, Integer> strongest = new EnumMap<>(SignalType.class);
        for (Signal signal : signals) strongest.merge(signal.type, signal.weight, Math::max);
        currentScore = strongest.values().stream().mapToInt(Integer::intValue).sum();
    }

    private void pruneSignals(long now) {
        while (!signals.isEmpty() && now - signals.peekFirst().timeMs > SIGNAL_WINDOW_MS) signals.removeFirst();
    }

    private boolean nearPlayer(Vec3 source) {
        return source != null && source.distanceToSqr(MC.player.position()) <= sensorRangeSq();
    }

    private boolean nearSelf(Vec3 source, double maxDistSq) {
        return source != null && MC.player != null && source.distanceToSqr(MC.player.position()) < maxDistSq;
    }

    private boolean selfContainerActive() {
        return System.currentTimeMillis() - lastContainerActivityMs < CONTAINER_SELF_GRACE_MS;
    }

    private static boolean isContainerSignal(String id) {
        String path = AntiVanishHeuristics.path(id);
        return path.contains("chest") || path.contains("barrel") || path.contains("shulker");
    }

    private String locatedSubject(Vec3 source) {
        if (source == null || MC.player == null) return "nearby";
        Vec3 me = MC.player.position();
        double dx = source.x - me.x, dy = source.y - me.y, dz = source.z - me.z;
        long dist = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
        String dir = compass(dx, dz);
        return dir.isEmpty() ? dist + "m" : dist + "m " + dir;
    }

    private static String compass(double dx, double dz) {
        String ns = dz < -1.0 ? "N" : dz > 1.0 ? "S" : "";
        String ew = dx > 1.0 ? "E" : dx < -1.0 ? "W" : "";
        return ns + ew;
    }

    private boolean hasVisibleCause(Vec3 source) {
        if (source == null) return true;

        for (Entity entity : MC.level.entitiesForRendering()) {
            if (entity == MC.player) continue;

            if (entity instanceof Player player) {
                if (player.isInvisible()) continue;
                if (entity.position().distanceToSqr(source) <= 16.0) return true;
            } else if (entity instanceof Projectile && entity.position().distanceToSqr(source) <= 16.0) {
                return true;
            }
        }
        return false;
    }

    private boolean isExplosionRelated(Vec3 source) {
        if (source == null) return false;
        long now = System.currentTimeMillis();
        while (!recentExplosions.isEmpty() && now - recentExplosions.peekFirst().timeMs > 3_000L) recentExplosions.removeFirst();
        for (ExplosionEvent explosion : recentExplosions) {
            if (explosion.center.distanceToSqr(source) <= explosion.radius * explosion.radius) return true;
        }
        return false;
    }

    private boolean hasAmbientParticleSource(Vec3 source, String particleId) {
        if (source == null || !shortId(particleId).contains("smoke")) return false;
        BlockPos center = BlockPos.containing(source);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    Identifier id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock());
                    String path = shortId(id == null ? "" : id.toString());
                    if (path.contains("campfire") || path.contains("furnace") || path.contains("smoker")
                        || path.contains("torch") || path.contains("fire") || path.contains("candle")
                        || path.contains("respawn_anchor")) return true;
                }
            }
        }
        return false;
    }

    private boolean isPoweredMechanism(Vec3 source, String blockOrSoundId) {
        String path = shortId(blockOrSoundId);
        if (!path.contains("door") && !path.contains("trapdoor")) return false;
        BlockPos center = BlockPos.containing(source);
        long now = System.currentTimeMillis();
        for (int dy = -1; dy <= 1; dy++) {
            BlockPos pos = center.offset(0, dy, 0);
            BlockState state = MC.level.getBlockState(pos);
            long key = pos.asLong();
            boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
            if (powered || MC.level.hasNeighborSignal(pos)) {
                automatedMechanisms.put(key, now + 5_000L);
                return true;
            }
            if (automatedMechanisms.getOrDefault(key, 0L) > now) return true;
        }
        return false;
    }

    private double sensorRangeSq() {
        double r = Math.max(8, integer("range"));
        return r * r;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static String soundId(Identifier id) {
        return id == null ? "" : id.toString();
    }

    private static String shortId(String id) {
        return AntiVanishHeuristics.path(id);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private void resetRuntime() {
        observations.clear();
        knownPlayers.clear();
        detections.clear();
        signalCooldowns.clear();
        announceCooldowns.clear();
        announceTimes.clear();
        selfBrokenBlocks.clear();
        confirmedDepartures.clear();
        automatedMechanisms.clear();
        weakParticleBursts.clear();
        chunkResends.clear();
        signals.clear();
        cameraCorrections.clear();
        recentExplosions.clear();
        pendingPlaces.clear();
        recentPlaceSounds.clear();
        pendingVanishes.clear();
        seenChunks.clear();
        lastLevel = null;
        lastPosition = null;
        stationaryTicks = 0;
        tickCounter = 0;
        localPlayerId = Integer.MIN_VALUE;
        rankBaselineReady = false;
        lastLocalActionMs = 0L;
        lastContainerActivityMs = 0L;
        lastServerCorrectionMs = 0L;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
        lastCriticalMs = 0L;
        criticalUntilMs = 0L;
        currentScore = 0;
        criticalSummary = "";
        lastTrigger = "";
        recentMessages.clear();
        listedSinceMs.clear();
        serverSendsLeaveMessages = true;
        completionRequestIds.clear();
        pendingCompletionNames = null;
        nextCompletionId = 30000;
    }

    public record HudEntry(String name, String reason, int score) {
    }

    private enum SignalType {
        VANISH("Vanish Event"),
        CAMERA("Camera Aberration"),
        INVISIBLE("Invisible Entity"),
        PARTICLE("Ghost Particles"),
        SOUND("Suspicious Sounds"),
        BLOCK("Block Updates"),
        CHUNK("Chunk Re-sends");

        final String label;

        SignalType(String label) {
            this.label = label;
        }
    }

    private enum ObservationType {
        TAB_REMOVE,
        TAB_HIDE,
        PLAYER_LEFT,
        SYSTEM_CHAT,
        ENTITY_METADATA,
        POSITIONAL_SOUND,
        ENTITY_SOUND,
        PARTICLE,
        BLOCK_EVENT,
        BLOCK_UPDATE,
        BLOCK_BREAK,
        CHUNK_DATA,
        EXPLOSION,
        CAMERA_CORRECTION
    }

    private record Observation(
        ObservationType type,
        UUID profileId,
        int entityId,
        double x,
        double y,
        double z,
        String detail,
        int chunkX,
        int chunkZ
    ) {
        static Observation tabRemove(UUID id) {
            return new Observation(ObservationType.TAB_REMOVE, id, -1, 0, 0, 0, "", 0, 0);
        }

        static Observation tabHide(UUID id) {
            return new Observation(ObservationType.TAB_HIDE, id, -1, 0, 0, 0, "", 0, 0);
        }

        static Observation playerLeft(String name) {
            return new Observation(ObservationType.PLAYER_LEFT, null, -1, 0, 0, 0, name, 0, 0);
        }

        static Observation systemChat(String text) {
            return new Observation(ObservationType.SYSTEM_CHAT, null, -1, 0, 0, 0, text, 0, 0);
        }

        static Observation entityMetadata(int id) {
            return new Observation(ObservationType.ENTITY_METADATA, null, id, 0, 0, 0, "", 0, 0);
        }

        static Observation cameraCorrection(double displacement, double rotation) {
            return new Observation(ObservationType.CAMERA_CORRECTION, null, -1,
                displacement, rotation, 0, "", 0, 0);
        }

        static Observation explosion(Vec3 center, float radius) {
            return new Observation(ObservationType.EXPLOSION, null, -1,
                center.x, center.y, center.z, Float.toString(radius), 0, 0);
        }

        static Observation entitySound(int id, String sound) {
            return new Observation(ObservationType.ENTITY_SOUND, null, id, 0, 0, 0, sound, 0, 0);
        }

        static Observation position(ObservationType type, double x, double y, double z, String detail) {
            return new Observation(type, null, -1, x, y, z, detail, 0, 0);
        }

        static Observation block(ObservationType type, BlockPos pos, String detail) {
            return position(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, detail);
        }

        static Observation chunk(int x, int z) {
            return new Observation(ObservationType.CHUNK_DATA, null, -1, 0, 0, 0, "", x, z);
        }

        Vec3 position() {
            return new Vec3(x, y, z);
        }
    }

    private record KnownPlayer(UUID uuid, String name, String rank, String rankSource, boolean staff) {
    }

    private record RankMatch(boolean matched, String keyword, String source) {
        static final RankMatch NONE = new RankMatch(false, "", "");
    }

    private record Signal(SignalType type, String subject, String reason, int weight, long timeMs) {
    }

    private record ExplosionEvent(Vec3 center, double radius, long timeMs) {
    }

    private record PosTime(Vec3 pos, long timeMs) {
    }

    private record PendingPlace(Vec3 pos, String detail, long timeMs) {
    }

    private record PendingVanish(UUID uuid, String name, int dueTick) {
    }

    private record RecentMessage(String text, long atMs) {
    }

    private static final class Detection {
        String name;
        String reason;
        int score;
        long expiresAt;

        Detection(String name, String reason, int score, long expiresAt) {
            this.name = name;
            this.reason = reason;
            this.score = score;
            this.expiresAt = expiresAt;
        }
    }
}
