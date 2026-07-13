package autismclient.util.multi;

import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class MultiEntityTracker {
    private static final int CAP = 512;

    private record Entry(String type, double x, double y, double z) {
    }

    private final Map<Integer, Entry> byId = new ConcurrentHashMap<>();

    void put(int id, String type, double x, double y, double z) {
        if (byId.size() >= CAP && !byId.containsKey(id)) return;
        byId.put(id, new Entry(type == null ? "" : type, x, y, z));
    }

    void move(int id, double x, double y, double z) {
        Entry e = byId.get(id);
        if (e != null) byId.put(id, new Entry(e.type(), x, y, z));
    }

    void remove(int id) {
        byId.remove(id);
    }

    void clear() {
        byId.clear();
    }

    int nearest(String typeQuery, Vec3 from) {
        String q = typeQuery == null ? "" : typeQuery.trim().toLowerCase(Locale.ROOT);
        int colon = q.indexOf(':');
        if (colon >= 0) q = q.substring(colon + 1);
        q = q.replace('_', ' ');
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Integer, Entry> e : byId.entrySet()) {
            Entry v = e.getValue();
            if (!q.isEmpty() && !v.type().toLowerCase(Locale.ROOT).replace('_', ' ').contains(q)) continue;
            double dx = v.x() - from.x;
            double dy = v.y() - from.y;
            double dz = v.z() - from.z;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = e.getKey();
            }
        }
        return best;
    }

    double[] pos(int id) {
        Entry e = byId.get(id);
        return e == null ? null : new double[]{e.x(), e.y(), e.z()};
    }

    String typeOf(int id) {
        Entry e = byId.get(id);
        return e == null ? null : e.type();
    }

    boolean present(java.util.List<String> typeQueries, boolean containerOnly, double cx, double cy, double cz, double radius) {
        double radiusSq = radius * radius;
        java.util.List<String> queries = new java.util.ArrayList<>();
        if (typeQueries != null) {
            for (String q : typeQueries) {
                if (q == null || q.isBlank() || q.startsWith("~")) continue;
                String n = q.trim().toLowerCase(Locale.ROOT);
                int colon = n.indexOf(':');
                if (colon >= 0) n = n.substring(colon + 1);
                queries.add(n.replace('_', ' '));
            }
        }
        for (Entry v : byId.values()) {
            double dx = v.x() - cx;
            double dy = v.y() - cy;
            double dz = v.z() - cz;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            String type = v.type().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (containerOnly && !(type.contains("boat") || type.contains("minecart") || type.contains("llama") || type.contains("chest"))) {
                continue;
            }
            if (queries.isEmpty()) return true;
            for (String q : queries) if (type.contains(q)) return true;
        }
        return false;
    }
}
