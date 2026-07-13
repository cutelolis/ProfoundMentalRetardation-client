package autismclient.mixin;

import autismclient.modules.AutismModule;
import autismclient.modules.PackHideState;
import autismclient.util.AutismPayloadSupport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.network.protocol.common.custom.CustomPacketPayload$1")
public abstract class AutismCustomPayloadCodecMixin {
    @Unique private int autism$encodeStartIndex = -1;
    @Unique private int autism$decodeStartIndex = -1;

    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void autism$encodeRawCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) {
            autism$encodeStartIndex = -1;
            return;
        }
        autism$encodeStartIndex = autism$shouldCapturePayloadBytes() && buf != null ? buf.writerIndex() : -1;
        boolean isRaw = payload instanceof AutismPayloadSupport.RawCustomPacketPayload;
        if (!isRaw && !autism$shouldCapturePayloadBytes()) return;

        byte[] rememberedBytes = AutismPayloadSupport.getRememberedUnknownPayloadBytes(payload);
        if (rememberedBytes == null && !isRaw) return;

        CustomPacketPayload.Type<?> type = payload.type();
        if (type == null || type.id() == null) return;

        byte[] bytes = rememberedBytes != null
            ? rememberedBytes
            : ((AutismPayloadSupport.RawCustomPacketPayload) payload).bytes();

        buf.writeIdentifier(type.id());
        if (bytes.length > 0) {
            buf.writeBytes(bytes);
        }
        ci.cancel();
    }

    @Inject(method = "encode(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("TAIL"))
    private void autism$captureEncodedCustomPacketPayload(FriendlyByteBuf buf, CustomPacketPayload payload, CallbackInfo ci) {
        if (PackHideState.isHardLocked()) {
            autism$encodeStartIndex = -1;
            return;
        }
        if (payload == null || buf == null || autism$encodeStartIndex < 0) return;
        int end = buf.writerIndex();
        if (end <= autism$encodeStartIndex) return;
        byte[] encodedBytes = new byte[end - autism$encodeStartIndex];
        buf.getBytes(autism$encodeStartIndex, encodedBytes);
        AutismPayloadSupport.rememberDecodedPayloadBytes(payload, autism$payloadChannel(payload), encodedBytes);
        autism$encodeStartIndex = -1;
    }

    @Inject(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At("HEAD"))
    private void autism$captureDecodeStart(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        if (PackHideState.isHardLocked()) {
            autism$decodeStartIndex = -1;
            return;
        }
        autism$decodeStartIndex = autism$shouldCapturePayloadBytes() && buf != null ? buf.readerIndex() : -1;
    }

    @Inject(method = "decode(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;",
            at = @At("RETURN"))
    private void autism$captureDecodedCustomPacketPayload(FriendlyByteBuf buf, CallbackInfoReturnable<CustomPacketPayload> cir) {
        if (PackHideState.isHardLocked()) {
            autism$decodeStartIndex = -1;
            return;
        }
        CustomPacketPayload payload = cir.getReturnValue();
        if (payload == null || buf == null || autism$decodeStartIndex < 0) return;
        int end = buf.readerIndex();
        if (end <= autism$decodeStartIndex) return;
        byte[] encodedBytes = new byte[end - autism$decodeStartIndex];
        buf.getBytes(autism$decodeStartIndex, encodedBytes);
        AutismPayloadSupport.rememberDecodedPayloadBytes(payload, autism$payloadChannel(payload), encodedBytes);
        autism$decodeStartIndex = -1;
    }

    @Unique
    private static boolean autism$shouldCapturePayloadBytes() {
        if (PackHideState.isHardLocked()) return false;
        AutismModule module = AutismModule.get();
        return module != null && module.shouldCapturePayloadBytes();
    }

    @Unique
    private static String autism$payloadChannel(CustomPacketPayload payload) {
        try {
            CustomPacketPayload.Type<?> type = payload == null ? null : payload.type();
            if (type != null && type.id() != null) return type.id().toString();
        } catch (Throwable ignored) {  }
        return "";
    }
}
