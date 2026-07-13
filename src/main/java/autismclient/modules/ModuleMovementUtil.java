package autismclient.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class ModuleMovementUtil {
    private static final Minecraft MC = Minecraft.getInstance();
    private static volatile SprintState sprintState = SprintState.inactive(-1);
    private static volatile MovementState movementState = MovementState.inactive(-1);

    private ModuleMovementUtil() {
    }

    public static boolean shouldCancelNoFallBounce(Entity entity) {
        return movementState().noFallAntiBounce() && MC != null && entity == MC.player;
    }

    public static float speedTimerMultiplier() {
        MovementState state = movementState();
        BuiltinModules.SpeedModule speed = state.speed();
        if (speed == null || !speed.shouldApplySpeedTimer()) return 1.0f;
        return state.speedTimerMultiplier();
    }

    public static float flightFlyingSpeed() {
        BuiltinModules.FlightModule flight = movementState().flight();
        if (flight != null) return flight.getFlyingSpeed();
        return -1.0f;
    }

    public static boolean flightNoSneak() {
        BuiltinModules.FlightModule flight = movementState().flight();
        return flight != null && flight.noSneak();
    }

    public static boolean sprintKeepsRunning() {
        return sprintIsOmnidirectional();
    }

    public static boolean sprintIsOmnidirectional() {
        return sprintState().omnidirectional();
    }

    public static boolean sprintIgnoresCollision() {
        return sprintState().ignoreCollision();
    }

    public static boolean sprintIgnoresHunger() {
        return sprintState().ignoreHunger();
    }

    public static boolean sprintIgnoresBlindness() {
        return sprintState().ignoreBlindness();
    }

    public static void autoSprintLocalPlayerTick() {
        Module module = ModuleRegistry.get("sprint");
        if (module instanceof BuiltinModules.SprintModule sprint && sprint.isEnabled()) {
            sprint.runWurstAutoSprintTick();
        }
    }

    private static SprintState sprintState() {
        int revision = ModuleRegistry.revision();
        SprintState snapshot = sprintState;
        if (snapshot.revision() == revision) return snapshot;
        Module module = ModuleRegistry.get("sprint");
        if (!(module instanceof BuiltinModules.SprintModule sprint) || !sprint.isEnabled()) {
            snapshot = SprintState.inactive(revision);
        } else {
            snapshot = new SprintState(
                revision,
                sprint.omnidirectional(),
                sprint.ignoreCollision(),
                sprint.ignoreHunger(),
                sprint.ignoreBlindness()
            );
        }
        sprintState = snapshot;
        return snapshot;
    }

    private static MovementState movementState() {
        int revision = ModuleRegistry.revision();
        MovementState snapshot = movementState;
        if (snapshot.revision() == revision) return snapshot;

        Module flightModule = ModuleRegistry.get("flight");
        BuiltinModules.FlightModule flight =
            flightModule instanceof BuiltinModules.FlightModule typed && typed.isEnabled() ? typed : null;

        Module speedModule = ModuleRegistry.get("speed");
        BuiltinModules.SpeedModule speed =
            speedModule instanceof BuiltinModules.SpeedModule typed && typed.isEnabled() ? typed : null;

        float speedTimer = 1.0f;
        if (speed != null) {
            try {
                speedTimer = Math.max(0.01f, Math.min(10.0f, Float.parseFloat(speed.value("timer"))));
            } catch (Exception ignored) {
                speedTimer = 1.0f;
            }
        }

        Module noFall = ModuleRegistry.get("no-fall");
        boolean noFallAntiBounce = noFall != null && noFall.isEnabled() && Boolean.parseBoolean(noFall.value("anti-bounce"));

        snapshot = new MovementState(revision, flight, speed, speedTimer, noFallAntiBounce);
        movementState = snapshot;
        return snapshot;
    }

    public static void preMovementTick() {
        if (!ModuleRegistry.hasPreMovementHooks()) return;
        ModuleRegistry.preMovementTick();
    }

    public static Vec3 onPlayerMove(Entity entity, MoverType type, Vec3 movement) {
        if (entity != MC.player) {
            if (MC.player == null || entity != MC.player.getVehicle() || PackHideState.isActive()) return movement;
            Vec3 adjusted = EntityControlModule.modifyVehicleMovement(entity, type, movement);
            adjusted = BoatFlyModule.modifyVehicleMovement(entity, type, adjusted);
            if (type == MoverType.SELF && adjusted != movement) entity.setDeltaMovement(adjusted);
            return adjusted;
        }
        if (!ModuleRegistry.hasMovementHooks()) return movement;
        Vec3 adjusted = ModuleRegistry.onPlayerMove(type, movement);
        if (type == MoverType.SELF && adjusted != movement) {
            MC.player.setDeltaMovement(adjusted);
        }
        return adjusted;
    }

    public static void applySpeedAfterLiquidTravel(Entity entity) {
        if (entity != MC.player || PackHideState.isActive()) return;
        MovementState state = movementState();
        BuiltinModules.SpeedModule speed = state.speed();
        if (speed == null) return;
        Vec3 movement = entity.getDeltaMovement();
        Vec3 adjusted = speed.afterLiquidTravel(movement);
        if (adjusted != null && adjusted != movement) entity.setDeltaMovement(adjusted);
    }

    private record SprintState(
        int revision,
        boolean omnidirectional,
        boolean ignoreCollision,
        boolean ignoreHunger,
        boolean ignoreBlindness
    ) {
        static SprintState inactive(int revision) {
            return new SprintState(revision, false, false, false, false);
        }
    }

    private record MovementState(
        int revision,
        BuiltinModules.FlightModule flight,
        BuiltinModules.SpeedModule speed,
        float speedTimerMultiplier,
        boolean noFallAntiBounce
    ) {
        static MovementState inactive(int revision) {
            return new MovementState(revision, null, null, 1.0f, false);
        }
    }
}
