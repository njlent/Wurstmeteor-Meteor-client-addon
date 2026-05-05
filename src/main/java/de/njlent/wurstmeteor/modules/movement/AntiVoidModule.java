package de.njlent.wurstmeteor.modules.movement;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Flight;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AntiVoidModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> useAirWalk = sgGeneral.add(new BoolSetting.Builder()
        .name("use-air-walk")
        .description("Holds you in the air instead of rubberbanding to the last safe position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> detectLava = sgGeneral.add(new BoolSetting.Builder()
        .name("detect-lava")
        .description("Also rescues when lava is directly below you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> gateAtVoidLevel = sgGeneral.add(new BoolSetting.Builder()
        .name("respond-only-at-void-level")
        .description("Only triggers at the fixed void level, or just above lava.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> useFlight = sgGeneral.add(new BoolSetting.Builder()
        .name("use-flight")
        .description("Enables Meteor Flight instead of rubberbanding or air-walking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> lavaBuffer = sgGeneral.add(new IntSetting.Builder()
        .name("lava-buffer")
        .description("Blocks above lava to trigger rescue.")
        .defaultValue(2)
        .range(0, 12)
        .sliderRange(0, 12)
        .build()
    );

    private Vec3 lastSafePos;
    private boolean airWalkActive;
    private double airWalkY;
    private boolean jumpWasDown;

    public AntiVoidModule() {
        super(WurstMeteorAddon.CATEGORY, "anti-void", "Prevents falling into the void or lava.");
    }

    @Override
    public void onActivate() {
        lastSafePos = null;
        airWalkActive = false;
        airWalkY = Double.NaN;
        jumpWasDown = false;
    }

    @Override
    public void onDeactivate() {
        airWalkActive = false;
        airWalkY = Double.NaN;
        jumpWasDown = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;

        if (airWalkActive && isBackOnSurface()) {
            airWalkActive = false;
            airWalkY = Double.NaN;
            jumpWasDown = false;
        }

        if (mc.player.onGround() && !mc.player.isInWater() && !mc.player.isInLava()) lastSafePos = mc.player.position();
        if (mc.player.isFallFlying()) return;

        if (airWalkActive) {
            applyAirWalk();
            return;
        }

        if (mc.player.getDeltaMovement().y >= 0 || mc.player.fallDistance <= 2F) return;
        if (!isOverVoid() && !isOverLava()) return;

        if (useFlight.get()) {
            Flight flight = Modules.get().get(Flight.class);
            if (flight != null && !flight.isActive()) flight.toggle();
            return;
        }

        if (useAirWalk.get()) {
            airWalkActive = true;
            airWalkY = mc.player.getY();
            applyAirWalk();
            return;
        }

        if (lastSafePos == null) return;

        mc.player.setDeltaMovement(0, 0, 0);
        mc.player.fallDistance = 0;
        mc.player.setOnGround(true);
        mc.player.setPos(lastSafePos.x, lastSafePos.y, lastSafePos.z);
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(lastSafePos.x, lastSafePos.y, lastSafePos.z, true, mc.player.horizontalCollision));
    }

    private void applyAirWalk() {
        if (Double.isNaN(airWalkY)) airWalkY = mc.player.getY();

        boolean jumpDown = mc.options.keyJump.isDown();
        if (jumpDown && !jumpWasDown) {
            double targetY = airWalkY + 1.0;
            AABB box = mc.player.getBoundingBox().move(0, targetY - mc.player.getY(), 0);
            if (mc.level.noCollision(mc.player, box)) airWalkY = targetY;
        }
        jumpWasDown = jumpDown;

        Vec3 velocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(velocity.x, 0, velocity.z);
        mc.player.setOnGround(true);
        mc.player.fallDistance = 0;
        if (Math.abs(mc.player.getY() - airWalkY) > 1.0E-4) mc.player.setPos(mc.player.getX(), airWalkY, mc.player.getZ());
    }

    private boolean isBackOnSurface() {
        if (mc.player.onGround()) return true;
        return !mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0, -0.05, 0));
    }

    private boolean isOverVoid() {
        double voidY = fixedVoidLevel();
        if (gateAtVoidLevel.get() && mc.player.getY() > voidY) return false;
        if (mc.player.getY() <= voidY && !mc.player.isInWater() && !mc.player.isInLava()) return true;

        int startY = mc.player.getBlockY();
        int minY = mc.level.getMinY();
        int endY = Math.max(minY, Mth.floor(voidY));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = startY; y >= endY; y--) {
            pos.set(mc.player.getBlockX(), y, mc.player.getBlockZ());
            BlockState state = mc.level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return false;
            if (!state.isAir()) return false;
        }

        return true;
    }

    private boolean isOverLava() {
        if (!detectLava.get()) return false;
        Integer lavaY = lavaYBelow();
        return lavaY != null && mc.player.getY() <= lavaY + lavaBuffer.get();
    }

    private Integer lavaYBelow() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = mc.player.getBlockY(); y >= mc.level.getMinY(); y--) {
            pos.set(mc.player.getBlockX(), y, mc.player.getBlockZ());
            BlockState state = mc.level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return state.getFluidState().is(FluidTags.LAVA) ? y : null;
            if (!state.isAir()) return null;
        }
        return null;
    }

    private double fixedVoidLevel() {
        if (mc.level.dimension() == Level.END || mc.level.dimension() == Level.NETHER) return -60.0;
        return -120.0;
    }
}
