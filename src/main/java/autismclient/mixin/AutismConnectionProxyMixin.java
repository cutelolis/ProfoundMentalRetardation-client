package autismclient.mixin;

import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyManager;
import autismclient.util.multi.MultiConnectionContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.BandwidthDebugMonitor;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

@Mixin(value = Connection.class, priority = 2000)
public abstract class AutismConnectionProxyMixin {
    @Inject(method = "configureSerialization", at = @At("HEAD"))
    private static void autism$disableMeteorProxyBeforeHandlers(ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (local || inboundDirection != PacketFlow.CLIENTBOUND) return;
        autism$disableMeteorProxy();
    }

    @Inject(method = "configureSerialization", at = @At("RETURN"))
    private static void autism$addProxyHandler(ChannelPipeline pipeline, PacketFlow inboundDirection, boolean local, @Nullable BandwidthDebugMonitor monitor, CallbackInfo ci) {
        if (local || inboundDirection != PacketFlow.CLIENTBOUND) return;
        AutismProxy proxy = AutismProxyManager.get().getEnabled();
        if (proxy == null) return;
        InetSocketAddress address = new InetSocketAddress(proxy.address, proxy.port);
        switch (proxy.type) {
            case Socks4 -> pipeline.addFirst("autism_socks4_proxy", new Socks4ProxyHandler(address, proxy.username == null ? "" : proxy.username));
            case Socks5 -> pipeline.addFirst("autism_socks5_proxy", new Socks5ProxyHandler(address, proxy.username == null ? "" : proxy.username, proxy.password == null ? "" : proxy.password));
        }
    }

    @Inject(method = "configurePacketHandler", at = @At("HEAD"))
    private void autism$applyMultiProxy(ChannelPipeline pipeline, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (!MultiConnectionContext.isMulti(connection)) return;
        autism$removeProxy(pipeline, "autism_socks4_proxy");
        autism$removeProxy(pipeline, "autism_socks5_proxy");
        MultiConnectionContext.ProxySpec proxy = MultiConnectionContext.proxy(connection);
        if (proxy == null || proxy.address().isBlank() || proxy.port() <= 0) return;
        InetSocketAddress address = new InetSocketAddress(proxy.address(), proxy.port());
        switch (proxy.type()) {
            case Socks4 -> pipeline.addFirst("autism_multi_socks4_proxy", new Socks4ProxyHandler(address, proxy.username()));
            case Socks5 -> pipeline.addFirst("autism_multi_socks5_proxy", new Socks5ProxyHandler(address, proxy.username(), proxy.password()));
        }
    }

    @Unique
    private static void autism$removeProxy(ChannelPipeline pipeline, String name) {
        if (pipeline.get(name) != null) pipeline.remove(name);
    }

    private static void autism$disableMeteorProxy() {
        if (!FabricLoader.getInstance().isModLoaded("meteor-client")) return;
        try {
            Class<?> proxiesClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxies");
            Class<?> proxyClass = Class.forName("meteordevelopment.meteorclient.systems.proxies.Proxy");
            Object proxies = proxiesClass.getMethod("get").invoke(null);
            Object enabled = proxiesClass.getMethod("getEnabled").invoke(proxies);
            if (enabled != null) proxiesClass.getMethod("setEnabled", proxyClass, boolean.class).invoke(proxies, enabled, false);
        } catch (ReflectiveOperationException ignored) {  }
    }
}
