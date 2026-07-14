
package autismclient.modules;
import autismclient.api.module.*;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.Vec3;

public final class BoatFlyModule extends Module {
    private static BoatFlyModule instance;

    private int antiKickTicks;
    private double lastPacketY = Double.MAX_VALUE;
    private boolean restorePacketPending;
    private boolean sendingSynthetic;

    public BoatFlyModule() {
        super("boat-fly", "Boat Fly", ModuleCategory.MOVEMENT, "Lets ridden vehicles fly.");
        instance = this;

        add(new BoolSetting("speed", "Speed", false)
            .group("Speed").description("Boost horizontal speed.").build());
        add(new DoubleSetting("horizontal-speed", "Horizontal Speed", 10.0, 0.0, 100.0, 0.5)
            .sliderRange(0.0, 50.0).group("Speed")
            .visibleWhen(() -> bool("speed")).description("Sets horizontal speed.").build());
        add(new BoolSetting("only-on-ground", "Only Ground", false)
            .group("Speed").visibleWhen(() -> bool("speed")).description("Require ground contact.").build());
        add(new BoolSetting("in-water", "In Water", true)
            .group("Speed").visibleWhen(() -> bool("speed")).description("Allow water speed.").build());

        add(new DoubleSetting("vertical-speed", "Vertical Speed", 6.0, 0.0, 100.0, 0.5)
            .sliderRange(0.0, 20.0).group("Flight")
            .description("Sets vertical speed.").build());
        add(new DoubleSetting("fall-speed", "Fall Speed", 0.0, 0.0, 100.0, 0.25)
            .sliderRange(0.0, 20.0).group("Flight")
            .description("Sets downward drift.").build());
        add(new BoolSetting("anti-kick", "Anti Kick", true)
            .group("Flight").description("Reduce flight kicks.").build());
        add(new IntSetting("anti-kick-delay", "Anti Kick Delay", 40, 1, 80, 1)
            .group("Flight").visibleWhen(() -> bool("anti-kick"))
            .description("Sets anti-kick interval.").build());

        add(new BoolSetting("lock-yaw", "Lock Yaw", true)
            .group("Control").description("Match your view.").build());
        add(new BoolSetting("cancel-server-packets", "Cancel Server Packets", true)
            .group("Control").description("Ignore server corrections.").build());
    }

    @Override
    public void onEnable() {
        resetAntiKick();
    }

    @Override
    public void onDisable() {
        resetAntiKick();
    }

    @Override
    public void onGameLeft() {
        resetAntiKick();
    }

    @Override
    protected void onOptionValueChanged(String optionId) {
        if ("anti-kick-delay".equals(optionId)) antiKickTicks = integer("anti-kick-delay");
    }

    @Override
    protected void onSettingsReset() {
        resetAntiKick();
    }

    @Override
    public void preMovementTick() {
        if (MC.player == null || MC.getConnection() == null) return;

        // restore y after antikick
        if (restorePacketPending) {
            Entity vehicle = MC.player.getVehicle();
            if (isControlling(vehicle)) {
                sendSynthetic(new ServerboundMoveVehiclePacket(
                    new Vec3(vehicle.getX(), lastPacketY, vehicle.getZ()),
                    vehicle.getYRot(), vehicle.getXRot(), vehicle.onGround()));
            }
            restorePacketPending = false;
        }
        if (antiKickTicks > 0) antiKickTicks--;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (sendingSynthetic || !bool("anti-kick")) return false;
        if (!(packet instanceof ServerboundMoveVehiclePacket pkt)) return false;

        Entity vehicle = MC.player == null ? null : MC.player.getVehicle();
        if (!isControlling(vehicle) || vehicle.isFlyingVehicle() || !isOnAir(vehicle)) {
            lastPacketY = pkt.position().y;
            return false;
        }

        double curY = pkt.position().y;
        if (antiKickTicks <= 0 && !restorePacketPending && shouldFlyDown(curY)) {
            double base = lastPacketY == Double.MAX_VALUE ? curY : lastPacketY;
            // send packet with slightly lower y so server doesnt kick
            ServerboundMoveVehiclePacket lowered = new ServerboundMoveVehiclePacket(
                new Vec3(pkt.position().x, base - 0.03130D, pkt.position().z),
                pkt.yRot(), pkt.xRot(), pkt.onGround());
            sendSynthetic(lowered);
            restorePacketPending = true;
            antiKickTicks = integer("anti-kick-delay");
            lastPacketY = curY;
            return true;
        }

        lastPacketY = curY;
        return false;
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        return bool("cancel-server-packets") && packet instanceof ClientboundMoveVehiclePacket;
    }

