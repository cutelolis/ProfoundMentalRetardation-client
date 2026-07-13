package autismclient.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import autismclient.mixin.accessor.AutismClientConnectionAccessor;
import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.modules.ModuleRegistry;
import autismclient.security.AutismProtectorPackStrip;
import autismclient.security.AutismResourcePackTruthGuard;
import autismclient.security.AutismSpoofPayloadFilter;
import autismclient.util.macro.MacroExecutor;
import autismclient.util.macro.PacketGateManager;
import autismclient.util.AutismClientMessaging;
import autismclient.util.AutismContainerHold;
import autismclient.util.AutismContainerTarget;
import autismclient.util.AutismSharedState;
import autismclient.AutismClientAddon;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.client.Minecraft;

@Mixin(Connection.class)
public abstract class AutismClientConnectionMixin {
    @Unique
    private static final boolean AUTISM_PACKET_TRACE = Boolean.getBoolean("autism.packet.trace");

    @Unique
    private volatile boolean autism$spoofPipelineInstalled;

    @Unique
    private static final String AUTISM_SPOOF_FILTER = "autism_spoof_filter";

    @Unique private static volatile autismclient.modules.Module autism$noFallCached;
    @Unique private static volatile int autism$noFallRevision = -1;

    @Unique
    private static autismclient.modules.Module autism$noFallModule() {
        int revision = autismclient.modules.ModuleRegistry.revision();
        if (revision != autism$noFallRevision) {
            autism$noFallCached = autismclient.modules.ModuleRegistry.get("no-fall");
            autism$noFallRevision = revision;
        }
        return autism$noFallCached;
    }

    @Inject(method = "channelActive", at = @At("HEAD"))
    private void autism$onChannelActive(ChannelHandlerContext context, CallbackInfo ci) {
        autism$spoofPipelineInstalled = false;
        autism$ensureSpoofPipelineFilter();
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void autism$onChannelInactive(ChannelHandlerContext context, CallbackInfo ci) {
        autism$spoofPipelineInstalled = false;
        AutismProtectorPackStrip.clearAll();
        AutismResourcePackTruthGuard.clearAll();
    }

    @Inject(method = "configurePacketHandler", at = @At("TAIL"))
    private void autism$onConfigurePacketHandler(ChannelPipeline pipeline, CallbackInfo ci) {
        autism$ensureSpoofPipelineFilter();
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void yang$onSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        autism$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            if (AutismResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
                ci.cancel();
                return;
            }
            AutismProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }
        PacketListener packetListener = ((Connection) (Object) this).getPacketListener();
        AutismModule module = AutismModule.get();
        AutismModule.PacketHookSnapshot hooks = module == null
            ? AutismModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayConnectionActive());
        boolean normalLoggerPath = hooks.normalPath();
        String protocolHint = autism$protocolHint(packetListener);
        if (AutismSpoofPayloadFilter.shouldBlockForVanillaSpoof(module, packet)) {
            ci.cancel();
            return;
        }
        if (AutismSpoofPayloadFilter.shouldDropForProtector(packet)) {
            ci.cancel();
            return;
        }
        if (PackHideState.isHardLocked()) return;

        if (autismclient.util.AutismClientWake.isActive()
            && packet instanceof ServerboundPlayerCommandPacket autism$cmd
            && autism$cmd.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SLEEPING) {
            ci.cancel();
            return;
        }

