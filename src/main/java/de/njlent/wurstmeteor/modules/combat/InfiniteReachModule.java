package de.njlent.wurstmeteor.modules.combat;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InfiniteReachModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Vanilla uses shorter reach and fewer packets. Paper uses longer reach with clip-up packets.")
        .defaultValue(Mode.Vanilla)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings the main hand when an action lands.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> homeTeleport = sgGeneral.add(new BoolSetting.Builder()
        .name("home-teleport")
        .description("Teleports back after each action.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clipUp = sgGeneral.add(new BoolSetting.Builder()
        .name("clip-up")
        .description("Sends elevated movement packets in Paper mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<IntRange> packetPreset = sgGeneral.add(new EnumSetting.Builder<IntRange>()
        .name("packet-preset")
        .description("Movement packet count preset.")
        .defaultValue(IntRange.Normal)
        .build()
    );

    private final Setting<Double> vanillaDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("vanilla-distance")
        .description("Maximum reach in Vanilla mode.")
        .defaultValue(22.0)
        .range(1.0, 22.0)
        .sliderRange(1.0, 22.0)
        .build()
    );

    private final Setting<Double> paperDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("paper-distance")
        .description("Maximum reach in Paper mode.")
        .defaultValue(59.0)
        .range(1.0, 99.0)
        .sliderRange(1.0, 99.0)
        .build()
    );

    private final Setting<Boolean> onlyMace = sgGeneral.add(new BoolSetting.Builder()
        .name("only-mace")
        .description("Only attacks entities while holding a mace.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> throughBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("through-blocks")
        .description("Skips clear-path checks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> blockDelay = sgGeneral.add(new IntSetting.Builder()
        .name("block-delay")
        .description("Ticks between block actions.")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> entityDelay = sgGeneral.add(new IntSetting.Builder()
        .name("entity-delay")
        .description("Ticks between entity actions.")
        .defaultValue(5)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> renderEntity = sgRender.add(new BoolSetting.Builder()
        .name("render-entity")
        .description("Renders the entity target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> entityColor = sgRender.add(new ColorSetting.Builder()
        .name("entity-color")
        .description("Entity target color.")
        .defaultValue(new SettingColor(255, 0, 0, 120))
        .build()
    );

    private final Setting<Boolean> renderBlock = sgRender.add(new BoolSetting.Builder()
        .name("render-block")
        .description("Renders the block target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> blockColor = sgRender.add(new ColorSetting.Builder()
        .name("block-color")
        .description("Block target color.")
        .defaultValue(new SettingColor(255, 0, 255, 120))
        .build()
    );

    private Entity entityTarget;
    private BlockHitResult blockTarget;
    private int blockTicks;
    private int entityTicks;

    public InfiniteReachModule() {
        super(WurstMeteorAddon.CATEGORY, "infinite-reach", "Teleports by packet to attack, use, or mine distant targets.");
    }

    @Override
    public void onActivate() {
        entityTarget = null;
        blockTarget = null;
        blockTicks = 0;
        entityTicks = 0;
    }

    @Override
    public void onDeactivate() {
        entityTarget = null;
        blockTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.player.connection == null) return;
        if (onlyMace.get() && !mc.player.getMainHandItem().is(Items.MACE)) {
            entityTarget = null;
            blockTarget = null;
            return;
        }

        if (blockTicks > 0) blockTicks--;
        if (entityTicks > 0) entityTicks--;

        updateTargets();

        boolean attack = mc.options.keyAttack.isDown();
        boolean use = mc.options.keyUse.isDown();
        if (!attack && !use) return;

        if (entityTarget != null && entityTicks <= 0) {
            if (attack || use) {
                hitEntity(entityTarget, attack);
                entityTicks = entityDelay.get();
                return;
            }
        }

        if (blockTarget != null && blockTicks <= 0) {
            hitBlock(blockTarget, attack);
            blockTicks = blockDelay.get();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (renderEntity.get() && entityTarget != null) {
            Color color = new Color(entityColor.get());
            event.renderer.box(entityTarget.getBoundingBox(), color.a(45), color.a(180), ShapeMode.Both, 0);
        }

        if (renderBlock.get() && blockTarget != null) {
            Color color = new Color(blockColor.get());
            event.renderer.box(new AABB(blockTarget.getBlockPos()), color.a(45), color.a(180), ShapeMode.Both, 0);
        }
    }

    private void updateTargets() {
        double maxDistance = maxDistance();
        Vec3 eyes = mc.player.getEyePosition(1.0F);
        Vec3 end = eyes.add(mc.player.getViewVector(1.0F).scale(maxDistance));

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            mc.player,
            eyes,
            end,
            mc.player.getBoundingBox().inflate(maxDistance),
            entity -> entity != mc.player && entity.isAlive() && entity.isAttackable() && !entity.isInvulnerable(),
            maxDistance * maxDistance
        );

        entityTarget = entityHit == null ? null : entityHit.getEntity();

        BlockHitResult blockHit = mc.level.clip(new ClipContext(eyes, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        blockTarget = blockHit.getType() == HitResult.Type.BLOCK && !mc.level.getBlockState(blockHit.getBlockPos()).isAir() ? blockHit : null;
    }

    private void hitEntity(Entity target, boolean attack) {
        if (onlyMace.get() && target instanceof Player player && player.isBlocking()) return;

        Vec3 start = mc.player.position();
        Vec3 actionPos = findNearestValidPos(target.getBoundingBox().getCenter());
        if (actionPos == null || !canReach(start, actionPos)) return;

        teleportTo(actionPos);

        if (!homeTeleport.get()) mc.player.setPos(actionPos.x, actionPos.y, actionPos.z);
        if (attack) mc.gameMode.attack(mc.player, target);
        else mc.gameMode.interact(mc.player, target, new EntityHitResult(target), InteractionHand.MAIN_HAND);
        swingHand();
        if (homeTeleport.get()) returnHome(start);
    }

    private void hitBlock(BlockHitResult hit, boolean attack) {
        Vec3 start = mc.player.position();
        Vec3 actionPos = findNearestValidPos(hit.getLocation());
        if (actionPos == null || !canReach(start, actionPos)) return;

        teleportTo(actionPos);

        if (!homeTeleport.get()) mc.player.setPos(actionPos.x, actionPos.y, actionPos.z);
        if (attack) {
            mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, hit.getBlockPos(), hit.getDirection(), 0));
            mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, hit.getBlockPos(), hit.getDirection(), 0));
        } else {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
        swingHand();
        if (homeTeleport.get()) returnHome(start);
    }

    private void teleportTo(Vec3 pos) {
        spamMovementPackets();

        if (mode.get() == Mode.Paper && clipUp.get()) {
            double up = Math.min(maxDistance(), Math.max(2.0, mc.player.position().distanceTo(pos) * 0.5));
            sendMove(mc.player.position().add(0, up, 0));
            sendMove(pos.add(0, up, 0));
        }

        sendMove(pos);
    }

    private void returnHome(Vec3 start) {
        if (mode.get() == Mode.Paper && clipUp.get()) {
            double up = Math.min(maxDistance(), 4.0);
            sendMove(mc.player.position().add(0, up, 0));
            sendMove(start.add(0, up, 0));
        }

        sendMove(start);
        Vec3 offset = getOffset(start);
        sendMove(offset);
        mc.player.setPos(offset.x, offset.y, offset.z);
    }

    private void spamMovementPackets() {
        int packets = packetPreset.get().packets(mode.get());
        for (int i = 0; i < packets; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, mc.player.horizontalCollision));
        }
    }

    private void sendMove(Vec3 pos) {
        mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(pos.x, pos.y, pos.z, mc.player.getYRot(), mc.player.getXRot(), false, false));
    }

    private boolean canReach(Vec3 start, Vec3 pos) {
        if (start.distanceTo(pos) > maxDistance() + 0.5) return false;
        return throughBlocks.get() || hasClearPath(start, pos);
    }

    private boolean hasClearPath(Vec3 start, Vec3 end) {
        int steps = Math.max(10, (int) (start.distanceTo(end) * 2.5));
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3 sample = start.lerp(end, t);
            if (invalid(sample)) return false;
        }
        return true;
    }

    private Vec3 findNearestValidPos(Vec3 preferred) {
        if (!invalid(preferred)) return preferred;

        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    Vec3 test = preferred.add(dx * 0.5, dy * 0.5, dz * 0.5);
                    if (invalid(test)) continue;
                    double distance = test.distanceToSqr(preferred);
                    if (distance < bestDistance) {
                        best = test;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }

    private boolean invalid(Vec3 pos) {
        AABB targetBox = mc.player.getBoundingBox().move(pos.x - mc.player.getX(), pos.y - mc.player.getY(), pos.z - mc.player.getZ());
        int minX = (int) Math.floor(targetBox.minX);
        int minY = (int) Math.floor(targetBox.minY);
        int minZ = (int) Math.floor(targetBox.minZ);
        int maxX = (int) Math.floor(targetBox.maxX);
        int maxY = (int) Math.floor(targetBox.maxY);
        int maxZ = (int) Math.floor(targetBox.maxZ);

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blockPos.set(x, y, z);
                    BlockState state = mc.level.getBlockState(blockPos);
                    if (state.is(Blocks.LAVA) || !state.getCollisionShape(mc.level, blockPos).isEmpty()) return true;
                }
            }
        }

        return false;
    }

    private Vec3 getOffset(Vec3 base) {
        double dx = 0.05;
        double dy = 0.01;
        List<Vec3> offsets = new ArrayList<>(List.of(
            base.add(dx, dy, 0), base.add(-dx, dy, 0), base.add(0, dy, dx), base.add(0, dy, -dx),
            base.add(dx, dy, dx), base.add(-dx, dy, -dx), base.add(-dx, dy, dx), base.add(dx, dy, -dx)
        ));
        Collections.shuffle(offsets);

        for (Vec3 offset : offsets) {
            if (!invalid(offset)) return offset;
        }

        return base;
    }

    private void swingHand() {
        if (!swing.get()) return;
        mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private double maxDistance() {
        return mode.get() == Mode.Vanilla ? vanillaDistance.get() : paperDistance.get();
    }

    public enum Mode {
        Vanilla,
        Paper
    }

    public enum IntRange {
        Low(2, 4),
        Normal(4, 7),
        High(5, 10);

        private final int vanillaPackets;
        private final int paperPackets;

        IntRange(int vanillaPackets, int paperPackets) {
            this.vanillaPackets = vanillaPackets;
            this.paperPackets = paperPackets;
        }

        public int packets(Mode mode) {
            return mode == Mode.Vanilla ? vanillaPackets : paperPackets;
        }
    }
}
