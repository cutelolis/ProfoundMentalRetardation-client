package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismPacketCapture;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketEncoder.class)
public abstract class AutismPacketEncoderMixin<T extends PacketListener> {
    @Shadow @Final private ProtocolInfo<T> protocolInfo;

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
        at = @At("TAIL"))
    private void autism$captureEncodedPlaintext(ChannelHandlerContext ctx, Packet<T> packet, ByteBuf output, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) return;
        AutismModule module = AutismModule.get();
        if (module == null) return;
        String protocol = protocolInfo.id().id();
        if (module.shouldCapturePacketPlaintext()) {
            AutismPacketCapture.capturePlaintext(packet, "C2S", protocol, packet.type(), output);
        }
        if (module.shouldCapturePayloadBytes()) {
            boolean captured = module.captureDecodedPayloadPacket(packet, "C2S", protocol, "encoder");
            if (!captured && output != null) {
                module.captureRawPayloadFrame(AutismPacketCapture.copyReadableBytes(output), "C2S", protocol, "encoder-raw");
            }
        }
    }

}
