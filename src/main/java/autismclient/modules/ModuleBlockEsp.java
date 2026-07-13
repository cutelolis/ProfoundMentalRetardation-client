package autismclient.modules;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;

final class ModuleBlockEsp {
    private static volatile Selection cachedSelection = new Selection("", Set.of());

    private ModuleBlockEsp() {
    }

    private static final int PER_CHUNK_CAP = 2048;

    private static final Set<Block> BARRIER_TARGET = Set.of(Blocks.BARRIER);
    private static final int BARRIER_COLOR = 0xFFFF3B3B;

    static void collectBoth(Module module, ClientLevel level, Player player,
                            BiConsumer<AABB, Integer> boxEmit,
                            BiConsumer<Vec3, Integer> traceEmit) {
        if (module == null || level == null || player == null || (boxEmit == null && traceEmit == null)) return;
        Selection selection = selection(module.value("blocks"));
        if (selection.blocks().isEmpty()) return;
        double maxDist = parseDouble(module.value("max-distance"), 64.0);
        int maxTargets = parseInt(module.value("max-targets"), 1024, 64, 8192);
        int color = ModuleRenderUtil.color(module, "color", 0xCCFF3B3B);
        collect(level, player, selection.blocks(), selection.value(), maxDist, maxTargets, color,
            ModuleEspChunkCache.BLOCK_ESP, boxEmit, traceEmit);
    }

    static void collectBarriers(Module module, ClientLevel level, Player player,
                                BiConsumer<AABB, Integer> boxEmit,
                                BiConsumer<Vec3, Integer> traceEmit) {
        if (module == null || level == null || player == null || (boxEmit == null && traceEmit == null)) return;
        double maxDist = parseDouble(module.value("max-distance"), 64.0);
        int maxTargets = parseInt(module.value("max-targets"), 1024, 64, 8192);
        collect(level, player, BARRIER_TARGET, "minecraft:barrier", maxDist, maxTargets, BARRIER_COLOR,
            ModuleEspChunkCache.BARRIER_ESP, boxEmit, traceEmit);
    }

    private static void collect(ClientLevel level, Player player, Set<Block> targets, String stampKey,
                                double maxDist, int maxTargets, int color, ModuleEspChunkCache cache,
                                BiConsumer<AABB, Integer> boxEmit, BiConsumer<Vec3, Integer> traceEmit) {
        double effectiveMax = maxDist <= 0 ? 4096.0 : maxDist;
        double maxDistSq = effectiveMax * effectiveMax;
        int chunkRadius = Math.max(1, (int) Math.ceil(effectiveMax / 16.0));

        ModuleEspChunkCache.onLevel(level);

        String stamp = stampKey + "|" + color;
        long gameTime = level.getGameTime();
        ClientChunkCache chunks = level.getChunkSource();
        Vec3 playerPos = player.position();
        int playerChunkX = player.chunkPosition().x();
        int playerChunkZ = player.chunkPosition().z();
        int emitted = 0;

        for (int radius = 0; radius <= chunkRadius && emitted < maxTargets; radius++) {
            for (int dx = -radius; dx <= radius && emitted < maxTargets; dx++) {
                for (int dz = -radius; dz <= radius && emitted < maxTargets; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int chunkX = playerChunkX + dx;
                    int chunkZ = playerChunkZ + dz;
                    if (chunkDistSq(playerPos, chunkX, chunkZ) > maxDistSq) continue;
                    LevelChunk chunk = chunks.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    List<ModuleEspChunkCache.Entry> entries = cache.chunkEntries(
                        chunk, gameTime, stamp, (scanned, out) -> scanChunk(level, scanned, targets, color, out));
                    for (ModuleEspChunkCache.Entry entry : entries) {
                        if (emitted >= maxTargets) break;
                        Vec3 trace = entry.trace();
                        if (sqDist(playerPos, trace.x, trace.y, trace.z) > maxDistSq) continue;
                        if (boxEmit != null) boxEmit.accept(entry.box(), entry.color());
                        if (traceEmit != null) traceEmit.accept(trace, entry.color());
                        emitted++;
                    }
                }
            }
        }
    }

    private static double chunkDistSq(Vec3 playerPos, int chunkX, int chunkZ) {
        double minX = chunkX << 4;
        double minZ = chunkZ << 4;
        double dx = playerPos.x < minX ? minX - playerPos.x : playerPos.x > minX + 16 ? playerPos.x - minX - 16 : 0;
        double dz = playerPos.z < minZ ? minZ - playerPos.z : playerPos.z > minZ + 16 ? playerPos.z - minZ - 16 : 0;
        return dx * dx + dz * dz;
    }

    private static void scanChunk(ClientLevel level, LevelChunk chunk, Set<Block> targets, int color,
                                  List<ModuleEspChunkCache.Entry> out) {
        LevelChunkSection[] sections = chunk.getSections();
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        java.util.function.Predicate<BlockState> isTarget = state -> targets.contains(state.getBlock());

        for (int sectionIndex = 0; sectionIndex < sections.length && out.size() < PER_CHUNK_CAP; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || section.hasOnlyAir()) continue;

            if (!section.maybeHas(isTarget)) continue;

            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int sy = 0; sy < 16 && out.size() < PER_CHUNK_CAP; sy++) {
                int y = baseY + sy;
                if (level.isOutsideBuildHeight(y)) continue;
                for (int sx = 0; sx < 16 && out.size() < PER_CHUNK_CAP; sx++) {
                    int x = minX + sx;
                    for (int sz = 0; sz < 16 && out.size() < PER_CHUNK_CAP; sz++) {
                        int z = minZ + sz;
                        BlockState state = section.getBlockState(sx, sy, sz);
                        if (!targets.contains(state.getBlock())) continue;
                        mutable.set(x, y, z);
                        out.add(new ModuleEspChunkCache.Entry(
                            blockShape(level, mutable, state), new Vec3(x + 0.5, y + 0.5, z + 0.5), color));
                    }
                }
            }
        }
    }

    private static AABB blockShape(ClientLevel level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        if (shape == null || shape.isEmpty()) return new AABB(pos);
        return shape.bounds().move(pos.getX(), pos.getY(), pos.getZ());
    }

    private static Selection selection(String value) {
        String safe = value == null ? "" : value;
        Selection cached = cachedSelection;
        if (cached.value().equals(safe)) return cached;
        Set<Block> blocks = new LinkedHashSet<>();
        for (String raw : safe.split("\\|")) {
            String id = normalizeId(raw);
            if (id.isEmpty()) continue;
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) continue;
            Block block = BuiltInRegistries.BLOCK.getOptional(parsed).orElse(Blocks.AIR);

            if (block != Blocks.AIR && block != Blocks.BARRIER) blocks.add(block);
        }
        Selection next = new Selection(safe, Set.copyOf(blocks));
        cachedSelection = next;
        return next;
    }

    private static String normalizeId(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "";
        return value.contains(":") ? value : "minecraft:" + value;
    }

    private static double sqDist(Vec3 from, double x, double y, double z) {
        double dx = from.x - x;
        double dy = from.y - y;
        double dz = from.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private record Selection(String value, Set<Block> blocks) {
    }
}
