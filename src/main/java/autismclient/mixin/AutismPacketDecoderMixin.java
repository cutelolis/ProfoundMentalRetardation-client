package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismPacketCapture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PacketDecoder.class)
public abstract class AutismPacketDecoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;
    @Unique private byte[] autism$incomingPlaintext = new byte[0];
    @Unique private int autism$outSizeBeforeDecode;

    @Inject(method = "decode", at = @At("HEAD"))
    private void autism$captureIncomingPlaintext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        autism$outSizeBeforeDecode = out == null ? 0 : out.size();
        if (PackHideState.isHardLocked()) {
            autism$incomingPlaintext = new byte[0];
            return;
        }
        autism$incomingPlaintext = autism$shouldCapturePacketBytes()
            ? AutismPacketCapture.copyReadableBytes(input)
            : new byte[0];
    }

    @Inject(method = "decode", at = @At("TAIL"))
    private void autism$attachIncomingPlaintext(ChannelHandlerContext ctx, ByteBuf input, List<Object> out, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        if (out == null || out.isEmpty()) return;
        int start = Math.max(0, Math.min(autism$outSizeBeforeDecode, out.size()));
        boolean capturePlaintext = autism$shouldCapturePacketPlaintext() && autism$incomingPlaintext.length > 0;
        AutismModule module = AutismModule.get();
        String protocol = protocolInfo.id().id();
        boolean payloadCaptured = false;
        for (int i = start; i < out.size(); i++) {
            Object decoded = out.get(i);
            if (!(decoded instanceof Packet<?> packet)) continue;
            if (capturePlaintext) {
                AutismPacketCapture.capturePlaintext(packet, "S2C", protocol, packet.type(),
                    Unpooled.wrappedBuffer(autism$incomingPlaintext));
            }
            if (module != null && module.shouldCapturePayloadBytes()) {
                if (module.captureDecodedPayloadPacket(packet, "S2C", protocol, "decoder")) {
                    payloadCaptured = true;
                }
            }
        }
        if (!payloadCaptured && module != null && module.shouldCapturePayloadBytes() && autism$incomingPlaintext.length > 0) {
            module.captureRawPayloadFrame(autism$incomingPlaintext, "S2C", protocol, "decoder-raw");
        }
    }

    @Unique
    private static boolean autism$shouldCapturePacketPlaintext() {
        if (PackHideState.isHardLocked()) return false;
        AutismModule module = AutismModule.get();
        return module != null && module.shouldCapturePacketPlaintext();
    }

    @Unique
    private static boolean autism$shouldCapturePacketBytes() {
        if (PackHideState.isHardLocked()) return false;
        AutismModule module = AutismModule.get();
        return module != null && (module.shouldCapturePacketPlaintext() || module.shouldCapturePayloadBytes());
    }
}
