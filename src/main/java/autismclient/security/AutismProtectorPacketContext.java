package autismclient.security;

public final class AutismProtectorPacketContext {
    private static final ThreadLocal<Boolean> PROCESSING_PACKET = new ThreadLocal<>();

    private AutismProtectorPacketContext() {
    }

    public static boolean isProcessingPacket() {
        return Boolean.TRUE.equals(PROCESSING_PACKET.get());
    }

    public static void setProcessingPacket(boolean value) {
        if (value) PROCESSING_PACKET.set(Boolean.TRUE);
        else PROCESSING_PACKET.remove();
    }
}