    static Vec3 modifyVehicleMovement(Entity vehicle, MoverType type, Vec3 movement) {
        BoatFlyModule m = instance;
        if (!isActive(m) || type != MoverType.SELF || !m.isControlling(vehicle)) return movement;

        double velX = movement.x;
        double velY = 0.0;
        double velZ = movement.z;

        if (m.bool("lock-yaw")) vehicle.setYRot(MC.player.getYRot());

        // speed stuff
        if (m.bool("speed")
            && (!m.bool("only-on-ground") || vehicle.onGround() || vehicle.isFlyingVehicle())
            && (m.bool("in-water") || !vehicle.isInWater())) {
            Vec3 hVel = horizontalVelocity(m.decimal("horizontal-speed"));
            velX = hVel.x;
            velZ = hVel.z;
        }

        // flight
        if (MC.options.keyJump.isDown()) velY += m.decimal("vertical-speed") / 20.0;
        if (MC.options.keySprint.isDown()) velY -= m.decimal("vertical-speed") / 20.0;
        else velY -= m.decimal("fall-speed") / 20.0;

        return new Vec3(velX, velY, velZ);
    }

    private static boolean isActive(BoatFlyModule m) {
        return m != null && m.isEnabled() && !PackHideState.isHardLocked() && MC.player != null;
    }

    private boolean isControlling(Entity vehicle) {
        return vehicle != null && MC.player != null && MC.player.getVehicle() == vehicle
            && vehicle.getControllingPassenger() == MC.player;
    }

    private void resetAntiKick() {
        antiKickTicks = integer("anti-kick-delay");
        lastPacketY = Double.MAX_VALUE;
        restorePacketPending = false;
        sendingSynthetic = false;
    }

    private void sendSynthetic(ServerboundMoveVehiclePacket packet) {
        if (MC.getConnection() == null) return;
        sendingSynthetic = true;
        try {
            MC.getConnection().send(packet);
        } finally {
            sendingSynthetic = false;
        }
    }

    private boolean shouldFlyDown(double curY) {
        return curY >= lastPacketY || lastPacketY - curY < 0.03130D;
    }

    private static boolean isOnAir(Entity e) {
        return e.level().getBlockStates(e.getBoundingBox().inflate(0.0625).expandTowards(0.0, -0.55, 0.0))
            .allMatch(BlockBehaviour.BlockStateBase::isAir);
    }

    // wasd -> world direction
    private static Vec3 horizontalVelocity(double bps) {
        double spd = bps / 20.0;
        float fwd = 0.0F;
        float side = 0.0F;
        if (MC.options.keyUp.isDown()) fwd++;
        if (MC.options.keyDown.isDown()) fwd--;
        if (MC.options.keyLeft.isDown()) side++;
        if (MC.options.keyRight.isDown()) side--;
        if (fwd == 0.0F && side == 0.0F) return Vec3.ZERO;

        double len = Math.sqrt(fwd * fwd + side * side);
        fwd /= (float) len;
        side /= (float) len;
        double yaw = Math.toRadians(MC.player.getYRot());
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        return new Vec3((side * cos - fwd * sin) * spd, 0.0, (fwd * cos + side * sin) * spd);
    }
}
