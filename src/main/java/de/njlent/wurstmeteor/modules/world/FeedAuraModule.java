package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FeedAuraModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance for feeding targets.")
        .defaultValue(5.0)
        .min(0.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Boolean> ignoreBabies = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-babies")
        .description("Skips baby animals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterUntamed = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-untamed")
        .description("Skips untamed horses and tameable mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> filterHorseLike = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-horse-like")
        .description("Skips horses, donkeys, llamas, and similar entities.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates before feeding targets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<HandMode> hand = sgGeneral.add(new EnumSetting.Builder<HandMode>()
        .name("hand")
        .description("InteractionHand used for feeding.")
        .defaultValue(HandMode.Main)
        .build()
    );

    private final Random random = new Random();
    private Animal renderTarget;

    public FeedAuraModule() {
        super(WurstMeteorAddon.CATEGORY, "feed-aura", "Automatically feeds nearby breedable animals.");
    }

    @Override
    public void onDeactivate() {
        renderTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gameMode.isDestroying() || mc.player.isUsingItem()) return;

        InteractionHand usedHand = hand.get().toMinecraft();
        ItemStack held = usedHand == InteractionHand.MAIN_HAND ? mc.player.getMainHandItem() : mc.player.getOffhandItem();
        if (held.isEmpty()) {
            renderTarget = null;
            return;
        }

        List<Animal> targets = collectTargets(held);
        if (targets.isEmpty()) {
            renderTarget = null;
            return;
        }

        Animal target = targets.get(random.nextInt(targets.size()));
        renderTarget = target;

        if (rotate.get()) {
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body), 50, () -> feed(target, usedHand));
        } else {
            feed(target, usedHand);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (renderTarget == null || !renderTarget.isAlive()) return;

        float maxHealth = renderTarget.getMaxHealth();
        float healthPercent = maxHealth <= 0.0F ? 1.0F : renderTarget.getHealth() / maxHealth;

        int red = (int) (255 * (1.0F - healthPercent));
        int green = (int) (255 * healthPercent);

        Color side = new Color(red, green, 0, 40);
        Color line = new Color(red, green, 0, 130);

        double x = Mth.lerp(event.tickDelta, renderTarget.xOld, renderTarget.getX()) - renderTarget.getX();
        double y = Mth.lerp(event.tickDelta, renderTarget.yOld, renderTarget.getY()) - renderTarget.getY();
        double z = Mth.lerp(event.tickDelta, renderTarget.zOld, renderTarget.getZ()) - renderTarget.getZ();

        AABB box = renderTarget.getBoundingBox().move(x, y, z);
        event.renderer.box(box, side, line, ShapeMode.Both, 0);
    }

    private List<Animal> collectTargets(ItemStack held) {
        double rangeSq = range.get() * range.get();
        List<Animal> targets = new ArrayList<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Animal animal)) continue;
            if (mc.player.distanceToSqr(animal) > rangeSq) continue;
            if (!animal.isFood(held)) continue;
            if (!animal.canFallInLove()) continue;
            if (ignoreBabies.get() && animal.isBaby()) continue;
            if (filterUntamed.get() && isUntamed(animal)) continue;
            if (filterHorseLike.get() && animal instanceof AbstractHorse) continue;

            targets.add(animal);
        }

        return targets;
    }

    private void feed(Animal target, InteractionHand hand) {
        if (!target.isAlive()) return;

        EntityHitResult hitResult = new EntityHitResult(target, target.getBoundingBox().getCenter());
        InteractionResult result = mc.gameMode.interact(mc.player, target, hitResult, hand);

        if (result.consumesAction()) mc.player.swing(hand);
    }

    private boolean isUntamed(Animal animal) {
        if (animal instanceof AbstractHorse horse && !horse.isTamed()) return true;
        return animal instanceof TamableAnimal tameable && tameable.getOwner() == null;
    }

    public enum HandMode {
        Main(InteractionHand.MAIN_HAND),
        Off(InteractionHand.OFF_HAND);

        private final InteractionHand hand;

        HandMode(InteractionHand hand) {
            this.hand = hand;
        }

        public InteractionHand toMinecraft() {
            return hand;
        }
    }
}
