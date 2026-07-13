package autismclient.util.multi;

import autismclient.util.AutismProxy;
import autismclient.util.AutismProxyType;
import net.minecraft.network.Connection;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class MultiConnectionContext {
    public record ProxySpec(AutismProxyType type, String address, int port, String username, String password) {
        public static ProxySpec copyOf(AutismProxy proxy) {
            if (proxy == null) return null;
            return new ProxySpec(
                proxy.type,
                proxy.address == null ? "" : proxy.address,
                proxy.port,
                proxy.username == null ? "" : proxy.username,
                proxy.password == null ? "" : proxy.password
            );
        }
    }

    private static final Map<Connection, ProxySpecHolder> CONTEXTS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private MultiConnectionContext() {
    }

    public static void register(Connection connection, AutismProxy proxy) {
        if (connection != null) CONTEXTS.put(connection, new ProxySpecHolder(ProxySpec.copyOf(proxy)));
    }

    public static boolean isMulti(Connection connection) {
        return connection != null && CONTEXTS.containsKey(connection);
    }

    public static ProxySpec proxy(Connection connection) {
        ProxySpecHolder holder = connection == null ? null : CONTEXTS.get(connection);
        return holder == null ? null : holder.proxy;
    }

    public static void remove(Connection connection) {
        if (connection != null) CONTEXTS.remove(connection);
    }

    private record ProxySpecHolder(ProxySpec proxy) {
    }
}