        if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket) {
            autismclient.modules.Module autism$noFall = autism$noFallModule();
            if (autism$noFall != null && autism$noFall.isEnabled()) autism$noFall.onPacketSend(packet);
        }
        if (autismclient.util.macro.PingSpoofController.interceptOutbound(packet)
            || autismclient.modules.AutismBlinkManager.interceptOutbound(packet)) {
            ci.cancel();
            return;
        }
        boolean payloadLoggedEarly = false;
        if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "C2S", protocolHint);
        }
        if (module != null && !payloadLoggedEarly && hooks.passivePayloadCapture() && (!normalLoggerPath || !hooks.packetLoggerCapturing())) {
            module.capturePassivePayloadPacket(packet, "C2S", protocolHint);
        }

        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketSend(packet);
        }

        AutismSharedState shared = AutismSharedState.get();

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            if (shared.consumeBlockCaptureCallback(pibp.getHitResult().getBlockPos(), pibp.getHitResult().getDirection())) {
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (AutismContainerHold.isHeld(closeForHold.getContainerId())) {
                AutismContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
            if (autism$shouldKeepXCarryOpen(shared, closeForHold)) {
                ci.cancel();
                return;
            }
        }

        if (!normalLoggerPath) return;

        if (packet instanceof ServerboundUseItemOnPacket pibp) {
            shared.setLastInteractedBlockPos(pibp.getHitResult().getBlockPos());
            shared.setLastContainerTarget(AutismContainerTarget.forBlockHit(pibp.getHitResult(), pibp.getHand()));
        }

        if (packet instanceof ServerboundInteractPacket entityPacket) {
            net.minecraft.world.entity.Entity targeted = Minecraft.getInstance().crosshairPickEntity;
            if (targeted != null && targeted != Minecraft.getInstance().player) {
                net.minecraft.world.InteractionHand capturedHand = entityPacket.hand();
                net.minecraft.world.phys.Vec3 capturedHitPos = entityPacket.location();
                shared.setLastContainerTarget(
                    capturedHitPos != null
                        ? AutismContainerTarget.forEntityAt(targeted, capturedHand, capturedHitPos)
                        : AutismContainerTarget.forEntity(targeted, capturedHand)
                );
            }
        }

        if (packet instanceof ServerboundInteractPacket && shared.hasEntityCaptureCallback()) {
            net.minecraft.world.entity.Entity targeted = Minecraft.getInstance().crosshairPickEntity;
            if (targeted != null && targeted != Minecraft.getInstance().player) {
                AutismSharedState state = AutismSharedState.get();
                String payload;
                if (state.isEntityCaptureSpecific()) {

                    String uuid = targeted.getStringUUID();
                    String typeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
                    String dispName = targeted.getDisplayName().getString().replaceAll("\u00a7.", "").trim();
                    payload = "~" + uuid + "~" + typeId + "~" + dispName;
                    state.setEntityCaptureSpecific(false);
                } else {
                    payload = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(targeted.getType()).toString();
                }
                state.consumeEntityCaptureCallback(payload);
            }
        }

        if (packet instanceof ServerboundSignUpdatePacket && shared.consumeSuppressNextSignUpdatePacket()) {
            ci.cancel();
            return;
        }

        if (packet instanceof ServerboundEditBookPacket && shared.consumeSuppressNextBookEditPacket()) {
            ci.cancel();
            return;
        }

        boolean forceBookOrSignPacket =
            packet instanceof ServerboundSignUpdatePacket && shared.consumeForceNextSignUpdatePacket()
                || packet instanceof ServerboundEditBookPacket && shared.consumeForceNextBookEditPacket();

        if (packet instanceof ServerboundSignUpdatePacket && !shared.shouldEditSigns()) {
            shared.setAllowSignEditing(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (packet instanceof ServerboundEditBookPacket && !shared.shouldUpdateBook()) {
            shared.setAllowBookUpdate(true);
            if (!forceBookOrSignPacket) {
                ci.cancel();
                return;
            }
        }

        if (shared.isGBreakCapturing()) {
            if (packet instanceof ServerboundPlayerActionPacket) {

                shared.onGBreakPacket(packet);
            }

            return;
        }

        if (packet instanceof ServerboundPlayerActionPacket actionPacket
            && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
            if (ModuleRegistry.dispatchStartBreakingBlock(actionPacket.getPos(), actionPacket.getDirection())) {
                ci.cancel();
                return;
            }
        }

        if (module.handlePacketSend(packet, payloadLoggedEarly)) {
            ci.cancel();
            return;
        }

        if (forceBookOrSignPacket) return;

        if (!shared.isFlushing()) {
            PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "C2S");
            if (gateResult == PacketGateManager.Result.CANCEL) {
                ci.cancel();
                return;
            }
            if (gateResult == PacketGateManager.Result.DELAY) {
                shared.enqueuePacket(packet);
                ci.cancel();
                return;
            }
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        if (shared.isFlushing()) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getC2SPackets().contains(packet.getClass());
        } else {

            shouldHandle = isGuiPacket(packet);
        }

        if (!shouldHandle) return;

        if (AUTISM_PACKET_TRACE) {
            AutismClientAddon.LOG.debug("[Autism] Packet detected: {} | Send={} Delay={} | Custom={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets(), shared.shouldUseCustomPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            if (AUTISM_PACKET_TRACE) AutismClientAddon.LOG.debug("[Autism] CANCELLED packet (send disabled)");
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            if (AUTISM_PACKET_TRACE) AutismClientAddon.LOG.debug("[Autism] QUEUED packet (delay enabled)");
            AutismModule captureModule = AutismModule.get();
            AutismSharedState.ReplayMode captureMode = (captureModule != null && captureModule.isCaptureAsExact())
                ? AutismSharedState.ReplayMode.EXACT
                : AutismSharedState.ReplayMode.REGENERATE;
            shared.enqueuePacket(packet, captureMode);
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void yang$afterSendPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        if (!isAutismActive()) return;
        if (!isPlayConnectionActive()) return;
        MacroExecutor.onPacketSent(packet);
    }

    @Unique
    private boolean isGuiPacket(Packet<?> packet) {
        return packet instanceof ServerboundContainerClickPacket
            || packet instanceof ServerboundContainerButtonClickPacket
            || packet instanceof ServerboundSetCreativeModeSlotPacket
            || packet instanceof ServerboundPlayerActionPacket
            || packet instanceof ServerboundUseItemPacket
            || packet instanceof ServerboundSignUpdatePacket
            || packet instanceof ServerboundEditBookPacket
            || packet instanceof ServerboundChatPacket
            || packet instanceof ServerboundChatCommandPacket
            || packet instanceof ServerboundChatCommandSignedPacket
            || packet instanceof net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
    }

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void yang$onReceivePacket(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        PacketListener listener = ((Connection) (Object) this).getPacketListener();
        AutismModule module = AutismModule.get();
        AutismModule.PacketHookSnapshot hooks = module == null
            ? AutismModule.PacketHookSnapshot.inactive()
            : module.packetHookSnapshot(isPlayReceiveListener(listener));
        boolean normalLoggerPath = hooks.normalPath();
        String protocolHint = autism$protocolHint(listener);
        if (!(packet instanceof net.minecraft.network.protocol.common.ClientboundShowDialogPacket)
            && !(packet instanceof net.minecraft.network.protocol.common.ClientboundClearDialogPacket)) {
            autismclient.util.custommenu.CustomMenuTracker.accept(packet, protocolHint);
        }
        if (PackHideState.isHardLocked()) {
            autism$clearRuntimeConnectionStateOnDisconnect(packet);
            return;
        }
        if (autismclient.util.macro.PingSpoofController.interceptInbound(packet)
            || autismclient.modules.AutismBlinkManager.interceptInbound(packet)) {
            ci.cancel();
            return;
        }
        boolean payloadLoggedEarly = false;
        if (module != null && hooks.packetLoggerCapturing()) {
            payloadLoggedEarly = module.capturePayloadPacketForLogger(packet, "S2C", protocolHint);
        }
        if (module != null && !payloadLoggedEarly && hooks.passivePayloadCapture() && (!normalLoggerPath || !hooks.packetLoggerCapturing())) {
            module.capturePassivePayloadPacket(packet, "S2C", protocolHint);
        }
        if (module != null && !normalLoggerPath && hooks.pluginDiscoveryObservation()) {
            module.observePluginDiscoveryPacketReceive(packet);
        }

        MacroExecutor.observeIncomingChat(packet);

        autismclient.modules.ModuleEspChunkCache.onPacketReceived(packet);
        if (!normalLoggerPath) return;

        autismclient.util.macro.ServerTickTracker.onS2CPacket(packet);
        MacroExecutor.onPacketReceived(packet);

        if (packet instanceof net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket macroSuggestions
                && autismclient.util.AutismCommandSuggestionIds.isMacroId(macroSuggestions.id())) {
            ci.cancel();
            return;
        }

        if (packet instanceof ClientboundOpenScreenPacket openScreenPacket) {
            AutismContainerHold.onContainerOpened(openScreenPacket.getContainerId());
        }
        if (packet instanceof ClientboundDisconnectPacket) {
            autism$clearRuntimeConnectionStateOnDisconnect(packet);
        }

        if (module.handlePacketReceive(packet, payloadLoggedEarly)) {
            ci.cancel();
            return;
        }

        AutismSharedState shared = AutismSharedState.get();

        PacketGateManager.Result gateResult = PacketGateManager.handle(packet, "S2C");
        if (gateResult == PacketGateManager.Result.CANCEL) {
            ci.cancel();
            return;
        }
        if (gateResult == PacketGateManager.Result.DELAY) {
            shared.enqueuePacket(packet);
            ci.cancel();
            return;
        }

        boolean anyFeatureActive = shared.shouldDelayGuiPackets()
            || !shared.shouldSendGuiPackets()
            || shared.shouldUseCustomPackets();
        if (!anyFeatureActive) return;

        boolean shouldHandle = false;

        if (shared.shouldUseCustomPackets()) {

            shouldHandle = shared.getS2CPackets().contains(packet.getClass());
        }

        if (!shouldHandle) return;

        if (AUTISM_PACKET_TRACE) {
            AutismClientAddon.LOG.debug("[Autism] S2C Packet detected: {} | Send={} Delay={}",
                packet.getClass().getSimpleName(), shared.shouldSendGuiPackets(),
                shared.shouldDelayGuiPackets());
        }

        if (!shared.shouldSendGuiPackets()) {
            ci.cancel();
            return;
        }

        if (shared.shouldDelayGuiPackets()) {
            shared.enqueuePacket(packet);
            ci.cancel();
        }
    }

    @Unique
    private boolean isAutismActive() {
        AutismModule module = AutismModule.get();
        return module != null && module.arePacketHooksActive();
    }

    @Unique
    private void autism$ensureSpoofPipelineFilter() {
        if (autism$spoofPipelineInstalled) return;
        Channel channel = null;
        try {
            channel = ((AutismClientConnectionAccessor) this).getChannel();
        } catch (Throwable ignored) {  }
        if (channel == null) return;

        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline == null || pipeline.get(AUTISM_SPOOF_FILTER) != null) {
                autism$spoofPipelineInstalled = true;
                return;
            }
            if (pipeline.get("encoder") != null) {
                pipeline.addAfter("encoder", AUTISM_SPOOF_FILTER, new AutismSpoofPayloadFilter());
                autism$spoofPipelineInstalled = true;
            }
        } catch (Throwable t) {
            AutismClientAddon.LOG.debug("[Autism] Failed to install client spoof payload filter", t);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void autism$onSendPacketWithFlush(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        autism$ensureSpoofPipelineFilter();
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket
            && AutismResourcePackTruthGuard.shouldCancelOutboundStatus(resourcePackPacket)) {
            ci.cancel();
            return;
        }
        if (packet instanceof ServerboundResourcePackPacket resourcePackPacket) {
            AutismProtectorPackStrip.onPackFinalResponse(resourcePackPacket.id(), resourcePackPacket.action());
        }
        AutismSharedState shared = AutismSharedState.get();
        if (packet instanceof ServerboundContainerClosePacket closeForHold) {
            if (shared.consumeSuppressNextContainerClosePacket()) {
                ci.cancel();
                return;
            }
            if (AutismContainerHold.isHeld(closeForHold.getContainerId())) {
                AutismContainerHold.capturePendingClose(closeForHold.getContainerId(), closeForHold);
                ci.cancel();
                return;
            }
            if (autism$shouldKeepXCarryOpen(shared, closeForHold)) {
                ci.cancel();
                return;
            }
        }
        if (AutismSpoofPayloadFilter.shouldBlockForVanillaSpoof(AutismModule.get(), packet)) {
            ci.cancel();
            return;
        }
        if (AutismSpoofPayloadFilter.shouldDropForProtector(packet)) {
            ci.cancel();
        }
    }

    @Unique
    private void autism$clearRuntimeConnectionStateOnDisconnect(Packet<?> packet) {
        if (!(packet instanceof ClientboundDisconnectPacket)) return;
        AutismContainerHold.clearAll();
        PacketGateManager.clearAll();
        autismclient.util.macro.PingSpoofController.clearQueue();
        autismclient.modules.AutismBlinkManager.clear();

        AutismSharedState s = AutismSharedState.get();
        s.setXCarryForcedTargets(java.util.Collections.emptySet(), false);
        s.setXCarryForced(false);
        s.setXCarryActive(false);
    }

    @Unique
    private boolean isPlayConnectionActive() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.getConnection() != null;
    }

    @Unique
    private static boolean isPlayReceiveListener(PacketListener listener) {
        return listener instanceof ClientGamePacketListener;
    }

    @Unique
    private static String autism$protocolHint(PacketListener listener) {
        if (listener == null) return "";
        if (listener instanceof ClientGamePacketListener) return "play";
        String name = listener.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("configuration")) return "configuration";
        return "";
    }

    @Unique
    private boolean autism$shouldKeepXCarryOpen(AutismSharedState shared, ServerboundContainerClosePacket packet) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        AutismModule module = AutismModule.get();
        boolean allowPassiveXCarry = module != null && module.isXCarryEnabled();

        if (!allowPassiveXCarry && !shared.isXCarryForced()) return false;
        if (packet.getContainerId() != client.player.inventoryMenu.containerId) return false;

        java.util.Set<Integer> mask;
        boolean carryCursor;
        if (shared.isXCarryForced()) {
            mask = shared.getXCarryForcedSlotMask();
            carryCursor = shared.isXCarryForcedCarryCursor();
        } else {
            mask = module == null ? null : module.getXCarryModuleSlotMask();
            carryCursor = module == null || module.isXCarryCarryCursor();
        }
        boolean hasItems = autismclient.util.macro.XCarryAction.hasStoredItems(
                client.player.inventoryMenu, carryCursor, mask);
        shared.setXCarryActive(hasItems);

        return true;
    }
}
