package autismclient.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import autismclient.util.AutismBufferSource;
import autismclient.util.AutismPerf;
import autismclient.util.AutismWorldGeometry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.AutismRenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class ModuleWorldRenderer {
    private static boolean initialized;
    private static StorageSnapshot storageSnapshot = StorageSnapshot.empty();
    private static StorageSnapshot blockSnapshot = StorageSnapshot.empty();
    private static StorageSnapshot barrierSnapshot = StorageSnapshot.empty();

    private static List<TracerLine> pendingTracerLines = List.of();
    private static Vec3 pendingTracerCamera = Vec3.ZERO;

    private static Module cachedTracers;
    private static Module cachedStorageEsp;
    private static Module cachedBlockEsp;

    private static Module tracersModule() {
        Module m = cachedTracers;
        return m != null ? m : (cachedTracers = ModuleRegistry.get("tracers"));
    }

    private static Module storageEspModule() {
        Module m = cachedStorageEsp;
        return m != null ? m : (cachedStorageEsp = ModuleRegistry.get("storage-esp"));
    }

    private static Module blockEspModule() {
        Module m = cachedBlockEsp;
        return m != null ? m : (cachedBlockEsp = ModuleRegistry.get("block-esp"));
    }

    private ModuleWorldRenderer() {
    }

    static void initialize() {
        if (initialized) return;
        initialized = true;
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {

            pendingTracerLines = List.of();
            Minecraft mc = Minecraft.getInstance();
            if (PackHideState.isActive()) return;
            if (mc == null || mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
            boolean drawTracers = ModuleRenderUtil.hasWorldTracerWork();
            Module storage = storageEspModule();
            Module blockEsp = blockEspModule();
            boolean storageEnabled = storage != null && storage.isEnabled();
            boolean blockEnabled = blockEsp != null && blockEsp.isEnabled();
            if (!drawTracers && !storageEnabled && !blockEnabled) return;
            if (ModuleRenderUtil.shouldSuppressEspForUi()) return;
            Module tracer = tracersModule();

            boolean storageFill = storageEnabled && Boolean.parseBoolean(storage.value("fill"));
            boolean storageWire = storageEnabled;
            boolean storageTrace = storageEnabled && Boolean.parseBoolean(storage.value("tracers"));
            boolean blockFill = blockEnabled && Boolean.parseBoolean(blockEsp.value("fill"));
            boolean blockWire = blockEnabled;
            boolean blockTrace = blockEnabled && Boolean.parseBoolean(blockEsp.value("tracers"));

            boolean barrierFill = blockEnabled && Boolean.parseBoolean(blockEsp.value("barriers"));
            if (!drawTracers && !storageFill && !storageWire && !storageTrace
                && !blockFill && !blockWire && !blockTrace && !barrierFill) return;
            CameraRenderState cameraState = context.levelState().cameraRenderState;
            Vec3 camera = cameraState.pos;
            float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

            Vec3 traceStart = camera.add(cameraForward(cameraState).scale(10.0));
            float tracerWidth = parseFloat(tracer == null ? null : tracer.value("line-width"), 2.0f, 2.0f, 6.0f);
            StorageSnapshot storageFrame = storageEnabled ? storageSnapshot(storage, mc.level, mc.player, tickDelta) : StorageSnapshot.empty();
            StorageSnapshot blockFrame = blockEnabled ? blockSnapshot(blockEsp, mc.level, mc.player) : StorageSnapshot.empty();
            StorageSnapshot barrierFrame = barrierFill ? barrierSnapshot(blockEsp, mc.level, mc.player) : StorageSnapshot.empty();
            List<TracerLine> tracerLines = new ArrayList<>();
            if (drawTracers) {
                collectEntityTracers(mc.level, traceStart, tickDelta, tracerWidth, tracerLines);
            }
            if (storageTrace && storageFrame != null) {
                final float traceW = 2.0f;
                storageFrame.forEachTrace((target, color) -> tracerLines.add(new TracerLine(traceStart, target, color, traceW)));
            }
            if (blockTrace && blockFrame != null) {
                final float traceW = 2.0f;
                blockFrame.forEachTrace((target, color) -> tracerLines.add(new TracerLine(traceStart, target, color, traceW)));
            }
            PoseStack poseStack = context.poseStack();
            if (!tracerLines.isEmpty()) {

                pendingTracerLines = tracerLines;
                pendingTracerCamera = camera;
            }
            if (storageFill || blockFill || barrierFill) {
                context.submitNodeCollector().submitCustomGeometry(poseStack, AutismRenderTypes.storageEspFillSeeThrough(), (pose, buffer) -> {
                    if (storageFill && storageFrame != null) {

                        final float fillOpacity = 0.3f;
                        storageFrame.forEachBox((box, color) -> {
                            AABB moved = box.move(-camera.x, -camera.y, -camera.z);
                            fillBox(pose, buffer, moved, withAlpha(color, fillOpacity));
                        });
                    }
                    if (blockFill && blockFrame != null) {
                        final float fillOpacity = 0.3f;
                        blockFrame.forEachBox((box, color) -> {
                            AABB moved = box.move(-camera.x, -camera.y, -camera.z);
                            fillBox(pose, buffer, moved, withAlpha(color, fillOpacity));
                        });
                    }
                    if (barrierFill && barrierFrame != null) {

                        final float fillOpacity = 0.2f;
                        barrierFrame.forEachBox((box, color) -> {
                            AABB moved = box.move(-camera.x, -camera.y, -camera.z);
                            fillBox(pose, buffer, moved, withAlpha(color, fillOpacity));
                        });
                    }
                });
            }
            if (storageWire || blockWire) {
                context.submitNodeCollector().submitCustomGeometry(poseStack, AutismRenderTypes.storageEspLinesSeeThrough(), (pose, buffer) -> {
                    if (storageWire && storageFrame != null) {

                        final float lineW = 1.5f;
                        storageFrame.forEachBox((box, color) -> {
                            AABB moved = box.move(-camera.x, -camera.y, -camera.z);
                            renderStorageBox(pose, buffer, moved, color, lineW);
                        });
                    }
                    if (blockWire && blockFrame != null) {
                        final float lineW = 1.5f;
                        blockFrame.forEachBox((box, color) -> {
                            AABB moved = box.move(-camera.x, -camera.y, -camera.z);
                            renderStorageBox(pose, buffer, moved, color, lineW);
                        });
                    }
                });
            }
        });
    }

    public static boolean hasPendingTracers() {
        return !pendingTracerLines.isEmpty();
    }

    public static void flushTracers(PoseStack matrices) {
        List<TracerLine> lines = pendingTracerLines;
        if (lines.isEmpty()) return;
        pendingTracerLines = List.of();
        Vec3 cam = pendingTracerCamera;
        AutismBufferSource bufferSource = new AutismBufferSource();
        VertexConsumer buffer = bufferSource.getBuffer(AutismRenderTypes.tracerEspLines());
        PoseStack.Pose pose = matrices.last();
        for (TracerLine seg : lines) {
            drawTracerLine(pose, buffer,
                seg.from().x - cam.x, seg.from().y - cam.y, seg.from().z - cam.z,
                seg.to().x - cam.x, seg.to().y - cam.y, seg.to().z - cam.z,
                seg.color(), seg.width());
        }
        bufferSource.uploadAndDraw();
    }

    private static void drawTracerLine(PoseStack.Pose entry, VertexConsumer buffer,
                                       double x1, double y1, double z1,
                                       double x2, double y2, double z2, int color, float width) {
        Vector3f normal = new Vector3f((float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1));
        if (normal.lengthSquared() <= 1.0E-8f) return;
        normal.normalize();
        float fx1 = (float) x1, fy1 = (float) y1, fz1 = (float) z1;
        float fx2 = (float) x2, fy2 = (float) y2, fz2 = (float) z2;
        buffer.addVertex(entry, fx1, fy1, fz1).setColor(color).setNormal(entry, normal).setLineWidth(width);

        float t = new Vector3f(fx1, fy1, fz1).negate().dot(normal);
        float length = new Vector3f(fx2, fy2, fz2).sub(fx1, fy1, fz1).length();
        if (t > 0 && t < length) {
            Vector3f closeToCam = new Vector3f(normal).mul(t).add(fx1, fy1, fz1);
            buffer.addVertex(entry, closeToCam.x, closeToCam.y, closeToCam.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
            buffer.addVertex(entry, closeToCam.x, closeToCam.y, closeToCam.z).setColor(color).setNormal(entry, normal).setLineWidth(width);
        }

        buffer.addVertex(entry, fx2, fy2, fz2).setColor(color).setNormal(entry, normal).setLineWidth(width);
    }

    private static Vec3 cameraForward(CameraRenderState camera) {
        if (camera == null) return new Vec3(0.0, 0.0, -1.0);
        Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F);
        camera.orientation.transform(forward);
        Vec3 result = new Vec3(forward.x, forward.y, forward.z);
        return result.lengthSqr() <= 1.0E-8 ? Vec3.directionFromRotation(camera.xRot, camera.yRot) : result.normalize();
    }

    private static void collectEntityTracers(ClientLevel level, Vec3 from, float tickDelta, float width, List<TracerLine> out) {
        if (level == null) return;
        for (Entity entity : level.entitiesForRendering()) {
            if (!ModuleRenderUtil.shouldTrace(entity)) continue;
            Vec3 base = stableInterpolatedPosition(entity, tickDelta);
            Vec3 top = base.add(0.0, entity.getBbHeight(), 0.0);
            int color = ModuleRenderUtil.tracerColor(entity);
            if (from.distanceToSqr(base) > 1.0E-8) out.add(new TracerLine(from, base, color, width));
            if (base.distanceToSqr(top) > 1.0E-8) out.add(new TracerLine(base, top, color, width));
        }
    }

    private static Vec3 stableInterpolatedPosition(Entity entity, float tickDelta) {
        double dx = entity.getX() - entity.xOld;
        double dy = entity.getY() - entity.yOld;
        double dz = entity.getZ() - entity.zOld;
        if (entity.tickCount <= 1 || dx * dx + dy * dy + dz * dz > TELEPORT_SNAP_DISTANCE_SQ) {
            return entity.position();
        }
        return new Vec3(
            Mth.lerp(tickDelta, entity.xOld, entity.getX()),
            Mth.lerp(tickDelta, entity.yOld, entity.getY()),
            Mth.lerp(tickDelta, entity.zOld, entity.getZ())
        );
    }

    private static void renderEntityBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        renderStorageBox(pose, buffer, box, color, 1.5f);
    }

    private static void renderStorageBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color, float width) {
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        line(pose, buffer, x1, y1, z1, x2, y1, z1, color, width);
        line(pose, buffer, x2, y1, z1, x2, y1, z2, color, width);
        line(pose, buffer, x2, y1, z2, x1, y1, z2, color, width);
        line(pose, buffer, x1, y1, z2, x1, y1, z1, color, width);
        line(pose, buffer, x1, y2, z1, x2, y2, z1, color, width);
        line(pose, buffer, x2, y2, z1, x2, y2, z2, color, width);
        line(pose, buffer, x2, y2, z2, x1, y2, z2, color, width);
        line(pose, buffer, x1, y2, z2, x1, y2, z1, color, width);
        line(pose, buffer, x1, y1, z1, x1, y2, z1, color, width);
        line(pose, buffer, x2, y1, z1, x2, y2, z1, color, width);
        line(pose, buffer, x2, y1, z2, x2, y2, z2, color, width);
        line(pose, buffer, x1, y1, z2, x1, y2, z2, color, width);
    }

    private static final double TELEPORT_SNAP_DISTANCE_SQ = 16.0 * 16.0;

    private static void clippedLine(PoseStack.Pose pose, VertexConsumer buffer,
                                    Vec3 a, Vec3 b, Vec3 planePoint, Vec3 forward,
                                    int color, float width) {
        double da = signedDistanceFromPlane(a, planePoint, forward);
        double db = signedDistanceFromPlane(b, planePoint, forward);
        if (da < 0.0 && db < 0.0) return;
        double ax = a.x, ay = a.y, az = a.z;
        double bx = b.x, by = b.y, bz = b.z;
        if (da < 0.0) {
            double t = -da / (db - da);
            ax = a.x + (b.x - a.x) * t;
            ay = a.y + (b.y - a.y) * t;
            az = a.z + (b.z - a.z) * t;
        } else if (db < 0.0) {
            double t = -db / (da - db);
            bx = b.x + (a.x - b.x) * t;
            by = b.y + (a.y - b.y) * t;
            bz = b.z + (a.z - b.z) * t;
        }
        AutismWorldGeometry.line(pose, buffer, ax, ay, az, bx, by, bz, color, width);
    }

    private static double signedDistanceFromPlane(Vec3 point, Vec3 planePoint, Vec3 forward) {
        return (point.x - planePoint.x) * forward.x
            + (point.y - planePoint.y) * forward.y
            + (point.z - planePoint.z) * forward.z;
    }

    private static void line(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, int color, float width) {
        AutismWorldGeometry.line(pose, buffer, x1, y1, z1, x2, y2, z2, color, width);
    }

    private static void fillBox(PoseStack.Pose pose, VertexConsumer buffer, AABB box, int color) {
        if (((color >>> 24) & 0xFF) <= 0) return;
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color);
        quad(pose, buffer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.minZ, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color);
        quad(pose, buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, int color) {
        buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color);
        buffer.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color);
        buffer.addVertex(pose, (float) x3, (float) y3, (float) z3).setColor(color);
        buffer.addVertex(pose, (float) x4, (float) y4, (float) z4).setColor(color);
    }

    private static int withAlpha(int color, float alphaMultiplier) {
        int alpha = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * alphaMultiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float parseFloat(String value, float fallback, float min, float max) {
        try {
            return Mth.clamp(Float.parseFloat(value), min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static StorageSnapshot storageSnapshot(Module module, ClientLevel level, Player player, float tickDelta) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = storageSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleStorageEsp.collectBoth(
            module,
            level,
            player,
            tickDelta,
            (box, color) -> boxes.add(new StorageBox(box, color)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = new StorageSnapshot(gameTime, revision, chunkX, chunkZ, List.copyOf(boxes), List.copyOf(traces));
        storageSnapshot = next;
        AutismPerf.endJoinSpike("join.storageEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static StorageSnapshot blockSnapshot(Module module, ClientLevel level, Player player) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = blockSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleBlockEsp.collectBoth(
            module,
            level,
            player,
            (box, color) -> boxes.add(new StorageBox(box, color)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = new StorageSnapshot(gameTime, revision, chunkX, chunkZ, List.copyOf(boxes), List.copyOf(traces));
        blockSnapshot = next;
        AutismPerf.endJoinSpike("join.blockEsp.scan", perf, 6_000_000L);
        return next;
    }

    private static StorageSnapshot barrierSnapshot(Module module, ClientLevel level, Player player) {
        if (module == null || level == null || player == null) return StorageSnapshot.empty();
        long gameTime = level.getGameTime();
        int revision = ModuleRegistry.revision();
        int chunkX = player.chunkPosition().x();
        int chunkZ = player.chunkPosition().z();
        StorageSnapshot cached = barrierSnapshot;
        if (cached.matches(gameTime, revision, chunkX, chunkZ)) return cached;

        long perf = AutismPerf.beginJoin();
        List<StorageBox> boxes = new ArrayList<>();
        List<StorageTrace> traces = new ArrayList<>();
        ModuleBlockEsp.collectBarriers(
            module,
            level,
            player,
            (box, color) -> boxes.add(new StorageBox(box, color)),
            (target, color) -> traces.add(new StorageTrace(target, color))
        );
        StorageSnapshot next = new StorageSnapshot(gameTime, revision, chunkX, chunkZ, List.copyOf(boxes), List.copyOf(traces));
        barrierSnapshot = next;
        AutismPerf.endJoinSpike("join.barrierEsp.scan", perf, 6_000_000L);
        return next;
    }

    private record StorageSnapshot(long gameTime, int revision, int chunkX, int chunkZ,
                                   List<StorageBox> boxes, List<StorageTrace> traces) {
        private static StorageSnapshot empty() {
            return new StorageSnapshot(Long.MIN_VALUE, -1, Integer.MIN_VALUE, Integer.MIN_VALUE, List.of(), List.of());
        }

        private boolean matches(long gameTime, int revision, int chunkX, int chunkZ) {
            return this.gameTime == gameTime && this.revision == revision && this.chunkX == chunkX && this.chunkZ == chunkZ;
        }

        private void forEachBox(java.util.function.BiConsumer<AABB, Integer> consumer) {
            for (StorageBox box : boxes) consumer.accept(box.box(), box.color());
        }

        private void forEachTrace(java.util.function.BiConsumer<Vec3, Integer> consumer) {
            for (StorageTrace trace : traces) consumer.accept(trace.target(), trace.color());
        }
    }

    private record StorageBox(AABB box, int color) {
    }

    private record StorageTrace(Vec3 target, int color) {
    }

    private record TracerLine(Vec3 from, Vec3 to, int color, float width) {
    }

}
