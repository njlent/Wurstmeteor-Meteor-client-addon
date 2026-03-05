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
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

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
        .description("Hand used for feeding.")
        .defaultValue(HandMode.Main)
        .build()
    );

    private final Random random = new Random();
    private AnimalEntity renderTarget;

    public FeedAuraModule() {
        super(WurstMeteorAddon.CATEGORY, "feed-aura", "Automatically feeds nearby breedable animals.");
    }

    @Override
    public void onDeactivate() {
        renderTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem()) return;

        Hand usedHand = hand.get().toMinecraft();
        ItemStack held = usedHand == Hand.MAIN_HAND ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        if (held.isEmpty()) {
            renderTarget = null;
            return;
        }

        List<AnimalEntity> targets = collectTargets(held);
        if (targets.isEmpty()) {
            renderTarget = null;
            return;
        }

        AnimalEntity target = targets.get(random.nextInt(targets.size()));
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

        double x = MathHelper.lerp(event.tickDelta, renderTarget.lastRenderX, renderTarget.getX()) - renderTarget.getX();
        double y = MathHelper.lerp(event.tickDelta, renderTarget.lastRenderY, renderTarget.getY()) - renderTarget.getY();
        double z = MathHelper.lerp(event.tickDelta, renderTarget.lastRenderZ, renderTarget.getZ()) - renderTarget.getZ();

        Box box = renderTarget.getBoundingBox().offset(x, y, z);
        event.renderer.box(box, side, line, ShapeMode.Both, 0);
    }

    private List<AnimalEntity> collectTargets(ItemStack held) {
        double rangeSq = range.get() * range.get();
        List<AnimalEntity> targets = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof AnimalEntity animal)) continue;
            if (mc.player.squaredDistanceTo(animal) > rangeSq) continue;
            if (!animal.isBreedingItem(held)) continue;
            if (!animal.canEat()) continue;
            if (ignoreBabies.get() && animal.isBaby()) continue;
            if (filterUntamed.get() && isUntamed(animal)) continue;
            if (filterHorseLike.get() && animal instanceof AbstractHorseEntity) continue;

            targets.add(animal);
        }

        return targets;
    }

    private void feed(AnimalEntity target, Hand hand) {
        if (!target.isAlive()) return;

        EntityHitResult hitResult = new EntityHitResult(target, target.getBoundingBox().getCenter());
        ActionResult result = mc.interactionManager.interactEntityAtLocation(mc.player, target, hitResult, hand);
        if (!result.isAccepted()) result = mc.interactionManager.interactEntity(mc.player, target, hand);

        if (result.isAccepted()) mc.player.swingHand(hand);
    }

    private boolean isUntamed(AnimalEntity animal) {
        if (animal instanceof AbstractHorseEntity horse && !horse.isTame()) return true;
        return animal instanceof TameableEntity tameable && tameable.getOwner() == null;
    }

    public enum HandMode {
        Main(Hand.MAIN_HAND),
        Off(Hand.OFF_HAND);

        private final Hand hand;

        HandMode(Hand hand) {
            this.hand = hand;
        }

        public Hand toMinecraft() {
            return hand;
        }
    }
}
