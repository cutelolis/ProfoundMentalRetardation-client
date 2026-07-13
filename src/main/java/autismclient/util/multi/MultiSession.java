package autismclient.util.multi;

import autismclient.api.custommenu.CustomMenuAdapterRegistry;
import autismclient.api.custommenu.CustomMenuButton;
import autismclient.api.custommenu.CustomMenuInput;
import autismclient.api.custommenu.CustomMenuSnapshot;
import autismclient.api.custommenu.CustomMenuSubmission;
import autismclient.api.custommenu.CustomMenuSubmitResult;
import autismclient.util.AutismDropAction;
import autismclient.util.AutismMacro;
import autismclient.util.AutismPayloadSupport;
import autismclient.util.macro.CaptureValueAction;
import autismclient.util.macro.ContainerClickSequenceAction;
import autismclient.util.macro.ItemAction;
import autismclient.util.macro.ItemTarget;
import autismclient.util.macro.PacketBurstAction;
import autismclient.util.macro.PickUpAllAction;
import autismclient.util.macro.StoreItemAction;
import autismclient.util.macro.SignEditAction;
import autismclient.util.macro.SwapSlotsAction;
import autismclient.util.macro.WaitForPacketAction;
import autismclient.util.macro.WaitForSlotChangeAction;
import autismclient.util.macro.WaitForSoundAction;
import autismclient.util.macro.WaitPacketMatchAction;
import autismclient.util.macro.WaitForMacroStepAction;
import autismclient.util.macro.XCarryAction;
import autismclient.util.custommenu.CustomMenuSession;
import autismclient.util.AutismPacketArgumentBuilder;
import autismclient.util.AutismPacketRegistry;
import autismclient.util.AutismProxy;
import com.google.common.hash.HashCode;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.netty.channel.ChannelFuture;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientRegistryLayer;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.SignableCommand;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundStoreCookiePacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientboundCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundResetChatPacket;
import net.minecraft.network.protocol.configuration.ClientboundSelectKnownPacks;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.configuration.ServerboundAcceptCodeOfConductPacket;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.network.protocol.configuration.ServerboundSelectKnownPacks;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundChatAckPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatSessionUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundConfigurationAcknowledgedPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Crypt;
import net.minecraft.util.HashOps;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class MultiSession implements MultiMacroHost {
    public enum Status {
        QUEUED,
        AUTHENTICATING,
        CONNECTING,
        LOGIN,
        CONFIGURING,
        JOINED,
        READY,
        DISCONNECTED,
        FAILED
    }

    public record MacroProgress(
        String macroName,
        boolean running,
        int step,
        int totalSteps,
        int loop,
        String detail
    ) {
        static MacroProgress idle() {
            return new MacroProgress("", false, 0, 0, 0, "");
        }
    }

    public record Snapshot(
        String accountId,
        String accountName,
        String proxyName,
        String protocol,
        Status status,
        String detail,
        int ping,
        boolean connected,
        boolean ready,
        String openScreen,
        boolean customMenuOpen,
        String heldItem,
        int hotbarSlot,
        String dimension,
        boolean hasPosition,
        double x,
        double y,
        double z,
        float health,
        float maxHealth,
        int food,
        long menuRevision,
        String macroStatus,
        MacroProgress macroProgress
    ) {
    }

    record Suggest(int id, int start, int length, List<String> entries) {
    }

    interface Sink {

        String identityRejection(MultiSession session, UUID profileId);

        void stateChanged(MultiSession session);

        void chat(MultiSession session, Component text);

        boolean macroStepMet(MultiSession session, WaitForMacroStepAction action);

        default void customMenuNeedsPassword(MultiSession session, String title) {}
    }

    private enum Phase {
        LOGIN(ConnectionProtocol.LOGIN),
        CONFIGURATION(ConnectionProtocol.CONFIGURATION),
        PLAY(ConnectionProtocol.PLAY);

        final ConnectionProtocol protocol;

        Phase(ConnectionProtocol protocol) {
            this.protocol = protocol;
        }
    }

    private static final double GRAVITY = 0.08D;
    private static final double DRAG = 0.98D;
    private static final double TERMINAL_VELOCITY = -3.92D;

    private static final long JOIN_MOVE_ACTIVE_MS = 5000L;

    private static final long AUTO_AUX_INTERVAL_MS = 1000L;
    private static final long IDLE_HEARTBEAT_MS = 1000L;

    private static final long RESPAWN_DELAY_MS = 1000L;
    private static final long CONNECT_PHASE_TIMEOUT_MS = 60_000L;
    private static final java.util.concurrent.atomic.AtomicLong MENU_EPOCHS = new java.util.concurrent.atomic.AtomicLong();
    private static final ClientboundCommandsPacket.NodeBuilder<Object> COMMAND_NODE_BUILDER = new ClientboundCommandsPacket.NodeBuilder<>() {
        @Override
        public ArgumentBuilder<Object, ?> createLiteral(String id) {
            return LiteralArgumentBuilder.literal(id);
        }

        @Override
        public ArgumentBuilder<Object, ?> createArgument(String id, ArgumentType<?> argumentType, @Nullable Identifier suggestionId) {
            return RequiredArgumentBuilder.argument(id, argumentType);
        }

        @Override
        public ArgumentBuilder<Object, ?> configure(ArgumentBuilder<Object, ?> builder, boolean executable, boolean restricted) {
            if (executable) builder.executes(context -> 0);
            return builder;
        }
    };

    private final long generation;
    private final MultiProfile.SessionSpec spec;
    private final AutismProxy proxy;
    private final String proxyName;
    private volatile MultiPacketPolicy policy;
    private final Sink sink;
    private final Executor worker;
    private final autismclient.util.multi.captcha.CaptchaSolver captcha;
    private final MultiViaCompat.Target viaTarget;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.QUEUED);
    private final Map<Identifier, byte[]> cookies = new LinkedHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Set<UUID> listedPlayers = ConcurrentHashMap.newKeySet();
    private final Object scoreboardLock = new Object();
    private final Map<String, ScoreObjective> scoreObjectives = new LinkedHashMap<>();
    private final Map<String, Map<String, TrackedScore>> scoresByObjective = new LinkedHashMap<>();
    private final Map<DisplaySlot, String> displayedObjectives = new EnumMap<>(DisplaySlot.class);
    private final Map<String, ScoreTeam> scoreTeams = new LinkedHashMap<>();
    private final Map<String, String> scoreOwnerTeams = new HashMap<>();
    private volatile RegistryDataCollector registryData = new RegistryDataCollector();
    private volatile LastSeenMessagesTracker lastSeenMessages = new LastSeenMessagesTracker(20);
    private volatile MessageSignatureCache signatureCache = MessageSignatureCache.createDefault();
    private final Object commandSource = new Object();
    private final Object chatStateLock = new Object();

    private volatile String detail = "Queued";
    private volatile long statusSince = System.currentTimeMillis();
    private volatile int ping = -1;
    private volatile Connection connection;

    private volatile int connectEpoch;
    private volatile boolean nextConnectTransferring;

    private volatile boolean suppressChatKey;

    private volatile java.util.UUID serverAssignedUuid;
    private volatile String lastConnectHost = "";
    private volatile int lastConnectPort;
    private volatile MultiIdentityResolver.Identity identity;

    private volatile boolean sessionRefreshAttempted;
    private volatile FeatureFlagSet enabledFeatures = FeatureFlags.DEFAULT_FLAGS;
    private volatile net.minecraft.core.RegistryAccess.Frozen registries;
    private volatile PositionMoveRotation position = new PositionMoveRotation(Vec3.ZERO, Vec3.ZERO, 0.0F, 0.0F);
    private final Object positionLock = new Object();
    private volatile boolean hasPosition;
    private volatile boolean readyOnce;
    private volatile double motionY;

    private volatile boolean grounded = true;

    private volatile boolean primeFallTick;

    private volatile boolean inVehicle;
    private volatile int vehicleId = -1;
    private volatile double vehicleX, vehicleY, vehicleZ;
    private volatile double vehicleFallMotion;
    private volatile boolean postVehicleFall;
    private volatile long postVehicleFallUntil;
    private volatile float vehicleYaw;
    private static final double VEHICLE_GRAVITY = 0.03999999910593033D;
    private volatile long moveActiveUntil;

    private volatile double walkX;
    private volatile double walkZ;
    private volatile long walkUntil;

    private volatile boolean walkGrounded;
    private volatile long walkProbeAt;
    private volatile long lastWalkCorrectionAt;
    private volatile int rapidWalkCorrections;
    private long lastWalkBlockedNoteAt;
    private static final long WALK_BLOCKED_NOTE_COOLDOWN_MS = 30_000L;
    private static final long GROUND_PROBE_MS = 500L;
    private volatile boolean inputShift;
    private volatile boolean inputSprint;
    private volatile long lastLookAt;
    private volatile long lastSwingAt;
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean serverEnforcesSecureChat;
    private volatile long lastMovementAt;
    private volatile int nextChatIndex;
    private volatile CommandDispatcher<Object> commands = new CommandDispatcher<>();
    private volatile boolean hasCommandTree;
    private final AtomicInteger suggestReqId = new AtomicInteger();
    private final Object suggestionLock = new Object();
    private final Map<Integer, Suggest> suggestionReplies = new LinkedHashMap<>();
    private volatile ClientInformation clientInformation = ClientInformation.createDefault();

    private volatile int openContainerId = -1;
    private volatile int containerStateId;
    private volatile int inventoryStateId;
    private volatile String openScreenTitle = "";
    private volatile Component openTitle = Component.empty();
    private volatile long openScreenSeq;
    private volatile long teleportSeq;
    private volatile int gameModeId = -1;
    private final Object chatLogLock = new Object();
    private final java.util.ArrayDeque<String> recentChat = new java.util.ArrayDeque<>();
    private volatile long chatSeqCounter;
    private static final int CHAT_LOG_CAP = 64;

    private volatile boolean packetCaptureArmed;
    private volatile long packetSeqCounter;
    private final Object packetLogLock = new Object();
    private final java.util.ArrayDeque<PktRec> packetLog = new java.util.ArrayDeque<>();
    private static final int PACKET_LOG_CAP = 128;

    private record PktRec(long seq, boolean c2s, Packet<?> packet) {
    }

    private volatile BlockPos signEditorPos;
    private volatile boolean signEditorFront = true;
    private volatile BlockPos lastInteractBlock;
    private volatile int lastInteractEntityId = -1;

    private volatile boolean soundCaptureArmed;
    private volatile long soundSeqCounter;
    private final Object soundLogLock = new Object();
    private final java.util.ArrayDeque<SndRec> soundLog = new java.util.ArrayDeque<>();
    private static final int SOUND_LOG_CAP = 64;

    private record SndRec(long seq, String id, double x, double y, double z) {
    }

    private final Map<String, Long> cooldownExpiry = new ConcurrentHashMap<>();

    private final Map<Long, net.minecraft.world.level.block.state.BlockState> blockUpdates = new ConcurrentHashMap<>();
    private static final int BLOCK_UPDATE_CAP = 512;
    private volatile int selectedHotbar;
    private volatile int useSeq;
    private volatile boolean containerDismissed;
    private final Object menuLock = new Object();
    private final List<ItemStack> menuSlots = new ArrayList<>();

    private final List<ItemStack> playerInv = new ArrayList<>(java.util.Collections.nCopies(46, ItemStack.EMPTY));
    private volatile ItemStack carried = ItemStack.EMPTY;
    private volatile SavedMenu savedMenu;
    private volatile XCarryJob xCarryJob;
    private volatile boolean xCarryForced;
    private volatile boolean xCarryActive;
    private volatile HashedPatchMap.HashGenerator itemHasher;
    private volatile net.minecraft.core.RegistryAccess.Frozen itemHasherFor;
    private volatile String heldItemName = "";
    private volatile String dimension = "";
    private volatile String serverAddress = "";
    private volatile int playerEntityId = -1;
    private volatile float health = 20.0F;
    private volatile float maxHealth = 20.0F;
    private volatile int food = 20;
    private volatile long respawnAt;

    private volatile long menuRevision = MENU_EPOCHS.incrementAndGet() << 32;
    private volatile SignedMessageChain.Encoder signedEncoder = SignedMessageChain.Encoder.UNSIGNED;
    private volatile LocalChatSession chatSession;
    private long userSendWindowAt;
    private int userSendsInWindow;

    private final MultiProfile.LoginMode loginMode;

    private static final long LOGIN_WINDOW_MS = 40_000L;
    private volatile boolean loginMacroRun;
    private long loginMacroDeadline;
    private final String profileName;
    private volatile Map<String, String> formValues;
    private final CustomMenuSession customMenus = new CustomMenuSession();
    private volatile long connectStartedAt;
    private volatile boolean missingPasswordAlerted;

    private static final long LOGIN_SCREEN_ALERT_WINDOW_MS = 10_000L;

    private volatile AuthMeChatLogin.Detection pendingAuthMe = AuthMeChatLogin.Detection.NONE;
    private long authMeNextSendAt;
    private int authMeAttempts;
    private boolean authMeGaveUp;
    private static final long AUTHME_RESEND_MS = 2500L;
    private static final int AUTHME_MAX_ATTEMPTS = 4;
    private volatile MultiMacroRun macroRun;
    private volatile AutismMacro macroStartRequest;
    private volatile boolean macroStopRequest;
    private volatile String macroStatus = "";
    private volatile MacroProgress macroProgress = MacroProgress.idle();

    private record ScoreObjective(String title, Optional<NumberFormat> numberFormat) {
    }

    private record TrackedScore(int score, String display, Optional<NumberFormat> numberFormat) {
    }

    private record ScoreTeam(String prefix, String suffix, Optional<TeamColor> color) {
    }

    private record SavedMenu(
        int containerId,
        int stateId,
        Component title,
        String titleText,
        List<ItemStack> slots,
        ItemStack carried
    ) {
    }

    private enum XCarryPhase {
        COLLECT,
        CLOSE_CONTAINER,
        WAIT_INVENTORY,
        EXECUTE,
        REOPEN,
        DONE
    }

    private static final class XCarryJob {
        final XCarryAction action;
        final ArrayDeque<Integer> collectSlots = new ArrayDeque<>();
        final ArrayDeque<MultiXCarryPlanner.Click> clicks = new ArrayDeque<>();
        final boolean hadContainer;
        final BlockPos reopenBlock;
        final int reopenEntity;
        XCarryPhase phase;
        long nextAt;
        long collectRevision = -1L;
        long collectDeadline;

        XCarryJob(XCarryAction action, boolean hadContainer, BlockPos reopenBlock, int reopenEntity) {
            this.action = action;
            this.hadContainer = hadContainer;
            this.reopenBlock = reopenBlock == null ? null : reopenBlock.immutable();
            this.reopenEntity = reopenEntity;
            this.phase = hadContainer ? XCarryPhase.COLLECT : XCarryPhase.WAIT_INVENTORY;
        }
    }

    private final AtomicReference<String> macroFinishNote = new AtomicReference<>();
    private final MultiEntityTracker entities = new MultiEntityTracker();

    MultiSession(
        long generation,
        MultiProfile.SessionSpec spec,
        AutismProxy proxy,
        String proxyName,
        MultiPacketPolicy policy,
        MultiProfile.LoginMode loginMode,
        String profileName,
        Map<String, String> formValues,
        Sink sink,
        Executor worker,
        MultiViaCompat.Target viaTarget
    ) {
        this.generation = generation;
        this.spec = spec;
        this.proxy = proxy;
        this.proxyName = proxyName == null || proxyName.isBlank() ? "Proxy Off" : proxyName;
        this.policy = new MultiPacketPolicy(policy);
        this.loginMode = loginMode == null ? MultiProfile.LoginMode.Auto : loginMode;
        this.profileName = profileName == null ? "" : profileName;
        this.formValues = formValues == null ? Map.of() : Map.copyOf(formValues);
        this.sink = sink;
        this.worker = worker;
        this.viaTarget = viaTarget;
        this.registries = ClientRegistryLayer.createRegistryAccess().compositeAccess();
        this.captcha = new autismclient.util.multi.captcha.CaptchaSolver(new autismclient.util.multi.captcha.CaptchaSolver.Host() {
            @Override public void sendCaptchaChat(String message) { sendChat(message); }
            @Override public void sendCaptchaCommand(String command) { sendCommand(command); }
            @Override public void captchaNote(String note) { appendLocal(note); }
        }, worker);
    }

    long generation() {
        return generation;
    }

    String accountId() {
        return spec.accountId();
    }

    boolean connected() {
        Connection current = connection;
        return current != null && current.isConnected() && !closed.get();
    }

    boolean ready() {
        return status.get() == Status.READY && connected();
    }

    Status statusValue() {
        return status.get();
    }

    String detailText() {
        return detail;
    }

    public Snapshot snapshot() {
        MultiIdentityResolver.Identity resolved = identity;
        String name = resolved == null ? spec.accountId() : resolved.user().getName();
        Status current = status.get();
        PositionMoveRotation p = position;
        Vec3 pos = p.position();
        boolean isConnected = connected();
        return new Snapshot(
            spec.accountId(),
            name,
            proxyName,
            viaTarget == null || !viaTarget.present() ? "Native 26.2" : viaTarget.label(),
            current,
            detail,
            ping,
            isConnected,
            current == Status.READY && isConnected,
            openScreenTitle,
            customMenus.current() != null,
            heldItemName,
            selectedHotbar + 1,
            dimension,
            hasPosition,
            pos.x,
            pos.y,
            pos.z,
            health,
            maxHealth,
            food,
            menuRevision,
            macroStatus,
            macroProgress
        );
    }

    void start(InetSocketAddress address, String handshakeHost, int handshakePort) {
        if (closed.get()) return;
        setStatus(Status.AUTHENTICATING, "Authenticating");
        CompletableFuture
            .supplyAsync(() -> MultiIdentityResolver.resolve(spec.accountId()), worker)
            .whenComplete((resolved, error) -> {
                if (closed.get()) return;
                if (error != null) {
                    fail(shortError(error));
                    return;
                }
                identity = resolved;
                String identityError = sink.identityRejection(this, resolved.user().getProfileId());
                if (identityError != null && !identityError.isBlank()) {
                    fail(identityError);
                    return;
                }
                connect(address, handshakeHost, handshakePort);
            });
    }

    private void connect(InetSocketAddress address, String handshakeHost, int handshakePort) {
        if (closed.get()) return;
        connectEpoch++;
        lastConnectHost = handshakeHost == null ? "" : handshakeHost;
        lastConnectPort = handshakePort;
        resetChatState();
        serverAddress = handshakeHost == null || handshakeHost.isBlank() ? ""
            : (handshakePort == 25565 ? handshakeHost : handshakeHost + ":" + handshakePort);
        pendingAuthMe = AuthMeChatLogin.Detection.NONE;
        authMeAttempts = 0;
        authMeNextSendAt = 0L;
        authMeGaveUp = false;
        boolean transferIntent = nextConnectTransferring;
        nextConnectTransferring = false;
        setStatus(Status.CONNECTING, transferIntent ? "Transferring" : "Connecting");
        connectStartedAt = System.currentTimeMillis();
        Connection created = new Connection(PacketFlow.CLIENTBOUND);
        connection = created;
        MultiConnectionContext.register(created, proxy);
        MultiViaCompat.applyTarget(created, viaTarget);
        ChannelFuture future;
        try {
            future = Connection.connect(
                address,
                EventLoopGroupHolder.remote(Minecraft.getInstance().options.useNativeTransport()),
                created
            );
        } catch (RuntimeException error) {
            fail(shortError(error));
            return;
        }
        future.addListener(done -> {
            if (closed.get()) {

                created.disconnect(net.minecraft.network.chat.Component.literal("Cancelled"));
                created.handleDisconnection();
                MultiConnectionContext.remove(created);
                return;
            }
            if (!done.isSuccess()) {
                fail(shortError(done.cause()));
                return;
            }
            setStatus(Status.LOGIN, "Logging in");
            created.initiateServerboundPlayConnection(
                handshakeHost,
                handshakePort,
                LoginProtocols.SERVERBOUND,
                LoginProtocols.CLIENTBOUND,
                listener(Phase.LOGIN, net.minecraft.network.protocol.login.ClientLoginPacketListener.class),
                transferIntent
            );
            created.send(new ServerboundHelloPacket(identity.user().getName(), identity.user().getProfileId()));
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T listener(Phase phase, Class<T> listenerType) {
        int epoch = connectEpoch;
        InvocationHandler handler = (proxyObject, method, args) -> invokeListener(phase, epoch, proxyObject, method, args);
        return (T) Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, handler);
    }

    private Object invokeListener(Phase phase, int epoch, Object proxyObject, Method method, Object[] args) {
        return switch (method.getName()) {
            case "protocol" -> phase.protocol;
            case "flow" -> PacketFlow.CLIENTBOUND;
            case "isAcceptingMessages", "shouldHandleMessage" ->
                epoch == connectEpoch && !closed.get() && connection != null && connection.isConnected();
            case "onDisconnect" -> {
                DisconnectionDetails details = args != null && args.length > 0 && args[0] instanceof DisconnectionDetails value ? value : null;
                handleDisconnected(details, epoch);
                yield null;
            }
            case "createDisconnectionInfo" -> {
                net.minecraft.network.chat.Component reason = args != null && args.length > 0
                    && args[0] instanceof net.minecraft.network.chat.Component value
                    ? value
                    : net.minecraft.network.chat.Component.literal("Connection error");
                yield new DisconnectionDetails(reason);
            }
            case "onPacketError" -> {
                if (epoch == connectEpoch) {
                    Throwable error = args != null && args.length > 1 && args[1] instanceof Throwable value ? value : null;
                    fail("Packet error: " + shortError(error));
                }
                yield null;
            }
            case "toString" -> "Multi" + phase + "Listener[" + spec.accountId() + "]";
            case "hashCode" -> System.identityHashCode(proxyObject);
            case "equals" -> args != null && args.length == 1 && proxyObject == args[0];
            default -> {
                if (epoch == connectEpoch && args != null && args.length > 0 && args[0] instanceof Packet<?> packet) {
                    handlePacket(phase, packet);
                }
                yield defaultValue(method.getReturnType());
            }
        };
    }

    private void handlePacket(Phase phase, Packet<?> packet) {
        if (closed.get() || packet == null) return;
        CustomMenuSnapshot beforeMenu = customMenus.current();
        boolean customMenuPacket = customMenus.accept(packet, phase.name());
        if (customMenuPacket) {
            updateCustomMenuTitle(beforeMenu, customMenus.current());
            maybeAlertMissingPassword(customMenus.current());
        }
        boolean critical = customMenuPacket || isCriticalInbound(packet);
        boolean allowed = policy.allows(MultiPacketPolicy.Direction.S2C, packet.getClass().getName(), critical, false);
        if (!allowed && !(packet instanceof ClientboundPlayerChatPacket)) return;
        try {
            handleCommon(packet);
            switch (phase) {
                case LOGIN -> handleLogin(packet);
                case CONFIGURATION -> handleConfiguration(packet);
                case PLAY -> handlePlay(packet, allowed);
            }
        } catch (Throwable error) {
            fail("Protocol error: " + shortError(error));
        }
    }

    private void handleLogin(Packet<?> packet) throws Exception {
        if (packet instanceof ClientboundHelloPacket hello) {
            authorize(hello);
        } else if (packet instanceof ClientboundLoginFinishedPacket finished) {
            sessionRefreshAttempted = false;
            setStatus(Status.CONFIGURING, "Configuring");
            connection.setupInboundProtocol(
                ConfigurationProtocols.CLIENTBOUND,
                listener(Phase.CONFIGURATION, net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener.class)
            );
            send(ServerboundLoginAcknowledgedPacket.INSTANCE, true, false);
            connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
            send(new ServerboundCustomPayloadPacket(new BrandPayload("vanilla")), true, false);
            clientInformation = Minecraft.getInstance().options.buildPlayerInformation();
            send(new ServerboundClientInformationPacket(clientInformation), true, false);
            playerNames.put(finished.gameProfile().id(), finished.gameProfile().name());

            serverAssignedUuid = finished.gameProfile().id();
        } else if (packet instanceof ClientboundLoginDisconnectPacket disconnected) {
            connection.disconnect(disconnected.reason());
        } else if (packet instanceof ClientboundLoginCompressionPacket compression) {
            if (!connection.isMemoryConnection()) connection.setupCompression(compression.getCompressionThreshold(), false);
        } else if (packet instanceof ClientboundCustomQueryPacket query) {
            send(new ServerboundCustomQueryAnswerPacket(query.transactionId(), null), true, false);
        }
    }

    private void authorize(ClientboundHelloPacket hello) throws Exception {
        SecretKey secretKey = Crypt.generateSecretKey();
        PublicKey publicKey = hello.getPublicKey();
        String digest = new BigInteger(Crypt.digestData(hello.getServerId(), publicKey, secretKey)).toString(16);
        Cipher decryptCipher = Crypt.getCipher(2, secretKey);
        Cipher encryptCipher = Crypt.getCipher(1, secretKey);
        ServerboundKeyPacket keyPacket = new ServerboundKeyPacket(secretKey, publicKey, hello.getChallenge());
        Runnable enableEncryption = () -> connection.send(
            keyPacket,
            PacketSendListener.thenRun(() -> connection.setEncryptionKey(decryptCipher, encryptCipher))
        );
        if (!hello.shouldAuthenticate()) {
            enableEncryption.run();
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                identity.sessionService().joinServer(identity.user().getProfileId(), identity.user().getAccessToken(), digest);
                if (!closed.get()) enableEncryption.run();
            } catch (AuthenticationException error) {
                if (!closed.get()) onSessionAuthFailed(error);
            }
        }, worker);
    }

    private void onSessionAuthFailed(AuthenticationException error) {
        boolean refreshable = !sessionRefreshAttempted && !closed.get() && !lastConnectHost.isBlank()
            && identity != null && identity.type() == autismclient.util.AutismAccountType.Microsoft;
        if (!refreshable) {
            fail("Session authentication failed: " + shortError(error));
            return;
        }
        sessionRefreshAttempted = true;
        autismclient.util.AutismAccountManager.get().invalidateSessionToken(spec.accountId());

        beginTransfer(lastConnectHost, lastConnectPort, false, 1000L, true);
    }

    private void handleConfiguration(Packet<?> packet) {
        if (packet instanceof ClientboundRegistryDataPacket registry) {
            registryData.appendContents(registry.registry(), registry.entries());
        } else if (packet instanceof net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket tags) {
            registryData.appendTags(tags.getTags());
        } else if (packet instanceof ClientboundUpdateEnabledFeaturesPacket features) {
            enabledFeatures = FeatureFlags.REGISTRY.fromNames(features.features());
        } else if (packet instanceof ClientboundSelectKnownPacks) {
            send(new ServerboundSelectKnownPacks(java.util.List.of()), true, false);
        } else if (packet instanceof ClientboundResetChatPacket) {
            resetChatState();
        } else if (packet instanceof ClientboundCodeOfConductPacket) {
            send(ServerboundAcceptCodeOfConductPacket.INSTANCE, true, false);
        } else if (packet instanceof ClientboundFinishConfigurationPacket) {
            finishConfiguration();
        }
    }

    private void finishConfiguration() {
        CompletableFuture.runAsync(() -> {
            try {
                if (closed.get()) return;

                net.minecraft.core.RegistryAccess.Frozen base =
                    ClientRegistryLayer.createRegistryAccess().compositeAccess();
                net.minecraft.core.RegistryAccess.Frozen collected =
                    registryData.collectGameRegistries(ResourceProvider.EMPTY, base, false);
                if (closed.get()) return;
                registries = collected;
                connection.setupInboundProtocol(
                    GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(collected)),
                    listener(Phase.PLAY, ClientGamePacketListener.class)
                );
                send(ServerboundFinishConfigurationPacket.INSTANCE, true, false);
                connection.setupOutboundProtocol(
                    GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(collected), () -> true)
                );
                setStatus(Status.JOINED, "Joining");
            } catch (Throwable error) {
                fail("Registry setup failed: " + shortError(error));
            }
        }, worker);
    }

    private void handlePlay(Packet<?> packet, boolean allowedForDisplay) {
        if (packetCaptureArmed) recordPacket(false, packet);
        if (packet instanceof ClientboundLoginPacket login) {
            serverEnforcesSecureChat = login.enforcesSecureChat();
            playerEntityId = login.playerId();
            dimension = dimensionOf(login.commonPlayerSpawnInfo());
            gameModeId = gameTypeIdOf(login.commonPlayerSpawnInfo());
            send(new ServerboundPlayerLoadedPacket(), true, false);
            captcha.reset(System.currentTimeMillis());

            grounded = true;
            primeFallTick = false;
            motionY = 0.0D;
            inVehicle = false;
            vehicleId = -1;
            postVehicleFall = false;
            blockUpdates.clear();

            if (serverEnforcesSecureChat) prepareChatSession();
        } else if (packet instanceof ClientboundGameEventPacket gameEvent) {
            if (gameEvent.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
                gameModeId = (int) gameEvent.getParam();
            }
        } else if (packet instanceof ClientboundOpenSignEditorPacket sign) {
            signEditorPos = sign.getPos();
            signEditorFront = sign.isFrontText();
            openScreenSeq++;
        } else if (packet instanceof ClientboundSoundPacket sound) {
            if (soundCaptureArmed) {
                try {
                    recordSound(sound.getSound().value().location().toString(), sound.getX(), sound.getY(), sound.getZ());
                } catch (RuntimeException ignored) {  }
            }
        } else if (packet instanceof ClientboundCooldownPacket cd) {
            if (cd.duration() > 0) cooldownExpiry.put(cd.cooldownGroup().toString(), System.currentTimeMillis() + (long) cd.duration() * 50L);
            else cooldownExpiry.remove(cd.cooldownGroup().toString());
        } else if (packet instanceof ClientboundBlockUpdatePacket bu) {
            trackBlock(bu.getPos(), bu.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sbu) {
            sbu.runUpdates(this::trackBlock);
        } else if (packet instanceof ClientboundRespawnPacket respawn) {
            dimension = dimensionOf(respawn.commonPlayerSpawnInfo());
            gameModeId = gameTypeIdOf(respawn.commonPlayerSpawnInfo());

            openContainerId = -1;
            openScreenTitle = "";
            openTitle = Component.empty();
            containerDismissed = false;
            synchronized (menuLock) {
                menuSlots.clear();
                menuRevision++;
            }
            entities.clear();
            blockUpdates.clear();
            savedMenu = null;
            xCarryJob = null;
            xCarryForced = false;
            xCarryActive = false;
        } else if (packet instanceof ClientboundSetHealthPacket healthPacket) {
            health = healthPacket.getHealth();
            food = healthPacket.getFood();
        } else if (packet instanceof ClientboundUpdateAttributesPacket attributes) {
            applyAttributes(attributes);
        } else if (packet instanceof ClientboundPlayerPositionPacket move) {
            long correctionAt = System.currentTimeMillis();
            boolean walking;
            synchronized (positionLock) {
                walking = correctionAt < walkUntil;
                position = PositionMoveRotation.calculateAbsolute(position, move.change(), move.relatives());
                motionY = 0.0D;
                if (walking) {
                    grounded = true;
                    primeFallTick = false;
                } else {

                    grounded = false;
                    primeFallTick = true;
                }
            }
            hasPosition = true;
            teleportSeq++;
            if (walking) {

                walkGrounded = true;
                walkProbeAt = correctionAt + GROUND_PROBE_MS;
                trackWalkCorrection(correctionAt);
            } else {

                moveActiveUntil = Math.max(moveActiveUntil, correctionAt + JOIN_MOVE_ACTIVE_MS);
            }
            send(new ServerboundAcceptTeleportationPacket(move.id()), true, false);
            sendPosition(true);
            setStatus(Status.READY, "Ready");
            if (!readyOnce) {

                readyOnce = true;
                moveActiveUntil = System.currentTimeMillis() + JOIN_MOVE_ACTIVE_MS;
            }
        } else if (packet instanceof ClientboundPlayerRotationPacket rotate) {
            float yaw;
            float pitch;
            synchronized (positionLock) {
                yaw = rotate.relativeY() ? position.yRot() + rotate.yRot() : rotate.yRot();
                pitch = rotate.relativeX() ? position.xRot() + rotate.xRot() : rotate.xRot();
                position = position.withRotation(yaw, pitch);
            }
            send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, false, false), true, true);
        } else if (packet instanceof ClientboundSystemChatPacket system) {
            captureChat(system.content());
            noteAuthMePrompt(system.content());
            captcha.onChat(system.content(), System.currentTimeMillis());
            if (allowedForDisplay) sink.chat(this, system.content());
        } else if (packet instanceof ClientboundDisguisedChatPacket disguised) {
            captureChat(disguised.message());
            noteAuthMePrompt(disguised.message());
            captcha.onChat(disguised.message(), System.currentTimeMillis());
            if (allowedForDisplay) sink.chat(this, disguised.message());
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundMapItemDataPacket mapData) {

            mapData.colorPatch().ifPresent(patch -> {
                if (patch.width() == 128 && patch.height() == 128 && patch.startX() == 0 && patch.startY() == 0) {
                    captcha.onMapData(patch.mapColors(), System.currentTimeMillis());
                }
            });
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket title) {
            captcha.onTitle(title.text(), System.currentTimeMillis());
        } else if (packet instanceof net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket subtitle) {
            captcha.onTitle(subtitle.text(), System.currentTimeMillis());
        } else if (packet instanceof ClientboundPlayerChatPacket playerChat) {
            handlePlayerChat(playerChat, allowedForDisplay);
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket playerInfo) {
            boolean addsPlayers = playerInfo.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER);
            boolean updatesListed = playerInfo.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED);
            for (ClientboundPlayerInfoUpdatePacket.Entry entry : playerInfo.entries()) {
                if (entry.profile() != null) playerNames.put(entry.profileId(), entry.profile().name());
                if (updatesListed) {
                    if (entry.listed()) listedPlayers.add(entry.profileId());
                    else listedPlayers.remove(entry.profileId());
                } else if (addsPlayers) {

                    listedPlayers.add(entry.profileId());
                }
                if (identity != null && entry.profileId().equals(identity.user().getProfileId())) {
                    int updatedPing = Math.max(0, entry.latency());
                    if (ping != updatedPing) ping = updatedPing;
                } else if (addsPlayers && identity != null && entry.profile() != null
                        && entry.profile().name().equalsIgnoreCase(identity.user().getName())) {

                    if (!entry.profileId().equals(serverAssignedUuid)) {
                        serverAssignedUuid = entry.profileId();
                        rekeyChatEncoder();
                    }
                }
            }
            while (playerNames.size() > 2048) {
                UUID oldest = playerNames.keySet().iterator().next();
                playerNames.remove(oldest);
                listedPlayers.remove(oldest);
            }
        } else if (packet instanceof ClientboundPlayerInfoRemovePacket remove) {
            for (UUID id : remove.profileIds()) {
                playerNames.remove(id);
                listedPlayers.remove(id);
            }
        } else if (packet instanceof ClientboundSetPlayerTeamPacket team) {
            trackTeam(team);
        } else if (packet instanceof ClientboundSetObjectivePacket objective) {
            trackObjective(objective);
        } else if (packet instanceof ClientboundSetDisplayObjectivePacket displayObjective) {
            trackDisplayObjective(displayObjective);
        } else if (packet instanceof ClientboundSetScorePacket score) {
            trackScore(score);
        } else if (packet instanceof ClientboundResetScorePacket resetScore) {
            resetScore(resetScore);
        } else if (packet instanceof ClientboundCommandsPacket commandPacket) {
            commands = new CommandDispatcher<>(commandPacket.getRoot(
                CommandBuildContext.simple(registries, enabledFeatures),
                COMMAND_NODE_BUILDER
            ));
            hasCommandTree = true;
        } else if (packet instanceof ClientboundCommandSuggestionsPacket suggestions) {
            Suggest reply = new Suggest(suggestions.id(), suggestions.start(), suggestions.length(),
                suggestions.suggestions().stream().map(ClientboundCommandSuggestionsPacket.Entry::text).toList());
            synchronized (suggestionLock) {
                suggestionReplies.put(reply.id(), reply);
                while (suggestionReplies.size() > 64) {
                    suggestionReplies.remove(suggestionReplies.keySet().iterator().next());
                }
            }
        } else if (packet instanceof ClientboundOpenScreenPacket open) {
            openContainerId = open.getContainerId();
            openTitle = open.getTitle() == null ? Component.empty() : open.getTitle();
            openScreenTitle = openTitle.getString();
            containerDismissed = false;
            openScreenSeq++;
            synchronized (menuLock) {
                menuSlots.clear();
                menuRevision++;
            }
        } else if (packet instanceof ClientboundContainerSetContentPacket content) {
            updateSavedMenuContent(content.containerId(), content.stateId(), content.items(), content.carriedItem());
            if (content.containerId() == 0) inventoryStateId = content.stateId();
            if (content.containerId() == currentContainerId()) {
                containerStateId = content.stateId();
                synchronized (menuLock) {

                    carried = content.carriedItem();
                    menuSlots.clear();
                    menuSlots.addAll(content.items());
                    menuRevision++;
                }
                syncPlayerInvFromMenu();
                recomputeHeldItem();
                if (content.containerId() == 0) refreshXCarryActive();
            }
        } else if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            updateSavedMenuCursor(currentContainerId(), cursor.contents());
            synchronized (menuLock) {
                carried = cursor.contents();
                menuRevision++;
            }
            refreshXCarryActive();
        } else if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            updateSavedMenuSlot(slot.getContainerId(), slot.getStateId(), slot.getSlot(), slot.getItem());
            if (slot.getContainerId() == 0) inventoryStateId = slot.getStateId();
            if (slot.getContainerId() == currentContainerId()) {
                containerStateId = slot.getStateId();
                setMenuSlot(slot.getSlot(), slot.getItem());
                syncPlayerInvFromMenu();
                recomputeHeldItem();
                if (slot.getContainerId() == 0) refreshXCarryActive();
            }
        } else if (packet instanceof ClientboundContainerClosePacket close) {
            if (close.getContainerId() == openContainerId) {
                openContainerId = -1;
                openScreenTitle = "";
                openTitle = Component.empty();
                containerDismissed = false;
                synchronized (menuLock) {
                    menuSlots.clear();
                    menuRevision++;
                }
                heldItemName = "";
            }
        } else if (packet instanceof ClientboundSetHeldSlotPacket held) {
            int slot = held.slot();
            if (slot >= 0 && slot <= 8) {
                selectedHotbar = slot;
                recomputeHeldItem();

                send(new ServerboundSetCarriedItemPacket(slot), false, false);
            }
        } else if (packet instanceof ClientboundAnimatePacket animate) {

            if (animate.getId() == playerEntityId && animate.getAction() == ClientboundAnimatePacket.SWING_MAIN_HAND) {
                send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
            }
        } else if (packet instanceof ClientboundSetPassengersPacket passengers) {
            handleSetPassengers(passengers);
        } else if (packet instanceof ClientboundSetPlayerInventoryPacket inv) {

            int handler = inventoryIndexToHandler(inv.slot());
            if (handler >= 0) {
                synchronized (menuLock) {
                    if (handler < playerInv.size()) playerInv.set(handler, inv.contents() == null ? ItemStack.EMPTY : inv.contents());
                    menuRevision++;
                }
                if (openContainerId < 0) setMenuSlot(handler, inv.contents());
            }
            recomputeHeldItem();
        } else if (packet instanceof ClientboundAddEntityPacket add) {
            entities.put(add.getId(), entityTypeKey(add.getType()), add.getX(), add.getY(), add.getZ());
        } else if (packet instanceof ClientboundEntityPositionSyncPacket sync) {
            Vec3 ep = sync.values().position();
            entities.move(sync.id(), ep.x, ep.y, ep.z);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket rem) {
            for (int i = 0; i < rem.getEntityIds().size(); i++) {
                int id = rem.getEntityIds().getInt(i);
                entities.remove(id);
                if (inVehicle && id == vehicleId) dismountVehicle();
            }
        } else if (packet instanceof ClientboundStartConfigurationPacket) {
            beginReconfiguration();
        }
    }

    private int currentContainerId() {
        return openContainerId >= 0 ? openContainerId : 0;
    }

    private void setMenuSlot(int index, ItemStack stack) {
        if (index < 0) return;
        synchronized (menuLock) {
            while (menuSlots.size() <= index) menuSlots.add(ItemStack.EMPTY);
            menuSlots.set(index, stack == null ? ItemStack.EMPTY : stack);
            menuRevision++;
        }
    }

    private void updateSavedMenuContent(int containerId, int stateId, List<ItemStack> items, ItemStack cursor) {
        SavedMenu saved = savedMenu;
        if (saved == null || saved.containerId() != containerId) return;
        savedMenu = new SavedMenu(containerId, stateId, saved.title(), saved.titleText(),
            copyStacks(items), nonNull(cursor).copy());
    }

    private void updateSavedMenuSlot(int containerId, int stateId, int slot, ItemStack stack) {
        SavedMenu saved = savedMenu;
        if (saved == null || saved.containerId() != containerId || slot < 0 || slot >= saved.slots().size()) return;
        List<ItemStack> slots = copyStacks(saved.slots());
        slots.set(slot, nonNull(stack).copy());
        savedMenu = new SavedMenu(containerId, stateId, saved.title(), saved.titleText(), slots, saved.carried());
    }

    private void updateSavedMenuCursor(int containerId, ItemStack cursor) {
        SavedMenu saved = savedMenu;
        if (saved == null || saved.containerId() != containerId) return;
        savedMenu = new SavedMenu(saved.containerId(), saved.stateId(), saved.title(), saved.titleText(),
            saved.slots(), nonNull(cursor).copy());
    }

    private static int inventoryIndexToHandler(int playerSlot) {
        if (playerSlot >= 0 && playerSlot <= 8) return 36 + playerSlot;
        if (playerSlot >= 9 && playerSlot <= 35) return playerSlot;
        if (playerSlot >= 36 && playerSlot <= 39) return 44 - playerSlot;
        if (playerSlot == 40) return 45;
        return -1;
    }

    private static String dimensionOf(CommonPlayerSpawnInfo info) {
        try {
            return info == null ? "" : info.dimension().identifier().toString();
        } catch (RuntimeException error) {
            return "";
        }
    }

    private void applyAttributes(ClientboundUpdateAttributesPacket packet) {
        if (packet.getEntityId() != playerEntityId) return;
        try {
            for (ClientboundUpdateAttributesPacket.AttributeSnapshot snapshot : packet.getValues()) {
                if (snapshot.attribute().value() != Attributes.MAX_HEALTH.value()) continue;
                double base = snapshot.base();
                double multBase = 0.0;
                double multTotal = 1.0;
                for (AttributeModifier modifier : snapshot.modifiers()) {
                    switch (modifier.operation()) {
                        case ADD_VALUE -> base += modifier.amount();
                        case ADD_MULTIPLIED_BASE -> multBase += modifier.amount();
                        case ADD_MULTIPLIED_TOTAL -> multTotal *= 1.0 + modifier.amount();
                    }
                }
                maxHealth = (float) Math.max(1.0, base * (1.0 + multBase) * multTotal);
            }
        } catch (RuntimeException ignored) {

        }
    }

    private String displayName() {
        MultiIdentityResolver.Identity resolved = identity;
        return resolved == null ? spec.accountId() : resolved.user().getName();
    }

    private int heldHandlerSlot() {
        return openContainerId < 0 ? 36 + selectedHotbar : menuSlots.size() - 9 + selectedHotbar;
    }

    private void recomputeHeldItem() {
        String name = "";
        synchronized (menuLock) {
            int idx = heldHandlerSlot();
            if (idx >= 0 && idx < menuSlots.size()) {
                ItemStack stack = menuSlots.get(idx);
                if (stack != null && !stack.isEmpty()) name = stack.getHoverName().getString();
            }
        }
        heldItemName = name;
    }

    public record ViewSlot(int x, int y, int handler, ItemStack item) {
    }

    public int hotbarIndexOfHandler(int handler) {
        synchronized (menuLock) {
            if (openContainerId < 0) {
                return (handler >= 36 && handler <= 44) ? handler - 36 : -1;
            }
            int start = menuSlots.size() - 9;
            return (handler >= start && handler < menuSlots.size()) ? handler - start : -1;
        }
    }

    public int selectedHotbarHandler() {
        int idx = Math.max(0, Math.min(8, selectedHotbar));
        synchronized (menuLock) {
            return openContainerId < 0 ? 36 + idx : (menuSlots.size() - 9) + idx;
        }
    }

    public record MenuView(Component title, List<ViewSlot> slots, ItemStack carried) {
    }

    public MenuView menuView() {
        if (openContainerId < 0 || containerDismissed) return inventoryView();
        List<ViewSlot> out = new ArrayList<>();
        synchronized (menuLock) {
            int size = menuSlots.size();
            int containerCount = Math.max(0, size - 36);
            for (int i = 0; i < containerCount; i++) laidSlot(out, i, (i % 9) * 18, (i / 9) * 18);
            int invTop = ((containerCount + 8) / 9) * 18 + 8;
            int base = containerCount;
            for (int i = 0; i < 27 && base + i < size; i++) laidSlot(out, base + i, (i % 9) * 18, invTop + (i / 9) * 18);
            for (int i = 0; i < 9 && base + 27 + i < size; i++) laidSlot(out, base + 27 + i, i * 18, invTop + 3 * 18 + 4);
        }
        Component title = openScreenTitle.isBlank() ? Component.literal("Container") : openTitle;
        return new MenuView(title, out, carried);
    }

    public MenuView inventoryView() {
        List<ViewSlot> out = new ArrayList<>();
        synchronized (menuLock) {
            int base = openContainerId < 0 ? -1 : menuSlots.size() - 36;
            invSlot(out, 0, invClickHandler(0, base), 146, 20);
            for (int i = 1; i <= 4; i++) invSlot(out, i, invClickHandler(i, base), 90 + ((i - 1) % 2) * 18, 10 + ((i - 1) / 2) * 18);
            for (int i = 5; i <= 8; i++) invSlot(out, i, invClickHandler(i, base), 0, (i - 5) * 18);
            for (int i = 9; i <= 35; i++) invSlot(out, i, invClickHandler(i, base), ((i - 9) % 9) * 18, 76 + ((i - 9) / 9) * 18);
            for (int i = 36; i <= 44; i++) invSlot(out, i, invClickHandler(i, base), (i - 36) * 18, 134);
            invSlot(out, 45, invClickHandler(45, base), 69, 54);
        }
        return new MenuView(Component.literal(displayName() + " Inventory"), out, carried);
    }

    private int invClickHandler(int h, int base) {
        if (base < 0) return h;
        if (h >= 9 && h <= 35) return base + (h - 9);
        if (h >= 36 && h <= 44) return base + 27 + (h - 36);
        return -1;
    }

    private void invSlot(List<ViewSlot> out, int h, int clickHandler, int x, int y) {
        ItemStack stack = h >= 0 && h < playerInv.size() && playerInv.get(h) != null ? playerInv.get(h) : ItemStack.EMPTY;
        out.add(new ViewSlot(x, y, clickHandler, stack));
    }

    private void laidSlot(List<ViewSlot> out, int handler, int x, int y) {
        ItemStack stack = handler >= 0 && handler < menuSlots.size() && menuSlots.get(handler) != null
            ? menuSlots.get(handler) : ItemStack.EMPTY;
        out.add(new ViewSlot(x, y, handler, stack));
    }

    private void syncPlayerInvFromMenu() {
        synchronized (menuLock) {
            int size = menuSlots.size();
            if (openContainerId < 0) {
                for (int h = 0; h < 46; h++) playerInv.set(h, h < size ? nonNull(menuSlots.get(h)) : ItemStack.EMPTY);
            } else {
                int base = size - 36;
                if (base < 0) return;
                for (int i = 0; i < 27; i++) playerInv.set(9 + i, nonNull(menuSlots.get(base + i)));
                for (int i = 0; i < 9; i++) playerInv.set(36 + i, nonNull(menuSlots.get(base + 27 + i)));
            }
        }
    }

    private static ItemStack nonNull(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack;
    }

    long menuRevision() {
        return menuRevision;
    }

    public String closeSilent() {
        if (status.get() != Status.READY) return "Session is not ready";
        containerDismissed = true;
        openScreenTitle = "";
        openTitle = Component.empty();
        synchronized (menuLock) {
            menuRevision++;
        }
        return "Sent";
    }

    @Override
    public boolean saveGui(boolean closeAfter, boolean sendClosePacket) {
        if (status.get() != Status.READY) return false;
        synchronized (menuLock) {
            List<ItemStack> source = menuSlots.isEmpty() ? playerInv : menuSlots;
            savedMenu = new SavedMenu(
                currentContainerId(),
                containerStateId,
                openTitle == null ? Component.empty() : openTitle.copy(),
                openScreenTitle,
                copyStacks(source),
                nonNull(carried).copy()
            );
        }
        if (!closeAfter) return true;
        return sendClosePacket ? "Sent".equals(closeContainer()) : "Sent".equals(closeSilent());
    }

    @Override
    public boolean desyncGui() {
        if (status.get() != Status.READY || openContainerId < 0) return false;

        return send(new ServerboundContainerClosePacket(openContainerId), false, false);
    }

    @Override
    public boolean restoreGui() {
        SavedMenu saved = savedMenu;
        if (status.get() != Status.READY || saved == null) return false;
        openContainerId = saved.containerId() == 0 ? -1 : saved.containerId();
        containerStateId = saved.stateId();
        openTitle = saved.title() == null ? Component.empty() : saved.title().copy();
        openScreenTitle = saved.titleText() == null ? "" : saved.titleText();
        carried = nonNull(saved.carried()).copy();
        containerDismissed = false;
        openScreenSeq++;
        synchronized (menuLock) {
            menuSlots.clear();
            menuSlots.addAll(copyStacks(saved.slots()));
            menuRevision++;
        }
        syncPlayerInvFromMenu();
        recomputeHeldItem();
        return true;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks == null ? 0 : stacks.size());
        if (stacks != null) {
            for (ItemStack stack : stacks) copy.add(nonNull(stack).copy());
        }
        return copy;
    }

    @Override
    public int runXCarry(XCarryAction action, long now) {
        if (status.get() != Status.READY || action == null) return -1;
        XCarryJob job = xCarryJob;
        if (job == null || job.action != action) {
            boolean hadContainer = openContainerId >= 0;
            if (hadContainer && lastInteractBlock == null && lastInteractEntityId < 0) return -1;
            job = new XCarryJob(action, hadContainer, lastInteractBlock, lastInteractEntityId);
            if (action.mode == XCarryAction.Mode.PUT_IN) {
                xCarryForced = true;
                xCarryActive = true;
            }
            if (hadContainer && action.mode == XCarryAction.Mode.PUT_IN) {
                synchronized (menuLock) {
                    int ownSlots = Math.max(0, menuSlots.size() - 36);
                    job.collectSlots.addAll(MultiXCarryPlanner.collectContainerSlots(action, menuSlots, ownSlots));
                }
            }
            xCarryJob = job;
        }
        if (now < job.nextAt) return 0;

        int budget = 8;
        while (budget-- > 0) {
            switch (job.phase) {
                case COLLECT -> {
                    if (job.collectRevision >= 0L) {
                        if (menuRevision() == job.collectRevision && now < job.collectDeadline) return 0;
                        job.collectRevision = -1L;
                    }
                    Integer slot = job.collectSlots.pollFirst();
                    if (slot != null) {
                        if (!sendPredictedClick(slot, 0, ContainerInput.QUICK_MOVE)) return failXCarry();
                        job.collectRevision = menuRevision();
                        job.collectDeadline = now + 2_000L;
                        job.nextAt = now + 50L;
                        return 0;
                    }
                    job.phase = XCarryPhase.CLOSE_CONTAINER;
                }
                case CLOSE_CONTAINER -> {
                    int closingId = openContainerId;
                    if (closingId >= 0 && !send(new ServerboundContainerClosePacket(closingId), false, false)) {
                        return failXCarry();
                    }
                    activateInventoryMirror();
                    job.phase = XCarryPhase.WAIT_INVENTORY;
                    job.nextAt = now + 50L;
                    return 0;
                }
                case WAIT_INVENTORY -> {
                    activateInventoryMirror();
                    synchronized (menuLock) {
                        job.clicks.addAll(MultiXCarryPlanner.plan(action, playerInv, carried));
                    }
                    job.phase = XCarryPhase.EXECUTE;
                }
                case EXECUTE -> {
                    MultiXCarryPlanner.Click click = job.clicks.pollFirst();
                    if (click == null) {
                        xCarryForced = true;
                        refreshXCarryActive();
                        job.phase = job.hadContainer ? XCarryPhase.REOPEN : XCarryPhase.DONE;
                        continue;
                    }
                    if (!sendPredictedClick(click.slot(), click.button(), click.input())) return failXCarry();
                    long delay = Math.max(0L, Math.min(500L, click.delayAfterMs()));
                    if (delay > 0L) {
                        job.nextAt = now + delay;
                        return 0;
                    }
                }
                case REOPEN -> {
                    boolean sent;
                    if (job.reopenBlock != null) {
                        BlockPos pos = job.reopenBlock;
                        sent = "Sent".equals(useOnBlock(pos.getX(), pos.getY(), pos.getZ(), "UP"));
                    } else {
                        sent = "Sent".equals(interactEntity(job.reopenEntity, false));
                    }
                    if (!sent) return failXCarry();
                    job.phase = XCarryPhase.DONE;
                    job.nextAt = now + 50L;
                    return 0;
                }
                case DONE -> {
                    xCarryJob = null;
                    refreshXCarryActive();
                    return 1;
                }
            }
        }
        return 0;
    }

    private int failXCarry() {
        xCarryJob = null;
        xCarryForced = true;
        refreshXCarryActive();
        return -1;
    }

    @Override
    public void cancelXCarry() {
        xCarryJob = null;
        refreshXCarryActive();
    }

    private void activateInventoryMirror() {
        openContainerId = -1;
        containerStateId = inventoryStateId;
        openScreenTitle = "";
        openTitle = Component.empty();
        containerDismissed = false;
        synchronized (menuLock) {
            menuSlots.clear();
            menuSlots.addAll(copyStacks(playerInv));
            menuRevision++;
        }
        recomputeHeldItem();
    }

    private void refreshXCarryActive() {
        synchronized (menuLock) {
            if (!xCarryForced) {
                xCarryActive = false;
                return;
            }
            boolean stored = !nonNull(carried).isEmpty();
            for (int slot = 0; !stored && slot <= 8 && slot < playerInv.size(); slot++) {
                stored = !nonNull(playerInv.get(slot)).isEmpty();
            }
            if (!stored && playerInv.size() > 45) stored = !nonNull(playerInv.get(45)).isEmpty();
            if (!stored) xCarryForced = false;
            xCarryActive = stored;
        }
    }

    private void beginReconfiguration() {
        sendChatAcknowledgement();

        resetChatState();
        registryData = new RegistryDataCollector();
        clearScoreboardState();
        savedMenu = null;
        xCarryJob = null;
        synchronized (suggestionLock) {
            suggestionReplies.clear();
        }
        hasCommandTree = false;
        hasPosition = false;
        readyOnce = false;
        openContainerId = -1;
        openScreenTitle = "";
        openTitle = Component.empty();
        containerDismissed = false;
        containerStateId = 0;
        inventoryStateId = 0;
        xCarryForced = false;
        xCarryActive = false;
        heldItemName = "";
        dimension = "";
        carried = ItemStack.EMPTY;
        health = 20.0F;
        maxHealth = 20.0F;
        food = 20;
        respawnAt = 0L;
        synchronized (menuLock) {
            menuSlots.clear();
            java.util.Collections.fill(playerInv, ItemStack.EMPTY);
            menuRevision++;
        }
        entities.clear();
        setStatus(Status.CONFIGURING, "Reconfiguring");
        connection.setupInboundProtocol(
            ConfigurationProtocols.CLIENTBOUND,
            listener(Phase.CONFIGURATION, net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener.class)
        );
        send(ServerboundConfigurationAcknowledgedPacket.INSTANCE, true, false);
        connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
    }

    private void handlePlayerChat(ClientboundPlayerChatPacket packet, boolean show) {
        Optional<SignedMessageBody> unpacked;
        boolean acknowledge;
        synchronized (chatStateLock) {
            int expected = nextChatIndex++;
            if (packet.globalIndex() != expected) {
                fail("Bad chat index: expected " + expected + ", got " + packet.globalIndex());
                return;
            }
            unpacked = packet.body().unpack(signatureCache);
            if (unpacked.isEmpty()) {
                fail("Unrecognized chat signature");
                return;
            }
            signatureCache.push(unpacked.get(), packet.signature());
            acknowledge = packet.signature() != null
                && lastSeenMessages.addPending(packet.signature(), show)
                && lastSeenMessages.offset() > 64;
        }
        if (acknowledge) sendChatAcknowledgement();
        captureChatText(unpacked.get().content());
        if (!show) return;
        String sender = playerNames.getOrDefault(packet.sender(), packet.sender().toString().substring(0, 8));

        Component content = packet.unsignedContent() != null
            ? packet.unsignedContent()
            : Component.literal(unpacked.get().content());
        sink.chat(this, Component.literal("<" + sender + "> ").append(content));
    }

    private void handleCommon(Packet<?> packet) {
        if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
            send(new ServerboundKeepAlivePacket(keepAlive.getId()), true, false);
        } else if (packet instanceof ClientboundPingPacket pingPacket) {
            send(new ServerboundPongPacket(pingPacket.getId()), true, false);
        } else if (packet instanceof ClientboundResourcePackPushPacket pack) {
            send(new ServerboundResourcePackPacket(pack.id(), ServerboundResourcePackPacket.Action.ACCEPTED), true, false);
            send(new ServerboundResourcePackPacket(pack.id(), ServerboundResourcePackPacket.Action.DOWNLOADED), true, false);
            send(new ServerboundResourcePackPacket(pack.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED), true, false);
        } else if (packet instanceof ClientboundStoreCookiePacket cookie) {
            if (!cookies.containsKey(cookie.key()) && cookies.size() >= 64) {
                cookies.remove(cookies.keySet().iterator().next());
            }
            cookies.put(cookie.key(), cookie.payload().clone());
        } else if (packet instanceof ClientboundCookieRequestPacket request) {
            send(new ServerboundCookieResponsePacket(request.key(), cookies.get(request.key())), true, false);
        } else if (packet instanceof ClientboundDisconnectPacket disconnected) {
            if (maybeRejoinKeyless(disconnected.reason())) return;
            if (maybeReconnectAfterVerification(disconnected.reason())) return;
            connection.disconnect(disconnected.reason());
        } else if (packet instanceof ClientboundTransferPacket transfer) {

            suppressChatKey = false;
            serverAssignedUuid = null;
            beginTransfer(transfer.host(), transfer.port());
        }
    }

    private boolean maybeRejoinKeyless(net.minecraft.network.chat.Component reason) {
        if (suppressChatKey || closed.get() || lastConnectHost.isBlank()) return false;
        String text = reason == null ? "" : reason.getString().toLowerCase(Locale.ROOT);
        if (!text.contains("signature")) return false;
        suppressChatKey = true;
        appendLocal("Server rejected signed chat; rejoining without a key in 5s (chat may be limited here).");
        beginTransfer(lastConnectHost, lastConnectPort, false, 5000L);
        return true;
    }

    private boolean maybeReconnectAfterVerification(net.minecraft.network.chat.Component reason) {
        if (closed.get() || lastConnectHost.isBlank()) return false;
        String text = reason == null ? "" : reason.getString().toLowerCase(Locale.ROOT);
        boolean verifiedOk = text.contains("successfully passed")
            || text.contains("passed the verification")
            || (text.contains("success") && text.contains("verif"))
            || text.contains("able to play")
            || text.contains("when you reconnect");
        if (!verifiedOk) return false;

        long delay = 5000L + java.util.concurrent.ThreadLocalRandom.current().nextLong(2000L);
        beginTransfer(lastConnectHost, lastConnectPort, false, delay);
        return true;
    }

    private void beginTransfer(String host, int port) {
        beginTransfer(host, port, true, 0L);
    }

    private void beginTransfer(String host, int port, boolean transferIntent, long delayMs) {
        beginTransfer(host, port, transferIntent, delayMs, false);
    }

    private void beginTransfer(String host, int port, boolean transferIntent, long delayMs, boolean reresolve) {
        if (closed.get() || host == null || host.isBlank()) return;
        Connection old = connection;
        connectEpoch++;
        if (old != null) {
            MultiConnectionContext.remove(old);
            try {
                old.disconnect(net.minecraft.network.chat.Component.literal("Reconnecting to " + host + ":" + port));
            } catch (RuntimeException ignored) {  }
        }
        setStatus(Status.CONNECTING, delayMs > 0 ? "Reconnecting in " + (delayMs / 1000) + "s" : "Reconnecting to " + host);
        final String targetHost = host;
        final int targetPort = port;
        java.util.concurrent.Executor exec = delayMs > 0
            ? CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS, worker)
            : worker;
        CompletableFuture
            .supplyAsync(() -> resolveTransferTarget(targetHost, targetPort), exec)
            .whenComplete((address, error) -> {
                if (closed.get()) return;
                if (error != null || address == null) {
                    fail("Reconnect failed: " + targetHost + ":" + targetPort);
                    return;
                }
                if (reresolve) {

                    try {
                        identity = MultiIdentityResolver.resolve(spec.accountId());
                    } catch (Exception resolveError) {
                        fail("Re-authentication failed: " + shortError(resolveError));
                        return;
                    }
                }
                nextConnectTransferring = transferIntent;
                connect(address, targetHost, targetPort);
            });
    }

    private static InetSocketAddress resolveTransferTarget(String host, int port) {
        net.minecraft.client.multiplayer.resolver.ServerAddress server =
            new net.minecraft.client.multiplayer.resolver.ServerAddress(host, port);
        return net.minecraft.client.multiplayer.resolver.ServerNameResolver.DEFAULT.resolveAddress(server)
            .map(net.minecraft.client.multiplayer.resolver.ResolvedServerAddress::asInetSocketAddress)
            .orElse(null);
    }

    private void updateCustomMenuTitle(CustomMenuSnapshot before, CustomMenuSnapshot after) {
        if (after != null) {
            openScreenTitle = after.title();
            openScreenSeq++;
            return;
        }
        if (before != null) {
            openScreenTitle = openContainerId >= 0 ? openTitle.getString() : "";
        }
    }

    void updateFormValues(Map<String, String> values) {
        formValues = values == null ? Map.of() : Map.copyOf(values);
        if (values != null && values.containsKey("password")) missingPasswordAlerted = true;
    }

    private volatile long autoLoginAnsweredGen = Long.MIN_VALUE;
    private volatile long autoLoginRetryAt;

    private boolean canAnswerLoginScreen() {
        if (customMenus.current() == null) return false;
        MultiMacroRun run = macroRun;

        if (run != null && run.isHandlingCustomMenu()) return true;
        return !loginPassword().isEmpty();
    }

    private String loginPassword() {
        String p = formValues.get("password");
        if (p != null && !p.isBlank()) return p;
        try {
            String global = autismclient.util.AutismJoinMacroController.openFormValues().get("password");
            if (global != null && !global.isBlank()) return global;
        } catch (RuntimeException ignored) {  }
        return "";
    }

    private boolean loginWindowOpen(long now) {
        return connectStartedAt != 0L && now - connectStartedAt < LOGIN_WINDOW_MS;
    }

    private void maybeAutoLogin(long now) {
        if (closed.get()) return;
        if (loginMode != MultiProfile.LoginMode.Auto || !loginWindowOpen(now)) return;
        Status s = status.get();
        if (s != Status.CONFIGURING && s != Status.JOINED) return;
        CustomMenuSnapshot snapshot = customMenus.current();
        if (snapshot == null) { autoLoginAnsweredGen = Long.MIN_VALUE; return; }
        if (snapshot.generation() == autoLoginAnsweredGen) return;
        if (now < autoLoginRetryAt) return;
        MultiMacroRun run = macroRun;
        if (run != null && run.handlesCustomMenu()) return;
        boolean hasText = false;
        for (CustomMenuInput input : snapshot.inputs()) {
            if (input.kind() == CustomMenuInput.Kind.TEXT) { hasText = true; break; }
        }
        String password = loginPassword();
        if (hasText && password.isEmpty()) return;
        CustomMenuButton button;
        Map<String, String> values;
        try {

            CustomMenuButton accept = hasText ? null
                : autismclient.util.macro.CustomMenuActionSupport.acceptButton(snapshot.buttons());
            button = accept != null ? accept
                : autismclient.util.macro.CustomMenuActionSupport.autoSubmitButton(snapshot.buttons());
            if (button == null) return;
            values = new LinkedHashMap<>();
            for (CustomMenuInput input : snapshot.inputs()) {
                values.put(input.key(), input.kind() == CustomMenuInput.Kind.TEXT ? password : input.initialValue());
            }
        } catch (RuntimeException error) {
            autoLoginRetryAt = now + 1000L;
            return;
        }
        CustomMenuSubmitResult result = submitCustomMenu(snapshot, new CustomMenuSubmission(values, button));
        if (result != null && result.success()) {

            autoLoginAnsweredGen = snapshot.generation();
        } else {
            autoLoginRetryAt = now + 1000L;
        }
    }

    private void maybeAlertMissingPassword(CustomMenuSnapshot snapshot) {
        if (snapshot == null || missingPasswordAlerted) return;
        long started = connectStartedAt;
        if (started == 0L || System.currentTimeMillis() - started > LOGIN_SCREEN_ALERT_WINDOW_MS) return;
        boolean hasTextInput = false;
        for (autismclient.api.custommenu.CustomMenuInput input : snapshot.inputs()) {
            if (input.kind() == autismclient.api.custommenu.CustomMenuInput.Kind.TEXT) {
                hasTextInput = true;
                break;
            }
        }
        if (!hasTextInput || formValues.containsKey("password")) return;
        missingPasswordAlerted = true;
        sink.customMenuNeedsPassword(this, snapshot.title());
    }

    private void noteAuthMePrompt(Component content) {
        if (content == null) return;
        AuthMeChatLogin.Detection detection = AuthMeChatLogin.detect(content.getString());
        if (detection.kind() != AuthMeChatLogin.Kind.NONE) pendingAuthMe = detection;
    }

    private void maybeAutoLoginChat(long now) {
        if (closed.get() || status.get() != Status.READY) return;
        if (loginMode != MultiProfile.LoginMode.Auto || !loginWindowOpen(now)) return;
        if (customMenus.current() != null) return;
        AuthMeChatLogin.Detection detection = pendingAuthMe;
        if (detection.kind() == AuthMeChatLogin.Kind.NONE) return;
        if (now < authMeNextSendAt) return;
        String password = loginPassword();
        if (password.isEmpty()) {
            if (!missingPasswordAlerted) {
                missingPasswordAlerted = true;
                sink.customMenuNeedsPassword(this, "AuthMe login");
            }
            return;
        }
        if (authMeAttempts >= AUTHME_MAX_ATTEMPTS) {
            if (!authMeGaveUp) {
                authMeGaveUp = true;
                appendLocal("AuthMe login failed after " + AUTHME_MAX_ATTEMPTS + " attempts - check the password.");
            }
            return;
        }
        pendingAuthMe = AuthMeChatLogin.Detection.NONE;
        authMeAttempts++;
        authMeNextSendAt = now + AUTHME_RESEND_MS;
        sendConsoleLine(detection.commandLine(password));
    }

    private void prepareChatSession() {
        MultiIdentityResolver.Identity id = identity;
        if (id == null || suppressChatKey) return;
        net.minecraft.util.SignatureValidator validator = id.profileKeyValidator();
        if (validator == null) return;
        id.keyPairManager().prepareKeyPair().whenComplete((pair, error) -> {
            if (closed.get() || suppressChatKey || error != null || pair == null || pair.isEmpty()) return;
            LocalChatSession created = LocalChatSession.create(pair.get());
            net.minecraft.network.chat.RemoteChatSession.Data data = created.asRemote().asData();

            java.util.UUID signAs = serverAssignedUuid != null ? serverAssignedUuid : id.user().getProfileId();
            try {
                data.validate(new com.mojang.authlib.GameProfile(signAs, id.user().getName()), validator);
            } catch (net.minecraft.world.entity.player.ProfilePublicKey.ValidationException invalid) {
                return;
            }
            synchronized (chatStateLock) {
                chatSession = created;
                signedEncoder = created.createMessageEncoder(signAs);
            }
            send(new ServerboundChatSessionUpdatePacket(data), true, false);
        });
    }

    private void rekeyChatEncoder() {
        synchronized (chatStateLock) {
            if (chatSession == null || serverAssignedUuid == null) return;
            signedEncoder = chatSession.createMessageEncoder(serverAssignedUuid);
        }
    }

    int requestSuggestions(String command) {
        if (status.get() != Status.READY || command == null) return -1;
        int id = suggestReqId.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
        return send(new ServerboundCommandSuggestionPacket(id, command), true, false) ? id : -1;
    }

    Suggest suggestion(int requestId) {
        synchronized (suggestionLock) {
            return suggestionReplies.get(requestId);
        }
    }

    private void trackObjective(ClientboundSetObjectivePacket packet) {
        String name = packet.getObjectiveName();
        if (name == null || name.isBlank()) return;
        synchronized (scoreboardLock) {
            if (packet.getMethod() == ClientboundSetObjectivePacket.METHOD_REMOVE) {
                scoreObjectives.remove(name);
                scoresByObjective.remove(name);
                displayedObjectives.values().removeIf(name::equals);
                return;
            }
            String title = packet.getDisplayName() == null ? name : packet.getDisplayName().getString();
            if (scoreObjectives.size() >= 64 && !scoreObjectives.containsKey(name)) {
                String oldest = scoreObjectives.keySet().iterator().next();
                scoreObjectives.remove(oldest);
                scoresByObjective.remove(oldest);
                displayedObjectives.values().removeIf(oldest::equals);
            }
            scoreObjectives.put(name, new ScoreObjective(title, packet.getNumberFormat()));
        }
    }

    private void trackDisplayObjective(ClientboundSetDisplayObjectivePacket packet) {
        DisplaySlot slot = packet.getSlot();
        if (slot == null) return;
        String objective = packet.getObjectiveName();
        synchronized (scoreboardLock) {
            if (objective == null || objective.isBlank()) displayedObjectives.remove(slot);
            else displayedObjectives.put(slot, objective);
        }
    }

    private void trackScore(ClientboundSetScorePacket packet) {
        String objective = packet.objectiveName();
        String owner = packet.owner();
        if (objective == null || objective.isBlank() || owner == null || owner.isBlank()) return;
        String display = packet.display().map(Component::getString).orElse(owner);
        synchronized (scoreboardLock) {
            if (scoresByObjective.size() >= 64 && !scoresByObjective.containsKey(objective)) {
                String oldest = scoresByObjective.keySet().iterator().next();
                scoresByObjective.remove(oldest);
                scoreObjectives.remove(oldest);
                displayedObjectives.values().removeIf(oldest::equals);
            }
            Map<String, TrackedScore> scores = scoresByObjective.computeIfAbsent(objective, ignored -> new LinkedHashMap<>());
            if (scores.size() >= 2048 && !scores.containsKey(owner)) scores.remove(scores.keySet().iterator().next());
            scores.put(owner, new TrackedScore(packet.score(), display, packet.numberFormat()));
        }
    }

    private void trackTeam(ClientboundSetPlayerTeamPacket packet) {
        String teamName = packet.getName();
        if (teamName == null || teamName.isBlank()) return;
        synchronized (scoreboardLock) {
            if (packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                scoreTeams.remove(teamName);
                scoreOwnerTeams.entrySet().removeIf(entry -> teamName.equals(entry.getValue()));
                return;
            }
            packet.getParameters().ifPresent(parameters -> {
                if (scoreTeams.size() >= 256 && !scoreTeams.containsKey(teamName)) {
                    String oldest = scoreTeams.keySet().iterator().next();
                    scoreTeams.remove(oldest);
                    scoreOwnerTeams.entrySet().removeIf(entry -> oldest.equals(entry.getValue()));
                }
                scoreTeams.put(teamName, new ScoreTeam(
                    componentText(parameters.playerPrefix()),
                    componentText(parameters.playerSuffix()),
                    parameters.color()
                ));
            });
            ClientboundSetPlayerTeamPacket.Action playerAction = packet.getPlayerAction();
            if (playerAction == ClientboundSetPlayerTeamPacket.Action.ADD) {
                for (String owner : packet.getPlayers()) {
                    if (owner == null || owner.isBlank()) continue;
                    if (scoreOwnerTeams.size() < 4096 || scoreOwnerTeams.containsKey(owner)) {
                        scoreOwnerTeams.put(owner, teamName);
                    }
                }
            } else if (playerAction == ClientboundSetPlayerTeamPacket.Action.REMOVE) {
                for (String owner : packet.getPlayers()) scoreOwnerTeams.remove(owner, teamName);
            }
        }
    }

    private static String componentText(Component component) {
        return component == null ? "" : component.getString();
    }

    private void resetScore(ClientboundResetScorePacket packet) {
        String owner = packet.owner();
        String objective = packet.objectiveName();
        synchronized (scoreboardLock) {
            if (objective != null && !objective.isBlank()) {
                Map<String, TrackedScore> scores = scoresByObjective.get(objective);
                if (scores != null) scores.remove(owner);
            } else {
                for (Map<String, TrackedScore> scores : scoresByObjective.values()) scores.remove(owner);
            }
        }
    }

    private void clearScoreboardState() {
        synchronized (scoreboardLock) {
            scoreObjectives.clear();
            scoresByObjective.clear();
            displayedObjectives.clear();
            scoreTeams.clear();
            scoreOwnerTeams.clear();
        }
    }

    public String sendConsoleLine(String line) {
        String value = line == null ? "" : line.trim();
        if (value.isEmpty()) return "Empty input";
        if (status.get() != Status.READY) return "Session is not ready";
        if (!reserveUserSend()) return "Rate limited";
        return value.startsWith("/") ? sendCommand(value.substring(1)) : sendChat(value);
    }

    private String sendChat(String content) {
        if (content.length() > 256) return "Chat exceeds 256 characters";
        ServerboundChatPacket packet;
        synchronized (chatStateLock) {
            Instant now = Instant.now();
            long salt = Crypt.SaltSupplier.getLong();
            LastSeenMessagesTracker.Update update = lastSeenMessages.generateAndApplyUpdate();

            MessageSignature signature = signedEncoder.pack(new SignedMessageBody(content, now, salt, update.lastSeen()));
            packet = new ServerboundChatPacket(content, now, salt, signature, update.update());
        }
        return send(packet, false, false)
            ? "Sent" : "Blocked by packet policy";
    }

    private String sendCommand(String command) {
        if (command.isBlank()) return "Empty command";
        if (!hasCommandTree) {
            return send(new ServerboundChatCommandPacket(command), false, false) ? "Sent" : "Blocked by packet policy";
        }
        SignableCommand<Object> signable = SignableCommand.of(commands.parse(command, commandSource));
        if (signable.arguments().isEmpty()) {
            return send(new ServerboundChatCommandPacket(command), false, false) ? "Sent" : "Blocked by packet policy";
        }
        ServerboundChatCommandSignedPacket packet;
        synchronized (chatStateLock) {

            if (chatSession == null) {
                return send(new ServerboundChatCommandPacket(command), false, false) ? "Sent" : "Blocked by packet policy";
            }
            Instant now = Instant.now();
            long salt = Crypt.SaltSupplier.getLong();
            LastSeenMessagesTracker.Update update = lastSeenMessages.generateAndApplyUpdate();
            ArgumentSignatures signatures = ArgumentSignatures.signCommand(signable, argument ->
                signedEncoder.pack(new SignedMessageBody(argument, now, salt, update.lastSeen())));
            packet = new ServerboundChatCommandSignedPacket(command, now, salt, signatures, update.update());
        }
        return send(packet, false, false)
            ? "Sent" : "Blocked by packet policy";
    }

    public String sendManual(Class<? extends Packet<?>> packetClass, String arguments) {
        if (status.get() != Status.READY) return "Session is not ready";
        if (!MultiManualPackets.isSafe(packetClass)) return "Packet is not headless-safe";
        if (!reserveUserSend()) return "Rate limited";
        AutismPacketArgumentBuilder.Result result = AutismPacketArgumentBuilder.build(packetClass, arguments);
        if (!result.ok() || result.packet() == null) return result.message();
        return send(result.packet(), false, result.packet() instanceof ServerboundMovePlayerPacket)
            ? "Sent" : "Blocked by packet policy";
    }

    private String containerPrecheck() {
        if (status.get() != Status.READY) return "Session is not ready";
        return "";
    }

    public String clickSlot(int handlerSlot, int button, ContainerInput input) {
        if (status.get() != Status.READY) return "Session is not ready";
        return sendPredictedClick(handlerSlot, button, input) ? "Sent" : "Blocked by packet policy";
    }

    private boolean sendPredictedClick(int handlerSlot, int button, ContainerInput input) {
        HashedPatchMap.HashGenerator hasher = itemHasher();
        Int2ObjectOpenHashMap<HashedStack> changed = new Int2ObjectOpenHashMap<>();
        ItemStack predictedCarried;
        int stateId;
        synchronized (menuLock) {
            stateId = containerStateId;
            ItemStack cur = carried == null ? ItemStack.EMPTY : carried;
            boolean primary = button == 0;
            boolean inSlot = handlerSlot >= 0 && handlerSlot < menuSlots.size();
            if (input == ContainerInput.PICKUP && (button == 0 || button == 1) && inSlot) {

                ItemStack slotStack = menuSlots.get(handlerSlot);
                ItemStack[] result = predictPickup(cur, slotStack == null ? ItemStack.EMPTY : slotStack, primary);
                predictedCarried = result[0];
                menuSlots.set(handlerSlot, result[1]);
                changed.put(handlerSlot, hashOf(result[1], hasher));
            } else if (input == ContainerInput.PICKUP && handlerSlot == -999 && !cur.isEmpty()) {

                predictedCarried = primary ? ItemStack.EMPTY : shrunk(cur, 1);
            } else if (input == ContainerInput.THROW && inSlot) {

                ItemStack slotStack = menuSlots.get(handlerSlot);
                if (slotStack != null && !slotStack.isEmpty()) {
                    ItemStack after = primary ? shrunk(slotStack, 1) : ItemStack.EMPTY;
                    menuSlots.set(handlerSlot, after);
                    changed.put(handlerSlot, hashOf(after, hasher));
                }
                predictedCarried = cur;
            } else {

                predictedCarried = cur;
            }
            carried = predictedCarried;
            menuRevision++;
        }
        syncPlayerInvFromMenu();
        ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
            currentContainerId(), stateId, (short) handlerSlot, (byte) button,
            input, changed, hashOf(predictedCarried, hasher));
        boolean ok = send(packet, false, false);
        recomputeHeldItem();
        return ok;
    }

    private boolean clickSlotRaw(int handlerSlot, int button, ContainerInput input) {
        ServerboundContainerClickPacket packet = new ServerboundContainerClickPacket(
            currentContainerId(), containerStateId, (short) handlerSlot, (byte) button,
            input, new Int2ObjectOpenHashMap<>(), hashOf(carried, itemHasher()));
        return send(packet, false, false);
    }

    private static HashedStack hashOf(ItemStack stack, HashedPatchMap.HashGenerator hasher) {
        if (stack == null || stack.isEmpty()) return HashedStack.EMPTY;
        if (hasher == null) return HashedStack.EMPTY;
        return HashedStack.create(stack, hasher);
    }

    private HashedPatchMap.HashGenerator itemHasher() {
        net.minecraft.core.RegistryAccess.Frozen reg = registries;
        if (reg == null) return null;
        HashedPatchMap.HashGenerator cached = itemHasher;
        if (cached != null && itemHasherFor == reg) return cached;
        RegistryOps<HashCode> ops = reg.createSerializationContext(HashOps.CRC32C_INSTANCE);
        HashedPatchMap.HashGenerator built = component -> component.encodeValue(ops)
            .getOrThrow(msg -> new IllegalArgumentException("Failed to hash " + component + ": " + msg))
            .asInt();
        itemHasher = built;
        itemHasherFor = reg;
        return built;
    }

    private static ItemStack shrunk(ItemStack stack, int by) {
        ItemStack copy = stack.copy();
        copy.shrink(by);
        return copy.isEmpty() ? ItemStack.EMPTY : copy;
    }

    private static ItemStack[] predictPickup(ItemStack cursor, ItemStack slot, boolean primary) {
        ItemStack cur = cursor.copy();
        ItemStack sl = slot.copy();
        if (sl.isEmpty()) {
            if (!cur.isEmpty()) {
                int amount = primary ? cur.getCount() : 1;
                int place = Math.min(amount, Math.min(cur.getCount(), cur.getMaxStackSize()));
                if (place > 0) {
                    sl = cur.copyWithCount(place);
                    cur.shrink(place);
                }
            }
        } else if (cur.isEmpty()) {
            int amount = primary ? sl.getCount() : (sl.getCount() + 1) / 2;
            int take = Math.min(amount, sl.getCount());
            if (take > 0) {
                cur = sl.copyWithCount(take);
                sl.shrink(take);
            }
        } else if (ItemStack.isSameItemSameComponents(sl, cur)) {
            int amount = primary ? cur.getCount() : 1;
            int space = Math.max(0, sl.getMaxStackSize() - sl.getCount());
            int place = Math.min(amount, Math.min(cur.getCount(), space));
            if (place > 0) {
                sl.grow(place);
                cur.shrink(place);
            }
        } else if (cur.getCount() <= cur.getMaxStackSize()) {
            ItemStack tmp = sl;
            sl = cur;
            cur = tmp;
        }
        return new ItemStack[]{cur.isEmpty() ? ItemStack.EMPTY : cur, sl.isEmpty() ? ItemStack.EMPTY : sl};
    }

    public int visibleToHandler(int visible) {
        if (openContainerId < 0) {
            if (visible >= 0 && visible <= 8) return 36 + visible;
            if (visible >= 9 && visible <= 35) return visible;
            if (visible >= 36 && visible <= 39) return 44 - visible;
            if (visible == 40) return 45;
            return -1;
        }
        int size;
        synchronized (menuLock) {
            size = menuSlots.size();
        }
        int base = size - 36;
        if (visible >= 100) {
            int gui = visible - 100;
            return gui < base ? gui : -1;
        }
        if (visible >= 0 && visible <= 8) return base + 27 + visible;
        if (visible >= 9 && visible <= 35) return base + (visible - 9);
        return -1;
    }

    public String dropFullInventory() {
        if (status.get() != Status.READY) return "Session is not ready";
        if (!reserveUserSend()) return "Rate limited";
        int dropped = 0;
        synchronized (menuLock) {
            int size = menuSlots.size();
            int from = openContainerId < 0 ? 9 : Math.max(0, size - 36);
            int to = openContainerId < 0 ? Math.min(45, size) : size;
            for (int handler = from; handler < to; handler++) {
                ItemStack stack = menuSlots.get(handler);
                if (stack == null || stack.isEmpty()) continue;
                if (clickSlotRaw(handler, 1, ContainerInput.THROW)) dropped++;
            }
        }
        return dropped > 0 ? "Sent" : "Nothing to drop";
    }

    public String selectHotbar(int slot0to8) {
        String pre = containerPrecheck();
        if (!pre.isBlank()) return pre;
        int slot = Math.max(0, Math.min(8, slot0to8));
        boolean ok = send(new ServerboundSetCarriedItemPacket(slot), false, false);
        if (ok) {
            selectedHotbar = slot;
            recomputeHeldItem();
        }
        return ok ? "Sent" : "Blocked by packet policy";
    }

    public String dropSelected(boolean all) {
        String pre = containerPrecheck();
        if (!pre.isBlank()) return pre;
        ServerboundPlayerActionPacket.Action action = all
            ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
            : ServerboundPlayerActionPacket.Action.DROP_ITEM;
        boolean ok = send(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN), false, false);
        if (ok) {

            synchronized (menuLock) {
                int handler = heldHandlerSlot();
                if (handler >= 0 && handler < menuSlots.size()) {
                    ItemStack stack = menuSlots.get(handler);
                    if (!stack.isEmpty()) {
                        int left = all ? 0 : stack.getCount() - 1;
                        menuSlots.set(handler, left <= 0 ? ItemStack.EMPTY : stack.copyWithCount(left));
                        menuRevision++;
                    }
                }
            }
            recomputeHeldItem();
        }
        return ok ? "Sent" : "Blocked by packet policy";
    }

    public String closeContainer() {
        String pre = containerPrecheck();
        if (!pre.isBlank()) return pre;
        boolean ok = send(new ServerboundContainerClosePacket(currentContainerId()), false, false);
        if (ok) {
            openContainerId = -1;
            openScreenTitle = "";
            openTitle = Component.empty();
            containerDismissed = false;
            synchronized (menuLock) {
                menuSlots.clear();
                menuRevision++;
            }
        }
        return ok ? "Sent" : "Blocked by packet policy";
    }

    public String useItem() {
        String pre = containerPrecheck();
        if (!pre.isBlank()) return pre;
        PositionMoveRotation current = position;
        return send(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, ++useSeq, current.yRot(), current.xRot()), false, false)
            ? "Sent" : "Blocked by packet policy";
    }

    public String swingArm() {
        String pre = containerPrecheck();
        if (!pre.isBlank()) return pre;
        return send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false) ? "Sent" : "Blocked by packet policy";
    }

    public int findSlotByItem(String query) {
        if (query == null || query.isBlank()) return -1;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        synchronized (menuLock) {
            for (int i = 0; i < menuSlots.size(); i++) {
                ItemStack stack = menuSlots.get(i);
                if (stack == null || stack.isEmpty()) continue;
                String hover = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (hover.contains(needle)) return i;
                if (id != null && (id.getPath().equals(needle) || id.toString().equals(needle))) return i;
            }
        }
        return -1;
    }

    private static final int ITEM_PLAN_CAP = 256;

    @Override
    public java.util.List<int[]> resolveItemClicks(ItemAction a) {
        java.util.List<int[]> plan = new ArrayList<>();
        if (a == null) return plan;
        synchronized (menuLock) {
            int size = menuSlots.size();
            if (size == 0) return plan;
            java.util.List<ItemTarget> targets = !a.itemTargets.isEmpty() ? a.itemTargets : legacyItemTargets(a.itemNames);
            if (!targets.isEmpty()) {
                for (int ei = 0; ei < targets.size() && plan.size() < ITEM_PLAN_CAP; ei++) {
                    ItemTarget target = targets.get(ei);
                    if (target == null || (!target.hasIdentity() && !target.hasSlot())) continue;
                    AutismDropAction action = a.getItemAction(ei);
                    int button = a.getItemButton(ei);
                    if (action != AutismDropAction.QUICK_MOVE && action.usesFixedButton()) button = action.getButton();
                    int slot;
                    if (action == AutismDropAction.PICKUP_ALL && a.useCursorItemForPickupAll) {
                        slot = cursorGatherSlot(size);
                    } else {
                        slot = resolveItemEntrySlot(a, target, ei, size);
                    }
                    if (slot < 0) continue;
                    addItemClicks(plan, slot, button, action, clampItemCount(a.getItemTime(ei), action));
                }
            } else if (a.useSlot && a.targetSlot >= 0) {
                AutismDropAction action = a.getAction();
                int button = a.getButton();
                if (action != AutismDropAction.QUICK_MOVE && action.usesFixedButton()) button = action.getButton();
                int slot = action == AutismDropAction.PICKUP_ALL && a.useCursorItemForPickupAll
                    ? cursorGatherSlot(size) : visibleToHandler(a.targetSlot);
                if (slot >= 0 && slot < size) addItemClicks(plan, slot, button, action, clampItemCount(a.times, action));
            }
        }
        return plan;
    }

    @Override
    public void clickResolved(int handlerSlot, int button, int containerInputOrdinal) {
        ContainerInput[] all = ContainerInput.values();
        if (containerInputOrdinal < 0 || containerInputOrdinal >= all.length) return;
        clickSlot(handlerSlot, button, all[containerInputOrdinal]);
    }

    @Override
    public boolean sendPacketBurst(PacketBurstAction action) {
        if (action == null || action.mode == null) return false;
        return switch (action.mode) {
            case CONTAINER_CLICK -> clickSlotRaw(action.slot, action.button, containerInput(action.containerInput));
            case ENTITY_INTERACT -> {
                if (action.entityId < 0) yield false;
                lastInteractEntityId = action.entityId;
                lastInteractBlock = null;
                yield send(new ServerboundInteractPacket(action.entityId,
                    "OFF_HAND".equalsIgnoreCase(action.hand) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    Vec3.ZERO, false), false, false);
            }
            case BUNDLE_SELECT -> send(new ServerboundSelectBundleItemPacket(
                action.slot >= 0 ? action.slot : 36 + selectedHotbar, action.bundleIndex), false, false);
            case USE_ITEM -> {
                InteractionHand hand = "OFF_HAND".equalsIgnoreCase(action.hand)
                    ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                PositionMoveRotation current = position;
                yield send(new ServerboundUseItemPacket(hand, ++useSeq, current.yRot(), current.xRot()), false, false);
            }
            case RELEASE_ITEM -> send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN), false, false);
            case SET_CARRIED_ITEM -> "Sent".equals(selectHotbar(Math.max(0, Math.min(8, action.carriedSlot))));
            case CLIENT_INFORMATION -> send(new ServerboundClientInformationPacket(clientInformation), false, false);
            case CLOSE_CONTAINER -> closeContainerBurst(action.containerId);
            case CLIENT_COMMAND -> false;
        };
    }

    private boolean closeContainerBurst(int containerId) {
        boolean closesTrackedContainer = containerId == currentContainerId();
        boolean sent = send(new ServerboundContainerClosePacket(containerId), false, false);
        if (sent && closesTrackedContainer) {
            openContainerId = -1;
            openScreenTitle = "";
            openTitle = Component.empty();
            containerDismissed = false;
            synchronized (menuLock) {
                menuSlots.clear();
                menuRevision++;
            }
        }
        return sent;
    }

    private static ContainerInput containerInput(String value) {
        if (value == null || value.isBlank()) return ContainerInput.PICKUP;
        try {
            return ContainerInput.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ContainerInput.PICKUP;
        }
    }

    @Override
    public int writeBook(List<String> pages, String title, boolean sign, boolean requireHeld, int excludedHotbarMask) {
        if (pages == null || pages.isEmpty()) return -1;
        int hotbar = -1;
        synchronized (menuLock) {
            int selected = Math.max(0, Math.min(8, selectedHotbar));
            if (requireHeld) {
                int handler = 36 + selected;
                if ((excludedHotbarMask & (1 << selected)) != 0 || handler >= playerInv.size()
                    || !isWritableBook(playerInv.get(handler))) return -1;
                hotbar = selected;
            } else {
                int bookHandler = -1;
                for (int h = 36; h <= 44 && h < playerInv.size(); h++) {
                    int slot = h - 36;
                    if ((excludedHotbarMask & (1 << slot)) == 0 && isWritableBook(playerInv.get(h))) {
                        bookHandler = h;
                        break;
                    }
                }
                if (bookHandler >= 0) {
                    hotbar = bookHandler - 36;
                } else {
                    for (int h = 9; h <= 35 && h < playerInv.size(); h++) {
                        if (!isWritableBook(playerInv.get(h))) continue;
                        hotbar = availableBookHotbar(excludedHotbarMask);
                        if (hotbar < 0) return -1;
                        int liveHandler = visibleToHandler(h);
                        if (liveHandler < 0 || !clickSlotRaw(liveHandler, hotbar, ContainerInput.SWAP)) return -1;
                        bookHandler = 36 + hotbar;
                        break;
                    }
                    if (bookHandler < 0) return -1;
                }
            }
        }
        if (hotbar < 0) return -1;
        List<String> bounded = pages.stream().limit(100).map(page -> page == null ? "" : page).toList();
        Optional<String> signedTitle = sign ? Optional.of(MultiManager.singleLine(title, 32)) : Optional.empty();
        return send(new ServerboundEditBookPacket(hotbar, bounded, signedTitle), false, false) ? hotbar : -1;
    }

    private int availableBookHotbar(int excludedMask) {
        for (int slot = 0; slot < 9; slot++) {
            if ((excludedMask & (1 << slot)) == 0 && playerInv.get(36 + slot).isEmpty()) return slot;
        }
        for (int slot = 0; slot < 9; slot++) if ((excludedMask & (1 << slot)) == 0) return slot;
        return -1;
    }

    private static boolean isWritableBook(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "minecraft:writable_book".equals(id.toString());
    }

    private int cursorGatherSlot(int size) {
        if (carried == null || carried.isEmpty()) return -1;
        for (int h = 0; h < size; h++) {
            ItemStack stack = menuSlots.get(h);
            if (stack == null || stack.isEmpty()) return h;
        }
        return -1;
    }

    private static int clampItemCount(int raw, AutismDropAction action) {
        if (action == AutismDropAction.DROP_STACK) return 1;
        return Math.max(1, Math.min(64, raw));
    }

    private static void addItemClicks(java.util.List<int[]> plan, int slot, int button, AutismDropAction action, int count) {
        ContainerInput input;
        try {
            input = action.toContainerInput();
        } catch (RuntimeException ignored) {
            return;
        }
        int ord = input.ordinal();
        for (int i = 0; i < count && plan.size() < ITEM_PLAN_CAP; i++) plan.add(new int[]{slot, button, ord});
    }

    private static java.util.List<ItemTarget> legacyItemTargets(java.util.List<String> names) {
        java.util.List<ItemTarget> out = new ArrayList<>();
        if (names == null) return out;
        for (String n : names) {
            ItemTarget t = ItemTarget.fromLegacyEntry(n);
            if (t.hasSlot() || t.hasIdentity()) out.add(t);
        }
        return out;
    }

    private int resolveItemEntrySlot(ItemAction a, ItemTarget target, int ei, int size) {
        int configuredSlot = target.hasSlot() ? target.slot : -1;
        if (configuredSlot >= 0 && target.hasIdentity()) {
            return findExactItemSlot(target, visibleToHandler(configuredSlot), size);
        } else if (configuredSlot >= 0) {
            int h = visibleToHandler(configuredSlot);
            return (h >= 0 && h < size) ? h : -1;
        } else if (a.useSlot && a.targetSlot >= 0) {
            return findExactItemSlot(target, visibleToHandler(a.targetSlot), size);
        }
        return findMatchingItemSlot(target, a.getItemSearchScope(ei), a.getStackAmountMode(ei), size);
    }

    private int findExactItemSlot(ItemTarget target, int handler, int size) {
        if (handler < 0 || handler >= size) return -1;
        ItemStack stack = menuSlots.get(handler);
        if (stack == null || stack.isEmpty()) return -1;
        if (!target.hasIdentity()) return handler;
        return target.matches(stack, handlerToVisible(handler, size)) ? handler : -1;
    }

    private int findMatchingItemSlot(ItemTarget target, ItemAction.ItemSearchScope scope,
                                     ItemAction.StackAmountMode mode, int size) {
        if (!target.hasIdentity() && !target.hasSlot()) return -1;
        ItemAction.StackAmountMode m = mode == null ? ItemAction.StackAmountMode.DEFAULT : mode;
        ItemAction.ItemSearchScope sc = scope == null ? ItemAction.ItemSearchScope.BOTH : scope;
        int base = openContainerId >= 0 ? size - 36 : -1;
        int bestSlot = -1;
        int bestScore = -1;
        int bestCount = m == ItemAction.StackAmountMode.LEAST ? Integer.MAX_VALUE : -1;
        for (int h = 0; h < size; h++) {
            ItemStack stack = menuSlots.get(h);
            if (stack == null || stack.isEmpty()) continue;
            boolean playerInv = base < 0 || h >= base;
            if (sc == ItemAction.ItemSearchScope.GUI && (base < 0 || playerInv)) continue;
            if (sc == ItemAction.ItemSearchScope.PLAYER_INVENTORY && !playerInv) continue;
            int score = target.score(stack, handlerToVisible(h, size));
            if (score < 0) continue;
            int count = stack.getCount();
            if (isBetterItemCandidate(m, score, count, bestScore, bestCount)) {
                bestScore = score;
                bestCount = count;
                bestSlot = h;
            }
        }
        return bestScore >= 0 ? bestSlot : -1;
    }

    private static boolean isBetterItemCandidate(ItemAction.StackAmountMode mode, int score, int count,
                                                 int bestScore, int bestCount) {
        if (bestScore < 0) return true;
        if (mode == ItemAction.StackAmountMode.LEAST) {
            if (count != bestCount) return count < bestCount;
            return score > bestScore;
        }
        if (mode == ItemAction.StackAmountMode.MOST) {
            if (count != bestCount) return count > bestCount;
            return score > bestScore;
        }
        return score > bestScore;
    }

    private int handlerToVisible(int handler, int size) {
        if (openContainerId < 0) {
            if (handler >= 36 && handler <= 44) return handler - 36;
            if (handler >= 9 && handler <= 35) return handler;
            if (handler >= 5 && handler <= 8) return 44 - handler;
            if (handler == 45) return 40;
            return handler;
        }
        int base = size - 36;
        if (handler < base) return 100 + handler;
        if (handler < base + 27) return 9 + (handler - base);
        return handler - (base + 27);
    }

    @Override
    public java.util.List<int[]> resolveStoreClicks(StoreItemAction a) {
        java.util.List<int[]> plan = new ArrayList<>();
        if (a == null) return plan;
        synchronized (menuLock) {
            if (openContainerId < 0) return plan;
            int size = menuSlots.size();
            int base = size - 36;
            if (base < 0) return plan;
            int quickMove = ContainerInput.QUICK_MOVE.ordinal();
            boolean loot = a.mode == StoreItemAction.Mode.LOOT;
            for (int h = 0; h < size && plan.size() < ITEM_PLAN_CAP; h++) {
                ItemStack stack = menuSlots.get(h);
                if (stack == null || stack.isEmpty()) continue;
                boolean playerInv = h >= base;
                if (loot && playerInv) continue;
                if (!loot && !playerInv) continue;
                if (storeMatches(a, stack, handlerToVisible(h, size))) plan.add(new int[]{h, 0, quickMove});
            }
        }
        return plan;
    }

    private static boolean storeMatches(StoreItemAction a, ItemStack stack, int visibleSlot) {
        if (a.allItems) return true;
        java.util.List<ItemTarget> targets = !a.itemTargets.isEmpty() ? a.itemTargets : legacyItemTargets(a.targetItems);
        if (targets.isEmpty()) return false;
        for (ItemTarget t : targets) {
            if (t == null || (!t.hasSlot() && !t.hasIdentity())) continue;
            if (t.matches(stack, visibleSlot)) return true;
        }
        return false;
    }

    @Override
    public java.util.List<int[]> resolveSwapClicks(SwapSlotsAction a) {
        java.util.List<int[]> plan = new ArrayList<>();
        if (a == null) return plan;
        synchronized (menuLock) {
            int size = menuSlots.size();
            if (size == 0) return plan;

            if (carried != null && !carried.isEmpty()) return plan;
            int from = resolveSwapEndpoint(a, true, size);
            int to = resolveSwapEndpoint(a, false, size);
            if (from < 0 || to < 0 || from == to) return plan;
            int pickup = ContainerInput.PICKUP.ordinal();
            plan.add(new int[]{from, 0, pickup});
            plan.add(new int[]{to, 0, pickup});
            plan.add(new int[]{from, 0, pickup});
        }
        return plan;
    }

    private int resolveSwapEndpoint(SwapSlotsAction a, boolean isFrom, int size) {
        boolean useItem = isFrom ? a.fromUseItemName : a.toUseItemName;
        if (useItem) {
            ItemTarget t = isFrom ? a.fromItemTarget : a.toItemTarget;
            if (t == null || (!t.hasIdentity() && !t.hasSlot())) return -1;
            if (t.hasSlot() && !t.hasIdentity()) return validHandler(visibleToHandler(t.slot), size);
            return findMatchingItemSlot(t, ItemAction.ItemSearchScope.BOTH, ItemAction.StackAmountMode.DEFAULT, size);
        }
        int slot = isFrom ? a.fromSlot : a.toSlot;
        return slot >= 0 ? validHandler(visibleToHandler(slot), size) : -1;
    }

    private static int validHandler(int handler, int size) {
        return handler >= 0 && handler < size ? handler : -1;
    }

    @Override
    public java.util.List<int[]> resolvePickupAllClicks(PickUpAllAction a) {
        java.util.List<int[]> plan = new ArrayList<>();
        if (a == null) return plan;
        synchronized (menuLock) {
            ItemStack cur = carried;
            if (cur == null || cur.isEmpty()) return plan;
            int size = menuSlots.size();

            int trigger = -1;
            boolean hasMatch = false;
            for (int h = 0; h < size; h++) {
                ItemStack stack = menuSlots.get(h);
                if (stack == null || stack.isEmpty()) {
                    if (trigger < 0) trigger = h;
                } else if (ItemStack.isSameItemSameComponents(stack, cur)) {
                    hasMatch = true;
                }
            }
            if (trigger < 0 || !hasMatch) return plan;
            int pickupAll = ContainerInput.PICKUP_ALL.ordinal();
            int times = Math.max(1, Math.min(64, a.times));
            for (int i = 0; i < times; i++) plan.add(new int[]{trigger, 0, pickupAll});
        }
        return plan;
    }

    @Override
    public java.util.List<int[]> resolveSequenceClicks(ContainerClickSequenceAction a) {
        java.util.List<int[]> plan = new ArrayList<>();
        if (a == null) return plan;

        if (a.containerSource != ContainerClickSequenceAction.ContainerSource.CURRENT
                && a.containerSource != ContainerClickSequenceAction.ContainerSource.PLAYER_INVENTORY) {
            return plan;
        }
        int button = 0;
        try {
            button = Integer.parseInt(a.button.split(" ")[0]);
        } catch (RuntimeException ignored) {  }
        ContainerInput input;
        try {
            input = ContainerInput.valueOf(a.containerInput);
        } catch (RuntimeException ignored) {
            input = ContainerInput.PICKUP;
        }
        int inputOrd = input.ordinal();
        java.util.List<Integer> visibleSlots = a.resolvedSlots();
        int repeat = Math.max(1, a.repeatCount);
        synchronized (menuLock) {
            int size = menuSlots.size();
            for (int r = 0; r < repeat && plan.size() < ITEM_PLAN_CAP; r++) {
                for (int visible : visibleSlots) {
                    if (plan.size() >= ITEM_PLAN_CAP) break;
                    int handler = visibleToHandler(visible);
                    if (handler >= 0 && handler < size) plan.add(new int[]{handler, button, inputOrd});
                }
            }
        }
        return plan;
    }

    public String runClientAction(String name, String args) {
        if (status.get() != Status.READY) return "Session is not ready";
        String rest = args == null ? "" : args.trim();
        String[] parts = rest.isEmpty() ? new String[0] : rest.split("\\s+");
        switch (name) {
            case "click-slot": {
                if (parts.length < 1) return "Usage: click-slot <slot> [mode] [count]";
                int visible = parseVisibleSlot(parts[0]);
                if (visible < 0) return "Bad slot: " + parts[0];
                int handler = visibleToHandler(visible);
                if (handler < 0) return "Slot not available in this menu";
                MultiClientCommands.ClickSpec spec = MultiClientCommands.parseClick(parts.length > 1 ? parts[1] : "left");
                if (spec == null) return "Bad click mode";
                return repeatClick(handler, spec, parts.length > 2 ? parts[2] : null);
            }
            case "click-item": {
                if (rest.isEmpty()) return "Usage: click-item <name> [mode] [count]";
                String[] split = splitQuotedFirst(rest);
                String[] tail = split[1].isEmpty() ? new String[0] : split[1].split("\\s+");
                MultiClientCommands.ClickSpec spec = MultiClientCommands.parseClick(tail.length > 0 ? tail[0] : "left");
                if (spec == null) return "Bad click mode";
                int slot = findSlotByItem(split[0]);
                if (slot < 0) return "Item not found in the open menu";
                return repeatClick(slot, spec, tail.length > 1 ? tail[1] : null);
            }
            case "change-slot": {
                Integer n = parts.length > 0 ? parseIntOrNull(parts[0]) : null;
                if (n == null || n < 1 || n > 9) return "Usage: change-slot <1-9>";
                return selectHotbar(n - 1);
            }
            case "drop": {
                if (parts.length == 0) return "Usage: drop <hand|fullinventory|<amount> [item]|<item>>";
                String first = parts[0].toLowerCase(Locale.ROOT);
                if (first.equals("hand")) return dropSelected(true);
                if (first.equals("fullinventory") || first.equals("all") || first.equals("inv") || first.equals("inventory")) {
                    return dropFullInventory();
                }
                Integer amount = parseIntOrNull(parts[0]);
                if (amount != null) {
                    if (parts.length >= 2) {
                        int slot = findSlotByItem(splitQuotedFirst(rest.substring(parts[0].length()).trim())[0]);
                        return slot < 0 ? "Item not found in the open menu" : throwFromHandler(slot, amount);
                    }
                    return throwFromHandler(heldHandlerSlot(), amount);
                }
                int slot = findSlotByItem(rest);
                if (slot < 0) return "Item not found in the open menu";
                return clickSlot(slot, 1, ContainerInput.THROW);
            }
            case "close": return closeContainer();
            case "close-silent": return closeSilent();
            case "use": return useItem();
            case "swing": return swingArm();
            case "say": return rest.isEmpty() ? "Usage: say <message>" : sendConsoleLine(rest);
            case "damage": {
                Integer n = parts.length > 0 ? parseIntOrNull(parts[0]) : null;
                return n == null ? "Usage: damage <amount>" : sendConsoleLine("/damage @s " + n);
            }
            case "send": {
                if (parts.length < 1) return "Usage: send <PacketClass> [args]";
                Class<? extends Packet<?>> cls = AutismPacketRegistry.getPacket(parts[0]);
                if (cls == null) return "Unknown packet: " + parts[0];
                return sendManual(cls, rest.substring(parts[0].length()).trim());
            }
            default: return "unknown or unsupported client command";
        }
    }

    private String repeatClick(int handlerSlot, MultiClientCommands.ClickSpec spec, String countArg) {
        int count = 1;
        if (countArg != null) {
            Integer parsed = parseIntOrNull(countArg);
            if (parsed == null) return "Bad count";
            count = Math.max(1, Math.min(64, parsed));
        }
        String last = "Sent";
        for (int i = 0; i < count; i++) {
            last = clickSlot(handlerSlot, spec.button(), spec.input());
            if (!"Sent".equals(last)) break;
        }
        return last;
    }

    private static Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static int parseVisibleSlot(String token) {
        if (token == null) return -1;
        String t = token.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "boots": return 36;
            case "leggings": return 37;
            case "chestplate": return 38;
            case "helmet": return 39;
            case "offhand": return 40;
            default:
        }
        int indexed = parseIndexed(t, "hotbar", 1, 9, 0);
        if (indexed >= 0) return indexed;
        indexed = parseIndexed(t, "inventory", 1, 27, 9);
        if (indexed >= 0) return indexed;
        indexed = parseIndexed(t, "gui", 1, 999, 100);
        if (indexed >= 0) return indexed;
        Integer numeric = parseIntOrNull(t);
        return numeric != null && numeric >= 0 ? numeric : -1;
    }

    private static int parseIndexed(String token, String prefix, int min, int max, int base) {
        if (!token.startsWith(prefix)) return -1;
        Integer n = parseIntOrNull(token.substring(prefix.length()));
        return n != null && n >= min && n <= max ? base + (n - min) : -1;
    }

    private static String[] splitQuotedFirst(String input) {
        String s = input == null ? "" : input.trim();
        if (s.startsWith("\"")) {
            int end = s.indexOf('"', 1);
            if (end > 0) return new String[]{s.substring(1, end), s.substring(end + 1).trim()};
            return new String[]{s.substring(1), ""};
        }
        int space = s.indexOf(' ');
        return space < 0 ? new String[]{s, ""} : new String[]{s.substring(0, space), s.substring(space + 1).trim()};
    }

    private String throwFromHandler(int handler, int count) {
        if (handler < 0) return "Bad slot";
        if (!reserveUserSend()) return "Rate limited";
        int n = Math.max(1, Math.min(64, count));
        for (int i = 0; i < n; i++) {
            if (!clickSlotRaw(handler, 0, ContainerInput.THROW)) return "Blocked by packet policy";
        }
        return "Sent";
    }

    public String sendImmediateMovement() {
        String precheck = movementPrecheck(status.get(), hasPosition);
        if (!precheck.isBlank()) return precheck;
        if (!reserveUserSend()) return "Rate limited";
        return sendPosition(false) ? "Sent" : "Blocked by packet policy";
    }

    public String sendImmediateMoveLook() {
        return sendImmediateMovement();
    }

    static String movementPrecheck(Status status, boolean hasPosition) {
        if (status != Status.READY) return "Session is not ready";
        if (!hasPosition) return "Position is not ready";
        return "";
    }

    private synchronized boolean reserveUserSend() {
        long now = System.currentTimeMillis();
        if (now - userSendWindowAt >= 1000L) {
            userSendWindowAt = now;
            userSendsInWindow = 0;
        }
        if (userSendsInWindow >= 20) return false;
        userSendsInWindow++;
        return true;
    }

    void applyPolicy(MultiPacketPolicy updated) {
        if (updated != null) policy = new MultiPacketPolicy(updated);
    }

    void startAssignedMacro(AutismMacro macro) {
        macroStartRequest = macro;
        macroStopRequest = false;
        loginMacroRun = false;
    }

    void startLoginMacro(AutismMacro macro) {
        macroStartRequest = macro;
        macroStopRequest = false;
        loginMacroRun = true;
        loginMacroDeadline = System.currentTimeMillis() + LOGIN_WINDOW_MS;
    }

    void stopMacro() {
        macroStopRequest = true;
        macroStartRequest = null;
        loginMacroRun = false;
    }

    boolean isMacroRunning() {
        return macroRun != null;
    }

    public String currentMacroName() {
        MultiMacroRun run = macroRun;
        return run == null ? "" : run.macroName();
    }

    private void driveMacro(long now) {
        if (macroStopRequest) {
            macroStopRequest = false;
            macroRun = null;
            macroStatus = "";
            macroProgress = MacroProgress.idle();
            resetMacroInputs();
        }
        AutismMacro start = macroStartRequest;
        if (start != null) {
            macroStartRequest = null;
            macroRun = start.actions.isEmpty() ? null : new MultiMacroRun(start);
            macroStatus = macroRun == null ? "" : macroRun.status();
            macroProgress = macroRun == null ? MacroProgress.idle() : macroRun.progress();
        }
        MultiMacroRun run = macroRun;
        if (run == null) return;
        try {
            run.step(now, this);
        } catch (RuntimeException error) {
            macroRun = null;
            macroStatus = "error";
            macroProgress = new MacroProgress(run.macroName(), false, run.stepIndex(), run.totalSteps(),
                run.loopNumber(), MultiManager.singleLine("Error: " + shortError(error), 64));
            macroFinishNote.set(run.macroName() + Character.toString(0) + "error");
            loginMacroRun = false;
            resetMacroInputs();
            return;
        }
        if (run.done()) {
            macroFinishNote.set(run.macroName() + '\0' + run.status());
            macroRun = null;
            loginMacroRun = false;
            macroStatus = "";
            macroProgress = new MacroProgress(run.macroName(), false, run.totalSteps(), run.totalSteps(),
                run.loopNumber(), MultiManager.singleLine(run.status(), 64));
            resetMacroInputs();
        } else {
            macroStatus = run.status();
            macroProgress = run.progress();
        }
    }

    String pollMacroFinish() {
        return macroFinishNote.getAndSet(null);
    }

    private void resetMacroInputs() {
        disarmPacketCapture();
        cancelXCarry();
        if (inputShift || inputSprint) {
            inputShift = false;
            inputSprint = false;
            send(new ServerboundPlayerInputPacket(Input.EMPTY), false, false);
        }
    }

    @Override public boolean macroReady() { return status.get() == Status.READY && !closed.get(); }
    @Override public void macroNote(String note) { if (note != null && !note.isBlank()) appendLocal(note); }
    @Override public boolean customMenuPhaseActive() {
        Status current = status.get();
        return !closed.get() && (current == Status.CONFIGURING || current == Status.JOINED || current == Status.READY);
    }
    @Override public boolean fullMode() { return true; }
    @Override public CustomMenuSnapshot customMenu() { return customMenus.current(); }

    @Override
    public CustomMenuSubmitResult submitCustomMenu(CustomMenuSnapshot snapshot, CustomMenuSubmission submission) {
        CustomMenuSubmitResult result = CustomMenuAdapterRegistry.submit(snapshot, submission);
        if (!result.success()) return result;
        for (Packet<?> packet : result.packets()) {
            if (!send(packet, true, false)) return CustomMenuSubmitResult.failure("Connection closed during submission");
        }
        customMenus.consume(snapshot, result.replacement());
        updateCustomMenuTitle(snapshot, customMenus.current());
        return result;
    }

    @Override
    public String resolveCustomMenuValue(String template, Map<String, String> macroVariables) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> entry : formValues.entrySet()) {
            out = out.replace("{secret." + entry.getKey() + "}", entry.getValue());
        }
        if (out.matches("(?s).*\\{secret\\.[^}]+}.*")) return null;
        String username = identity == null || identity.user() == null ? "" : identity.user().getName();
        out = out.replace("{username}", username)
            .replace("{account_id}", spec.accountId())
            .replace("{profile_name}", profileName);
        if (macroVariables != null) {
            for (Map.Entry<String, String> entry : macroVariables.entrySet()) {
                out = out.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return out;
    }
    @Override public String botUsername() {
        return identity == null || identity.user() == null ? "" : identity.user().getName();
    }
    @Override public String botUuid() {
        return identity == null || identity.user() == null ? "" : identity.user().getProfileId().toString();
    }
    @Override public String serverAddress() { return serverAddress; }
    @Override public String macroPassword() { return loginMacroRun ? loginPassword() : ""; }
    @Override public float health() { return health; }
    @Override public float maxHealth() { return maxHealth; }
    @Override public int food() { return food; }
    @Override public boolean hasPosition() { return hasPosition; }
    @Override public double posX() { return position.position().x; }
    @Override public double posY() { return position.position().y; }
    @Override public double posZ() { return position.position().z; }
    @Override public String dimension() { return dimension; }
    @Override public String heldItemName() { return heldItemName; }
    @Override public int selectedHotbar() { return selectedHotbar; }
    @Override public String openScreenTitle() { return openScreenTitle; }
    @Override public boolean containerOpen() { return openContainerId >= 0 && !containerDismissed; }
    @Override public long guiOpenSeq() { return openScreenSeq; }

    @Override
    public int countItem(String query) {
        String q = normalizeItemQuery(query);
        int total = 0;
        synchronized (menuLock) {
            for (ItemStack stack : playerInv) {
                if (stack == null || stack.isEmpty()) continue;
                if (q.isEmpty() || itemMatches(stack, q)) total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public int freeSlots() {
        int free = 0;
        synchronized (menuLock) {
            for (int h = 9; h <= 44; h++) {
                ItemStack stack = h < playerInv.size() ? playerInv.get(h) : ItemStack.EMPTY;
                if (stack == null || stack.isEmpty()) free++;
            }
        }
        return free;
    }

    @Override
    public boolean slotFilled(int visibleSlot) {
        int handler = visibleToHandler(visibleSlot);
        if (handler < 0) return false;
        synchronized (menuLock) {
            ItemStack stack = handler < menuSlots.size() ? menuSlots.get(handler) : ItemStack.EMPTY;
            return stack != null && !stack.isEmpty();
        }
    }

    @Override public boolean cursorEmpty() { ItemStack c = carried; return c == null || c.isEmpty(); }
    @Override public String cursorName() { ItemStack c = carried; return c == null || c.isEmpty() ? "" : c.getHoverName().getString(); }

    @Override
    public int countItemTarget(ItemTarget target) {
        if (target == null || (!target.hasIdentity() && !target.hasSlot())) return 0;
        int total = 0;
        synchronized (menuLock) {
            for (int h = 9; h <= 44 && h < playerInv.size(); h++) {
                ItemStack stack = playerInv.get(h);
                if (stack == null || stack.isEmpty()) continue;
                int invIndex = h >= 36 ? h - 36 : h;
                if (target.score(stack, invIndex) >= 0) total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean cursorMatches(ItemTarget target) {
        if (target == null) return false;
        ItemStack c = carried;
        return c != null && !c.isEmpty() && target.score(c, -1) >= 0;
    }

    @Override
    public float currentPitch() {
        PositionMoveRotation p = position;
        return p == null ? 0f : p.xRot();
    }

    @Override
    public int[] heldDurability() {
        return durabilityAtHandler(36 + Math.max(0, Math.min(8, selectedHotbar)));
    }

    @Override
    public int[] durabilityAtInv(int inventoryIndex) {
        return durabilityAtHandler(invToHandler(inventoryIndex));
    }

    @Override
    public int[] itemDurability(ItemTarget target) {
        if (target == null) return null;
        ItemTarget itemOnly = target.copy();
        if (itemOnly.hasIdentity()) itemOnly.slot = -1;
        synchronized (menuLock) {
            for (int h = 9; h <= 45 && h < playerInv.size(); h++) {
                ItemStack stack = playerInv.get(h);
                if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) continue;
                int invIndex = h == 45 ? 40 : (h >= 36 ? h - 36 : h);
                if (itemOnly.score(stack, invIndex) >= 0) return new int[]{stack.getDamageValue(), stack.getMaxDamage()};
            }
        }
        return null;
    }

    private int[] durabilityAtHandler(int handler) {
        synchronized (menuLock) {
            if (handler < 0 || handler >= playerInv.size()) return null;
            ItemStack s = playerInv.get(handler);
            if (s == null || s.isEmpty() || !s.isDamageableItem()) return null;
            return new int[]{s.getDamageValue(), s.getMaxDamage()};
        }
    }

    private static int invToHandler(int inv) {
        if (inv >= 0 && inv <= 8) return 36 + inv;
        if (inv >= 9 && inv <= 35) return inv;
        if (inv >= 36 && inv <= 39) return 44 - inv;
        if (inv == 40) return 45;
        return -1;
    }

    @Override public long teleportSeq() { return teleportSeq; }
    @Override public long containerRevision() { return containerStateId; }

    private void trackBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (pos == null || state == null) return;
        long key = pos.asLong();
        if (blockUpdates.size() >= BLOCK_UPDATE_CAP && !blockUpdates.containsKey(key)) blockUpdates.clear();
        blockUpdates.put(key, state);
    }

    @Override
    public boolean blockAt(int x, int y, int z, java.util.List<String> ids, boolean anyBlock, boolean wantDestroyed) {
        net.minecraft.world.level.block.state.BlockState st = blockUpdates.get(BlockPos.asLong(x, y, z));
        if (st == null) return false;
        if (wantDestroyed) return st.isAir();
        if (st.isAir()) return false;
        if (anyBlock || ids == null || ids.isEmpty()) return true;
        Identifier id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        String cur = id == null ? "" : id.toString();
        for (String want : ids) {
            if (want == null || want.isBlank()) continue;
            if (soundIdMatches(want, cur)) return true;
        }
        return false;
    }

    @Override
    public void sendRawPayload(String channel, String rawData) {
        if (channel == null || channel.isBlank()) return;
        try {
            byte[] bytes = AutismPayloadSupport.parsePayloadBytes(rawData);
            send(AutismPayloadSupport.createC2SPacket(channel.trim(), bytes), false, false);
        } catch (RuntimeException ignored) {

        }
    }

    @Override
    public String[] slotChangeBaseline(WaitForSlotChangeAction action) {
        String[] base = new String[action.entries.size()];
        synchronized (menuLock) {
            int size = menuSlots.size();
            for (int i = 0; i < action.entries.size(); i++) {
                WaitForSlotChangeAction.WaitEntry e = action.entries.get(i);
                ItemTarget t = e.resolvedTarget();
                if (e.waitMode != WaitForSlotChangeAction.WaitMode.ANY_CHANGE || t == null || !t.hasSlot()) {
                    base[i] = "";
                    continue;
                }
                int handlerSlot = visibleToHandler(t.slot);
                ItemStack s = handlerSlot >= 0 && handlerSlot < size ? menuSlots.get(handlerSlot) : ItemStack.EMPTY;
                base[i] = snapshotStackIdentity(s);
            }
        }
        return base;
    }

    @Override
    public boolean slotChangeMet(WaitForSlotChangeAction action, String[] baseline) {
        if (action.entries.isEmpty()) return true;
        synchronized (menuLock) {
            int size = menuSlots.size();
            for (int i = 0; i < action.entries.size(); i++) {
                if (!slotChangeEntryMet(action.entries.get(i), i, baseline, size)) return false;
            }
        }
        return true;
    }

    private boolean slotChangeEntryMet(WaitForSlotChangeAction.WaitEntry e, int idx, String[] baseline, int size) {
        ItemTarget target = e.resolvedTarget();
        if (target == null) target = new ItemTarget();
        if (target.hasSlot()) {
            int handlerSlot = visibleToHandler(target.slot);
            if (handlerSlot < 0 || handlerSlot >= size) return e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY;
            return checkSlotStack(menuSlots.get(handlerSlot), target, target.slot, e, idx, baseline);
        }
        if (e.waitMode == WaitForSlotChangeAction.WaitMode.IS_EMPTY) {
            for (int h = 0; h < size; h++) {
                ItemStack s = menuSlots.get(h);
                if (s == null || s.isEmpty()) continue;
                int vis = handlerToVisible(h, size);
                if (!target.hasIdentity() || target.matches(s, vis)) return false;
            }
            return true;
        }
        for (int h = 0; h < size; h++) {
            ItemStack s = menuSlots.get(h);
            if (s == null || s.isEmpty()) continue;
            int vis = handlerToVisible(h, size);
            if (target.hasIdentity() && !target.matches(s, vis)) continue;
            if (checkSlotStack(s, target, vis, e, idx, baseline)) return true;
        }
        return false;
    }

    private boolean checkSlotStack(ItemStack stack, ItemTarget target, int visibleSlot,
                                   WaitForSlotChangeAction.WaitEntry e, int idx, String[] baseline) {
        boolean empty = stack == null || stack.isEmpty();
        boolean nameMatches = target == null || !target.hasIdentity() || (!empty && target.matches(stack, visibleSlot));
        return switch (e.waitMode) {
            case NOT_EMPTY -> !empty && nameMatches;
            case IS_EMPTY -> empty;
            case COUNT_AT_LEAST -> !empty && nameMatches && stack.getCount() >= e.targetCount;
            case COUNT_BELOW -> empty || (nameMatches && stack.getCount() < e.targetCount);
            case ANY_CHANGE -> {
                String init = baseline != null && idx < baseline.length && baseline[idx] != null ? baseline[idx] : "";
                yield !snapshotStackIdentity(stack).equals(init);
            }
        };
    }

    private static String snapshotStackIdentity(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String rich = autismclient.util.macro.MacroExecutor.serializeTextComponent(stack.getHoverName());
        String components = String.valueOf(stack.getComponents());
        return (id == null ? "" : id.toString()) + "|" + stack.getCount() + "|" + (rich == null ? "" : rich)
            + "|" + components.hashCode();
    }

    private static int gameTypeIdOf(CommonPlayerSpawnInfo info) {
        try {
            return info == null || info.gameType() == null ? -1 : info.gameType().getId();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private void captureChat(Component c) {
        if (c != null) captureChatText(c.getString());
    }

    private void captureChatText(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (chatLogLock) {
            recentChat.addLast(text);
            while (recentChat.size() > CHAT_LOG_CAP) recentChat.removeFirst();
            chatSeqCounter++;
        }
    }

    @Override public int gameMode() { return gameModeId; }
    @Override public long chatSeq() { return chatSeqCounter; }

    @Override
    public java.util.List<String> chatSince(long baselineSeq) {
        synchronized (chatLogLock) {
            long missed = chatSeqCounter - baselineSeq;
            int take = (int) Math.min(Math.max(0, missed), recentChat.size());
            if (take <= 0) return java.util.List.of();
            java.util.List<String> out = new ArrayList<>(take);
            int skip = recentChat.size() - take;
            int i = 0;
            for (String line : recentChat) {
                if (i++ < skip) continue;
                out.add(line);
            }
            return out;
        }
    }

    @Override
    public boolean entityWithin(java.util.List<String> typeRefs, boolean containerOnly, boolean centerOnPlayer,
                                double cx, double cy, double cz, double radius) {
        Vec3 base = position.position();
        double x = centerOnPlayer ? base.x : cx;
        double y = centerOnPlayer ? base.y : cy;
        double z = centerOnPlayer ? base.z : cz;
        return entities.present(typeRefs, containerOnly, x, y, z, radius);
    }

    private void recordPacket(boolean c2s, Packet<?> packet) {
        synchronized (packetLogLock) {
            packetLog.addLast(new PktRec(++packetSeqCounter, c2s, packet));
            while (packetLog.size() > PACKET_LOG_CAP) packetLog.removeFirst();
        }
    }

    @Override
    public void setPacketCapture(boolean on) {
        packetCaptureArmed = on;
        if (!on) {
            synchronized (packetLogLock) {
                packetLog.clear();
            }
        }
    }

    @Override public long packetSeq() { return packetSeqCounter; }

    @Override
    public boolean packetSeen(long baselineSeq, java.util.List<String> targets) {
        synchronized (packetLogLock) {
            for (PktRec r : packetLog) {
                if (r.seq() <= baselineSeq) continue;
                if (targets == null || targets.isEmpty()) return true;
                String simple = r.packet().getClass().getSimpleName();
                for (String target : targets) {
                    String dir = WaitForPacketAction.getDirection(target);
                    if (!dir.isEmpty() && dir.equalsIgnoreCase("C2S") != r.c2s()) continue;
                    if (WaitForPacketAction.getPacketName(target).equalsIgnoreCase(simple)) return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean packetMatched(long baselineSeq, autismclient.util.macro.WaitPacketMatchAction action) {
        synchronized (packetLogLock) {
            for (PktRec r : packetLog) {
                if (r.seq() <= baselineSeq) continue;
                try {
                    if (action.matches(r.packet(), r.c2s() ? "C2S" : "S2C")) return true;
                } catch (RuntimeException ignored) {  }
            }
        }
        return false;
    }

    @Override
    public boolean itemOnCooldown(ItemTarget target, boolean mainHand) {

        if (target != null && target.hasIdentity()) {
            ItemStack stack = firstMatchingStack(target);

            if (stack == null || stack.isEmpty()) return true;
            return onCooldown(stack);
        }

        ItemStack held = heldStack(mainHand);
        if (held == null || held.isEmpty()) return false;
        return onCooldown(held);
    }

    private boolean onCooldown(ItemStack stack) {
        Long expiry = cooldownExpiry.get(cooldownGroupOf(stack));
        return expiry != null && expiry > System.currentTimeMillis();
    }

    private ItemStack heldStack(boolean mainHand) {
        synchronized (menuLock) {
            int h = mainHand ? 36 + Math.max(0, Math.min(8, selectedHotbar)) : 45;
            return h >= 0 && h < playerInv.size() ? playerInv.get(h) : ItemStack.EMPTY;
        }
    }

    private ItemStack firstMatchingStack(ItemTarget target) {
        ItemTarget itemOnly = target.copy();
        if (itemOnly.hasIdentity()) itemOnly.slot = -1;
        synchronized (menuLock) {
            for (int h = 9; h <= 45 && h < playerInv.size(); h++) {
                ItemStack s = playerInv.get(h);
                if (s == null || s.isEmpty()) continue;
                int invIndex = h == 45 ? 40 : (h >= 36 ? h - 36 : h);
                if (itemOnly.score(s, invIndex) >= 0) return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static String cooldownGroupOf(ItemStack stack) {
        UseCooldown uc = stack.get(DataComponents.USE_COOLDOWN);
        if (uc != null && uc.cooldownGroup().isPresent()) return uc.cooldownGroup().get().toString();
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    @Override
    public String captureItemText(CaptureValueAction a, ItemTarget filter) {
        ItemStack stack = resolveCaptureStack(a, filter);
        if (stack == null || stack.isEmpty()) return null;
        return switch (a.itemText == null ? CaptureValueAction.ItemText.NAME : a.itemText) {
            case ID -> {
                Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                yield id == null ? "" : id.toString();
            }
            case LORE -> loreText(stack);
            default -> stack.getHoverName().getString();
        };
    }

    private ItemStack resolveCaptureStack(CaptureValueAction a, ItemTarget filter) {
        synchronized (menuLock) {
            return switch (a.source) {
                case CURSOR_ITEM -> carried;
                case HELD_ITEM -> {
                    int h = 36 + Math.max(0, Math.min(8, selectedHotbar));
                    yield h < playerInv.size() ? playerInv.get(h) : ItemStack.EMPTY;
                }
                case GUI_ITEM -> findCaptureStack(filter, a.slot, true);
                case PLAYER_ITEM -> findCaptureStack(filter, a.slot, false);
                default -> null;
            };
        }
    }

    private ItemStack findCaptureStack(ItemTarget filter, int slot, boolean guiRegion) {
        int size = menuSlots.size();
        int base = openContainerId >= 0 ? size - 36 : 0;
        for (int h = 0; h < size; h++) {
            ItemStack s = menuSlots.get(h);
            if (s == null || s.isEmpty()) continue;
            boolean playerSlot = openContainerId < 0 || h >= base;
            if (guiRegion == playerSlot) continue;
            int visible = handlerToVisible(h, size);
            if (slot >= 0 && slot != visible && slot != h) continue;
            if (filter != null && filter.hasIdentity() && filter.score(s, visible) < 0) continue;
            return s;
        }
        return ItemStack.EMPTY;
    }

    private static String loreText(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Component line : lore.lines()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(line.getString());
        }
        return sb.toString();
    }

    @Override
    public List<String> tablistNames(boolean excludeSelf) {
        UUID self = identity == null || identity.user() == null ? null : identity.user().getProfileId();
        List<String> names = new ArrayList<>(listedPlayers.size());
        for (UUID profileId : listedPlayers) {
            if (excludeSelf && self != null && self.equals(profileId)) continue;
            String name = playerNames.get(profileId);
            if (name != null && !name.isBlank() && !names.contains(name)) names.add(name);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    @Override
    public int requestCommandSuggestions(String command) {
        String query = command == null ? "" : command;
        return requestSuggestions(query);
    }

    @Override
    public List<String> commandSuggestions(int requestId) {
        Suggest suggest;
        synchronized (suggestionLock) {
            suggest = suggestionReplies.remove(requestId);
        }
        return suggest == null ? null : List.copyOf(suggest.entries());
    }

    @Override
    public List<CaptureValueAction.ScoreboardLine> scoreboardLines() {
        synchronized (scoreboardLock) {
            String objective = displayedSidebarObjective();
            if (objective == null || objective.isBlank()) return List.of();
            Map<String, TrackedScore> tracked = scoresByObjective.get(objective);
            if (tracked == null || tracked.isEmpty()) return List.of();
            ScoreObjective objectiveData = scoreObjectives.getOrDefault(
                objective, new ScoreObjective(objective, Optional.empty()));
            List<Map.Entry<String, TrackedScore>> entries = new ArrayList<>(tracked.entrySet());
            entries.removeIf(entry -> entry.getKey().startsWith("#"));
            entries.sort((a, b) -> {
                int score = Integer.compare(b.getValue().score(), a.getValue().score());
                return score != 0 ? score : a.getKey().compareToIgnoreCase(b.getKey());
            });
            List<CaptureValueAction.ScoreboardLine> lines = new ArrayList<>(Math.min(15, entries.size()));
            for (int i = 0; i < entries.size() && i < 15; i++) {
                Map.Entry<String, TrackedScore> entry = entries.get(i);
                String owner = entry.getKey();
                TrackedScore score = entry.getValue();
                String baseName = score.display() == null || score.display().isBlank() ? owner : score.display();
                ScoreTeam team = scoreTeams.get(scoreOwnerTeams.get(owner));
                String name = team == null ? baseName : team.prefix() + baseName + team.suffix();
                NumberFormat format = score.numberFormat().orElseGet(() ->
                    objectiveData.numberFormat().orElse(StyledFormat.SIDEBAR_DEFAULT));
                String scoreText = componentText(format.format(score.score()));
                lines.add(new CaptureValueAction.ScoreboardLine(objective + "\u001F" + owner, i, objective,
                    objectiveData.title(), owner, name, scoreText,
                    scoreText.isEmpty() ? name : name + ": " + scoreText));
            }
            return List.copyOf(lines);
        }
    }

    private String displayedSidebarObjective() {
        String selfName = identity == null || identity.user() == null ? "" : identity.user().getName();
        ScoreTeam team = scoreTeams.get(scoreOwnerTeams.get(selfName));
        if (team != null && team.color().isPresent()) {
            String teamObjective = displayedObjectives.get(team.color().get().displaySlot());
            if (teamObjective != null && !teamObjective.isBlank()) return teamObjective;
        }
        return displayedObjectives.getOrDefault(DisplaySlot.SIDEBAR, "");
    }

    @Override
    public boolean macroStepMet(WaitForMacroStepAction action) {
        return sink.macroStepMet(this, action);
    }

    private void disarmPacketCapture() {
        if (packetCaptureArmed) setPacketCapture(false);
        if (soundCaptureArmed) setSoundCapture(false);
    }

    @Override
    public boolean editSign(SignEditAction a, String l1, String l2, String l3, String l4) {
        if (a == null) return false;
        BlockPos pos;
        boolean front;
        switch (a.targetMode) {
            case MANUAL_POS -> { pos = new BlockPos(a.x, a.y, a.z); front = a.frontText; }
            case LAST_INTERACTED_BLOCK -> { pos = lastInteractBlock; front = a.frontText; }
            default -> { pos = signEditorPos; front = signEditorFront; }
        }
        if (pos == null) return false;
        boolean sent = send(new ServerboundSignUpdatePacket(pos, front, l1, l2, l3, l4), false, false);
        SignEditAction.CloseMode mode = a.closeMode == null ? SignEditAction.CloseMode.STAY_OPEN : a.closeMode;
        if (mode == SignEditAction.CloseMode.SEND_CLOSE_PACKET_ONLY) {
            send(new ServerboundContainerClosePacket(a.closePacketContainerId), false, false);
        } else if (mode == SignEditAction.CloseMode.CLOSE_WITH_PACKET) {
            send(new ServerboundContainerClosePacket(openContainerId >= 0 ? openContainerId : 0), false, false);
        }
        return sent;
    }

    private void recordSound(String id, double x, double y, double z) {
        synchronized (soundLogLock) {
            soundLog.addLast(new SndRec(++soundSeqCounter, id == null ? "" : id, x, y, z));
            while (soundLog.size() > SOUND_LOG_CAP) soundLog.removeFirst();
        }
    }

    @Override
    public void setSoundCapture(boolean on) {
        soundCaptureArmed = on;
        if (!on) {
            synchronized (soundLogLock) {
                soundLog.clear();
            }
        }
    }

    @Override public long soundSeq() { return soundSeqCounter; }

    @Override
    public boolean soundMatched(long baselineSeq, java.util.List<String> ids, boolean checkDistance, double maxDistance) {
        Vec3 self = position.position();
        double maxSq = maxDistance * maxDistance;
        synchronized (soundLogLock) {
            for (SndRec r : soundLog) {
                if (r.seq() <= baselineSeq) continue;
                if (checkDistance) {
                    double dx = r.x() - self.x, dy = r.y() - self.y, dz = r.z() - self.z;
                    if (dx * dx + dy * dy + dz * dz > maxSq) continue;
                }
                if (ids == null || ids.isEmpty()) return true;
                for (String want : ids) {
                    if (want == null || want.isBlank()) continue;
                    if (soundIdMatches(want, r.id())) return true;
                }
            }
        }
        return false;
    }

    private static boolean soundIdMatches(String want, String actual) {
        String w = want.trim().toLowerCase(Locale.ROOT);
        String a = actual == null ? "" : actual.trim().toLowerCase(Locale.ROOT);
        if (w.equals(a)) return true;

        String wp = w.contains(":") ? w.substring(w.indexOf(':') + 1) : w;
        String ap = a.contains(":") ? a.substring(a.indexOf(':') + 1) : a;
        return wp.equals(ap);
    }

    @Override public int nearestEntity(String type) { return entities.nearest(type, position.position()); }
    @Override public double[] entityPos(int entityId) { return entities.pos(entityId); }

    @Override
    public String runClient(String name, String args) {
        return runClientAction(name, args);
    }

    @Override
    public void useItemPhase(autismclient.util.macro.UseItemPhaseAction.Phase phase, boolean offhand) {
        InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        PositionMoveRotation current = position;
        switch (phase == null ? autismclient.util.macro.UseItemPhaseAction.Phase.USE_ONCE : phase) {
            case USE_ONCE, START_USE, USE_BLOCK ->
                send(new ServerboundUseItemPacket(hand, ++useSeq, current.yRot(), current.xRot()), false, false);
            case RELEASE_USE ->
                send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                    BlockPos.ZERO, Direction.DOWN), false, false);
            case SWING -> send(new ServerboundSwingPacket(hand), false, false);
        }
    }

    @Override
    public String chat(String message) {
        return sendConsoleLine(message);
    }

    @Override
    public void startSelfMacro(String macroName) {
        AutismMacro found = autismclient.util.AutismMacroManager.get().get(macroName);
        if (found != null) startAssignedMacro(found.deepCopy());
    }

    @Override public void stopSelfMacro() { stopMacro(); }
    @Override public void disconnectBot(String reason) { disconnect(reason == null ? "Macro" : reason); }

    @Override public float currentYaw() { return position.yRot(); }

    @Override
    public void look(float yaw, float pitch) {
        synchronized (positionLock) {
            position = position.withRotation(yaw, pitch);
        }
        send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, true, false), false, true);
    }

    @Override
    public void move(double worldDx, double worldDz, long durationMs) {
        walkX = worldDx;
        walkZ = worldDz;
        long now = System.currentTimeMillis();
        long until = now + Math.max(0, durationMs);
        walkUntil = until;

        walkGrounded = true;
        walkProbeAt = now + GROUND_PROBE_MS;
        rapidWalkCorrections = 0;
        lastWalkCorrectionAt = 0L;
        primeFallTick = false;
        moveActiveUntil = Math.max(moveActiveUntil, until);
    }

    private void trackWalkCorrection(long now) {
        rapidWalkCorrections = now - lastWalkCorrectionAt <= 150L ? rapidWalkCorrections + 1 : 1;
        lastWalkCorrectionAt = now;
        if (rapidWalkCorrections >= 8) {
            walkUntil = 0L;
            rapidWalkCorrections = 0;

            if (now - lastWalkBlockedNoteAt > WALK_BLOCKED_NOTE_COOLDOWN_MS) {
                lastWalkBlockedNoteAt = now;
                appendLocal("Walk blocked by terrain; move cancelled early. Clear the path or use a clip action.");
            }
        }
    }

    @Override
    public void clip(double dx, double dy, double dz, int segments, boolean onGround) {
        int count = Math.max(1, Math.min(64, segments));
        PositionMoveRotation start;
        synchronized (positionLock) {
            start = position;
        }
        Vec3 origin = start.position();
        Vec3 accepted = origin;
        for (int i = 1; i <= count; i++) {
            double progress = i / (double) count;
            Vec3 next = new Vec3(origin.x + dx * progress, origin.y + dy * progress, origin.z + dz * progress);
            if (!send(new ServerboundMovePlayerPacket.Pos(next.x, next.y, next.z, onGround, false), false, true)) break;
            accepted = next;
        }
        synchronized (positionLock) {
            position = new PositionMoveRotation(accepted, Vec3.ZERO, start.yRot(), start.xRot());
        }
        motionY = 0.0D;
        walkX = 0.0D;
        walkZ = 0.0D;
        walkUntil = 0L;
        walkGrounded = false;
        grounded = onGround;
        primeFallTick = false;
    }

    @Override public void setSneak(boolean on) { inputShift = on; sendInput(); }
    @Override public void setSprint(boolean on) { inputSprint = on; sendInput(); }

    private void sendInput() {
        send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, inputShift, inputSprint)), false, false);
    }

    @Override
    public void jump() {
        motionY = 0.42D;
        walkGrounded = false;
        grounded = false;
        primeFallTick = false;
        moveActiveUntil = Math.max(moveActiveUntil, System.currentTimeMillis() + 1200L);
    }

    @Override
    public String interactEntity(int entityId, boolean attack) {
        if (entityId < 0) return "No entity";
        if (!attack) {
            lastInteractEntityId = entityId;
            lastInteractBlock = null;
        }
        if (attack) {
            send(new ServerboundAttackPacket(entityId), false, false);
            send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
        } else {
            send(new ServerboundInteractPacket(entityId, InteractionHand.MAIN_HAND, Vec3.ZERO, false), false, false);
        }
        return "Sent";
    }

    @Override
    public String useOnBlock(int x, int y, int z, String face) {
        Direction dir = directionByName(face);
        BlockPos pos = new BlockPos(x, y, z);
        lastInteractBlock = pos;
        lastInteractEntityId = -1;
        Vec3 hitVec = new Vec3(x + 0.5 + dir.getStepX() * 0.5, y + 0.5 + dir.getStepY() * 0.5, z + 0.5 + dir.getStepZ() * 0.5);
        send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, new BlockHitResult(hitVec, dir, pos, false), ++useSeq), false, false);
        return "Sent";
    }

    @Override
    public String breakBlock(int x, int y, int z, String face) {
        Direction dir = directionByName(face);
        BlockPos pos = new BlockPos(x, y, z);

        send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, dir, ++useSeq), false, false);
        send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
        send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir, ++useSeq), false, false);
        return "Sent";
    }

    private static Direction directionByName(String name) {
        if (name != null) {
            try {
                return Direction.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {

            }
        }
        return Direction.UP;
    }

    private static String entityTypeKey(net.minecraft.world.entity.EntityType<?> type) {
        try {
            return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String normalizeItemQuery(String query) {
        if (query == null) return "";
        String q = query.trim().toLowerCase(Locale.ROOT);
        int colon = q.indexOf(':');
        if (colon >= 0) q = q.substring(colon + 1);
        return q.replace('_', ' ').trim();
    }

    private static boolean itemMatches(ItemStack stack, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;
        String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (name.contains(normalizedQuery)) return true;
        try {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT).replace('_', ' ');
            return id.contains(normalizedQuery);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    void tick(long now) {
        Status currentStatus = status.get();
        if (currentStatus == Status.DISCONNECTED || currentStatus == Status.FAILED) return;
        Connection current = connection;
        if (current != null) current.tick();
        driveMacro(now);
        captcha.tick(now);
        maybeAutoLogin(now);
        maybeAutoLoginChat(now);

        if (loginMacroRun && macroRun != null && now >= loginMacroDeadline) {
            stopMacro();
        }
        Status afterNetworkTick = status.get();
        if (afterNetworkTick != Status.QUEUED && afterNetworkTick != Status.READY
            && afterNetworkTick != Status.DISCONNECTED && afterNetworkTick != Status.FAILED
            && now - statusSince >= CONNECT_PHASE_TIMEOUT_MS
            && (macroRun == null || !macroRun.hasActiveCustomMenuDeadline(now))

            && !canAnswerLoginScreen()) {
            fail("Timed out during " + detail);
            return;
        }
        if (closed.get() || status.get() != Status.READY || !hasPosition) return;

        if (health <= 0.0F) {
            if (respawnAt == 0L) {
                respawnAt = now + RESPAWN_DELAY_MS;
            } else if (now >= respawnAt) {
                send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN), false, false);
                respawnAt = now + RESPAWN_DELAY_MS;
            }
            return;
        }
        respawnAt = 0L;
        MultiPacketPolicy activePolicy = policy;

        boolean walking = now < walkUntil;
        boolean moving = now < moveActiveUntil || walking;
        if (inVehicle) {
            streamVehicle();
        } else if (postVehicleFall) {

            if (now >= postVehicleFallUntil) {
                postVehicleFall = false;
                grounded = true;
            } else {
                grounded = false;
                sendPosition(false);
            }
        } else if (moving) {
            synchronized (positionLock) {
                if (walking) {
                    Vec3 wp = position.position();
                    position = new PositionMoveRotation(new Vec3(wp.x + walkX, wp.y, wp.z + walkZ),
                        position.deltaMovement(), position.yRot(), position.xRot());

                    if (walkGrounded && now >= walkProbeAt) walkGrounded = false;
                }

                if (walking && walkGrounded && motionY <= 0.0D) { motionY = 0.0D; grounded = true; }
                else if (primeFallTick) { primeFallTick = false; grounded = false; }
                else if (walking) { applyWalkGravity(); }
                else applyGravity();
            }
            sendPosition(false);
        } else if (shouldSendIdleHeartbeat(activePolicy.autoPosition(), lastMovementAt, now)) {
            sendPosition(false);
        }
        if (activePolicy.autoLook() && !activePolicy.autoPosition() && !moving
            && now - lastLookAt >= AUTO_AUX_INTERVAL_MS) {
            lastLookAt = now;
            PositionMoveRotation p = position;
            send(new ServerboundMovePlayerPacket.Rot(p.yRot(), p.xRot(), true, false), false, true);
        }
        if (activePolicy.autoSwing() && now - lastSwingAt >= AUTO_AUX_INTERVAL_MS) {
            lastSwingAt = now;
            send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND), false, false);
        }
    }

    private void applyWalkGravity() {
        double next = (motionY - GRAVITY) * DRAG;
        motionY = Math.max(TERMINAL_VELOCITY, next);
        Vec3 p = position.position();
        position = new PositionMoveRotation(new Vec3(p.x, p.y + motionY, p.z), position.deltaMovement(), position.yRot(), position.xRot());
        grounded = true;
    }

    private void applyGravity() {
        Vec3 p = position.position();

        double top = columnGroundTop(p.x, p.y, p.z);
        if (Double.isNaN(top)) {
            grounded = true;
            motionY = 0.0D;
            return;
        }
        double next = (motionY - GRAVITY) * DRAG;
        motionY = Math.max(TERMINAL_VELOCITY, next);
        double newY = p.y + motionY;
        if (newY <= top) {
            newY = top;
            motionY = 0.0D;
            grounded = true;
        } else {
            grounded = false;
        }
        position = new PositionMoveRotation(new Vec3(p.x, newY, p.z), position.deltaMovement(), position.yRot(), position.xRot());
    }

    private double columnGroundTop(double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        double best = Double.NaN;
        for (java.util.Map.Entry<Long, net.minecraft.world.level.block.state.BlockState> e : blockUpdates.entrySet()) {
            long key = e.getKey();
            if (BlockPos.getX(key) != bx || BlockPos.getZ(key) != bz) continue;
            net.minecraft.world.level.block.state.BlockState st = e.getValue();
            if (st == null || st.isAir()) continue;
            double h = collisionTopHeight(st);
            if (h <= 0.0D) continue;
            double topY = BlockPos.getY(key) + h;
            if (topY <= y + 0.5D && (Double.isNaN(best) || topY > best)) best = topY;
        }
        return best;
    }

    private static double collisionTopHeight(net.minecraft.world.level.block.state.BlockState st) {
        try {
            net.minecraft.world.phys.shapes.VoxelShape shape =
                st.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) return 0.0D;
            return shape.max(Direction.Axis.Y);
        } catch (Throwable t) {
            return 1.0D;
        }
    }

    private void handleSetPassengers(ClientboundSetPassengersPacket passengers) {
        int veh = passengers.getVehicle();
        boolean mountsUs = false;
        for (int p : passengers.getPassengers()) {
            if (p == playerEntityId) { mountsUs = true; break; }
        }
        if (mountsUs) {
            double[] vp = entities.pos(veh);
            synchronized (positionLock) {
                vehicleId = veh;
                inVehicle = true;
                if (vp != null) { vehicleX = vp[0]; vehicleY = vp[1]; vehicleZ = vp[2]; }
                else { Vec3 pp = position.position(); vehicleX = pp.x; vehicleY = pp.y; vehicleZ = pp.z; }
                vehicleFallMotion = 0.0D;
                vehicleYaw = position.yRot();
                grounded = false;
                primeFallTick = false;
                postVehicleFall = false;
            }
            moveActiveUntil = Math.max(moveActiveUntil, System.currentTimeMillis() + JOIN_MOVE_ACTIVE_MS);
        } else if (inVehicle && veh == vehicleId) {
            dismountVehicle();
        }
    }

    private void dismountVehicle() {
        if (!inVehicle) return;
        inVehicle = false;
        long now = System.currentTimeMillis();
        synchronized (positionLock) {
            grounded = false;
            motionY = 0.0D;
            primeFallTick = false;
            postVehicleFall = true;
        }
        postVehicleFallUntil = now + 1000L;
        moveActiveUntil = Math.max(moveActiveUntil, now + 2000L);
    }

    private void streamVehicle() {
        float yaw;
        float pitch;
        double vx;
        double vy;
        double vz;
        synchronized (positionLock) {
            vehicleFallMotion -= VEHICLE_GRAVITY;
            vehicleY += vehicleFallMotion;
            vehicleYaw += 3.0F;
            yaw = vehicleYaw;
            pitch = position.xRot();
            vx = vehicleX;
            vy = vehicleY;
            vz = vehicleZ;
        }
        send(new ServerboundPaddleBoatPacket(false, false), false, false);
        send(new ServerboundMoveVehiclePacket(new Vec3(vx, vy, vz), yaw, pitch, false), false, true);
        send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, false, false), false, true);
        send(ServerboundClientTickEndPacket.INSTANCE, true, false);
    }

    static boolean shouldSendIdleHeartbeat(boolean enabled, long lastMovementAt, long now) {
        return enabled && now - lastMovementAt >= IDLE_HEARTBEAT_MS;
    }

    private boolean sendPosition(boolean critical) {
        PositionMoveRotation current = position;
        if (send(new ServerboundMovePlayerPacket.PosRot(
            current.position(),
            current.yRot(),
            current.xRot(),
            grounded,
            false
        ), critical, true)) {

            send(ServerboundClientTickEndPacket.INSTANCE, true, false);
            lastMovementAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    private void sendChatAcknowledgement() {
        int offset;
        synchronized (chatStateLock) {
            offset = lastSeenMessages.getAndClearOffset();
        }
        if (offset > 0) send(new ServerboundChatAckPacket(offset), true, false);
    }

    private boolean send(Packet<?> packet, boolean critical, boolean movementPacket) {
        Connection current = connection;
        if (packet == null || current == null || closed.get()) return false;
        if (packet instanceof ServerboundContainerClosePacket close && close.getContainerId() == 0 && xCarryForced) {
            refreshXCarryActive();
            if (xCarryActive) {
                return true;
            }
        }
        if (!policy.allows(MultiPacketPolicy.Direction.C2S, packet.getClass().getName(), critical, movementPacket)) return false;
        if (packetCaptureArmed) recordPacket(true, packet);
        current.send(packet);
        return true;
    }

    public void disconnect(String reason) {
        String message = reason == null ? "Disconnected" : reason;
        if (!closed.compareAndSet(false, true)) {

            setStatus(Status.DISCONNECTED, message);
            return;
        }
        Connection current = connection;
        if (current != null) {
            current.disconnect(net.minecraft.network.chat.Component.literal(message));
            current.handleDisconnection();
            MultiConnectionContext.remove(current);
        }
        setStatus(Status.DISCONNECTED, message);
    }

    void failExternal(String reason) {
        fail(reason == null ? "Connection failed" : reason);
    }

    private void handleDisconnected(DisconnectionDetails details, int epoch) {

        if (epoch != connectEpoch) return;
        MultiConnectionContext.remove(connection);
        if (!closed.compareAndSet(false, true)) return;
        String reason = details == null ? "Disconnected" : details.reason().getString();
        setStatus(Status.DISCONNECTED, reason);
    }

    private void fail(String message) {
        if (!closed.compareAndSet(false, true)) return;
        Connection current = connection;
        if (current != null) {
            current.disconnect(net.minecraft.network.chat.Component.literal(message));
            MultiConnectionContext.remove(current);
        }
        setStatus(Status.FAILED, message);
        if (current != null) current.handleDisconnection();
    }

    private void setStatus(Status value, String text) {
        if (closed.get() && value != Status.DISCONNECTED && value != Status.FAILED) return;
        String updatedDetail = text == null ? value.name() : text;
        Status previous = status.get();
        String previousDetail = detail;
        detail = updatedDetail;
        if (previous != value) statusSince = System.currentTimeMillis();

        status.set(value);
        if (previous != value || !java.util.Objects.equals(previousDetail, updatedDetail)) sink.stateChanged(this);
    }

    private void appendLocal(String line) {
        sink.chat(this, Component.literal(line));
    }

    private void resetChatState() {
        synchronized (chatStateLock) {
            nextChatIndex = 0;
            lastSeenMessages = new LastSeenMessagesTracker(20);
            signatureCache = MessageSignatureCache.createDefault();
            signedEncoder = SignedMessageChain.Encoder.UNSIGNED;
            chatSession = null;
            serverAssignedUuid = null;
        }
    }

    private static boolean isCriticalInbound(Packet<?> packet) {
        return packet instanceof ClientboundKeepAlivePacket
            || packet instanceof ClientboundPingPacket
            || packet instanceof ClientboundDisconnectPacket
            || packet instanceof ClientboundLoginDisconnectPacket
            || packet instanceof ClientboundHelloPacket
            || packet instanceof ClientboundLoginFinishedPacket
            || packet instanceof ClientboundLoginCompressionPacket
            || packet instanceof ClientboundCustomQueryPacket
            || packet instanceof ClientboundFinishConfigurationPacket
            || packet instanceof ClientboundRegistryDataPacket
            || packet instanceof ClientboundUpdateEnabledFeaturesPacket
            || packet instanceof ClientboundSelectKnownPacks
            || packet instanceof ClientboundResetChatPacket
            || packet instanceof ClientboundCodeOfConductPacket
            || packet instanceof net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket
            || packet instanceof ClientboundCookieRequestPacket
            || packet instanceof ClientboundStoreCookiePacket
            || packet instanceof ClientboundResourcePackPushPacket
            || packet instanceof ClientboundTransferPacket
            || packet instanceof ClientboundLoginPacket
            || packet instanceof ClientboundStartConfigurationPacket
            || packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ClientboundPlayerRotationPacket
            || packet instanceof ClientboundShowDialogPacket
            || packet instanceof ClientboundClearDialogPacket;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        return null;
    }

    private static String shortError(Throwable error) {
        if (error == null) return "Unknown error";
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        if (message == null || message.isBlank()) message = cause.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 180 ? message.substring(0, 177) + "..." : message;
    }
}
