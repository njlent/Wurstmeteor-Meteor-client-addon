package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.modules.world.autolibrarian.BlockHitSelection;
import de.njlent.wurstmeteor.modules.world.autolibrarian.BookOffer;
import de.njlent.wurstmeteor.modules.world.autolibrarian.WantedBooks;
import de.njlent.wurstmeteor.util.KeyBindingUtils;
import de.njlent.wurstmeteor.util.RotationPackets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.VillagerProfession;

import java.util.*;

public class AutoLibrarianModule extends Module {
    private static final List<String> DEFAULT_WANTED_BOOKS = List.of(
        "minecraft:depth_strider;3",
        "minecraft:efficiency;5",
        "minecraft:feather_falling;4",
        "minecraft:fortune;3",
        "minecraft:looting;3",
        "minecraft:mending;1",
        "minecraft:protection;4",
        "minecraft:respiration;3",
        "minecraft:sharpness;5",
        "minecraft:silk_touch;1",
        "minecraft:unbreaking;3"
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> wantedBooks = sgGeneral.add(new StringListSetting.Builder()
        .name("wanted-books")
        .description("Wanted books as id;level or id;level;maxPrice.")
        .defaultValue(DEFAULT_WANTED_BOOKS)
        .build()
    );

    private final Setting<Boolean> lockInTrade = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-in-trade")
        .description("Buys one trade after finding a matching book.")
        .defaultValue(false)
        .build()
    );

    private final Setting<UpdateBooksMode> updateBooks = sgGeneral.add(new EnumSetting.Builder<UpdateBooksMode>()
        .name("update-books")
        .description("How to update the wanted book list after success.")
        .defaultValue(UpdateBooksMode.Remove)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum working range.")
        .defaultValue(5.0)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .decimalPlaces(2)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Faces targets before interacting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings hand on successful interactions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> repairMode = sgGeneral.add(new IntSetting.Builder()
        .name("repair-mode")
        .description("Stops breaking when held tool has this many uses left. 0 = off.")
        .defaultValue(1)
        .range(0, 100)
        .sliderRange(0, 100)
        .build()
    );

    private final Set<UUID> experiencedVillagers = new HashSet<>();

    private VillagerEntity villager;
    private BlockPos jobSite;
    private boolean placingJobSite;
    private boolean breakingJobSite;
    private int actionDelay;

    public AutoLibrarianModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-librarian", "Cycles librarian lecterns until a wanted enchanted-book trade appears.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (actionDelay > 0) actionDelay--;

        if (villager == null || villager.isRemoved() || villager.isDead()) {
            setTargetVillager();
            return;
        }

        if (jobSite == null) {
            setTargetJobSite();
            return;
        }

        if (placingJobSite && breakingJobSite) {
            error("Internal state conflict: place + break both enabled.");
            toggle();
            return;
        }

        if (placingJobSite) {
            placeJobSite();
            return;
        }

        if (breakingJobSite) {
            breakJobSite();
            return;
        }

        if (!(mc.currentScreen instanceof MerchantScreen merchantScreen)) {
            openTradeScreen();
            return;
        }

        MerchantScreenHandler screenHandler = merchantScreen.getScreenHandler();
        int experience = screenHandler.getExperience();
        if (experience > 0) {
            warning("Villager at %s is already experienced and cannot be retrained.", villager.getBlockPos().toShortString());
            experiencedVillagers.add(villager.getUuid());
            villager = null;
            jobSite = null;
            closeTradeScreen();
            return;
        }

        BookOffer offer = findEnchantedBookOffer(screenHandler.getRecipes());
        if (offer == null) {
            info("Villager is not selling an enchanted book; breaking lectern.");
            closeTradeScreen();
            breakingJobSite = true;
            return;
        }

        info("Villager is selling %s for %s.", offer.nameWithLevel(mc.world.getRegistryManager()), offer.formattedPrice());

        List<BookOffer> wanted = WantedBooks.parse(wantedBooks.get());
        int wantedIndex = WantedBooks.indexOf(wanted, offer);
        if (wantedIndex < 0 || offer.price() > wanted.get(wantedIndex).price()) {
            closeTradeScreen();
            breakingJobSite = true;
            return;
        }

        if (lockInTrade.get()) {
            lockInCurrentTrade(screenHandler);
            closeTradeScreen();
        }

        wantedBooks.set(WantedBooks.applyUpdateMode(wanted, wantedIndex, offer, updateBooks.get()));
        info("Done!");
        toggle();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Color greenSide = new Color(0, 255, 0, 40);
        Color greenLine = new Color(0, 255, 0, 180);

        Color redSide = new Color(255, 0, 0, 35);
        Color redLine = new Color(255, 0, 0, 170);

        if (villager != null && villager.isAlive()) {
            Box box = lerpedBox(villager, event.tickDelta);
            event.renderer.box(box, greenSide, greenLine, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        }

        if (jobSite != null) {
            event.renderer.box(new Box(jobSite), greenSide, greenLine, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        }

        for (UUID villagerId : experiencedVillagers) {
            if (mc.world.getEntity(villagerId) instanceof VillagerEntity experienced && experienced.isAlive()) {
                Box box = lerpedBox(experienced, event.tickDelta);
                event.renderer.box(box, redSide, redLine, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
            }
        }
    }

    private void setTargetVillager() {
        double rangeSq = range.get() * range.get();
        VillagerEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof VillagerEntity candidate)) continue;
            if (candidate.isRemoved() || !candidate.isAlive()) continue;
            if (mc.player.squaredDistanceTo(candidate) > rangeSq) continue;
            if (!candidate.getVillagerData().profession().matchesKey(VillagerProfession.LIBRARIAN)) continue;
            if (candidate.getVillagerData().level() != 1) continue;
            if (experiencedVillagers.contains(candidate.getUuid())) continue;

            double distance = mc.player.squaredDistanceTo(candidate);
            if (distance >= bestDistance) continue;

            best = candidate;
            bestDistance = distance;
        }

        villager = best;

        if (villager == null) {
            error("Couldn't find a nearby librarian.");
            toggle();
            return;
        }

        info("Found villager at %s.", villager.getBlockPos().toShortString());
    }

    private void setTargetJobSite() {
        if (villager == null) return;

        Vec3d eyes = mc.player.getEyePos();
        double rangeSq = range.get() * range.get();
        int blockRange = (int) Math.ceil(range.get());

        jobSite = BlockPos.streamOutwards(BlockPos.ofFloored(eyes), blockRange, blockRange, blockRange)
            .filter(pos -> pos.toCenterPos().squaredDistanceTo(eyes) <= rangeSq)
            .filter(pos -> mc.world.getBlockState(pos).isOf(Blocks.LECTERN))
            .min(Comparator.comparingDouble(pos -> villager.squaredDistanceTo(pos.toCenterPos())))
            .map(BlockPos::toImmutable)
            .orElse(null);

        if (jobSite == null) {
            error("Couldn't find the librarian's lectern.");
            toggle();
            return;
        }

        info("Found lectern at %s.", jobSite.toShortString());
    }

    private void breakJobSite() {
        if (jobSite == null) return;

        BlockState state = mc.world.getBlockState(jobSite);
        if (state.isAir() || state.isReplaceable()) {
            breakingJobSite = false;
            placingJobSite = true;
            return;
        }

        if (shouldPauseForRepair()) {
            error("Repair mode triggered. Tool too close to breaking.");
            toggle();
            return;
        }

        if (rotate.get()) RotationPackets.face(jobSite.toCenterPos());

        if (meteordevelopment.meteorclient.utils.world.BlockUtils.breakBlock(jobSite, swing.get())) {
            actionDelay = 1;
        }
    }

    private void placeJobSite() {
        if (jobSite == null) return;

        BlockState state = mc.world.getBlockState(jobSite);
        if (!state.isReplaceable()) {
            if (state.isOf(Blocks.LECTERN)) {
                placingJobSite = false;
            } else {
                placingJobSite = false;
                breakingJobSite = true;
            }
            return;
        }

        FindItemResult lectern = InvUtils.find(Items.LECTERN);
        if (!lectern.found()) {
            error("No lectern in inventory.");
            toggle();
            return;
        }

        if (!lectern.isMainHand() && !lectern.isOffhand()) {
            if (lectern.isHotbar()) {
                InvUtils.swap(lectern.slot(), false);
            } else {
                InvUtils.move().from(lectern.slot()).toHotbar(mc.player.getInventory().getSelectedSlot());
            }
            return;
        }

        Hand hand = lectern.getHand() != null ? lectern.getHand() : Hand.MAIN_HAND;

        BlockHitSelection selection = getPlacementHitResult(jobSite);
        if (selection == null) return;

        mc.options.sneakKey.setPressed(true);

        if (rotate.get()) RotationPackets.face(selection.hitPos());

        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, selection.toHitResult());
        if (result.isAccepted()) {
            if (swing.get()) mc.player.swingHand(hand);
            actionDelay = 4;
        }

        KeyBindingUtils.resetPressedState(mc.options.sneakKey);
    }

    private void openTradeScreen() {
        if (actionDelay > 0) return;
        if (villager == null || !villager.isAlive()) return;

        if (mc.player.squaredDistanceTo(villager) > range.get() * range.get()) {
            error("Villager is out of range.");
            toggle();
            return;
        }

        Vec3d boxCenter = villager.getBoundingBox().getCenter();
        if (rotate.get()) RotationPackets.face(boxCenter);

        Vec3d hitPos = villager.getBoundingBox().raycast(mc.player.getEyePos(), boxCenter).orElse(boxCenter);
        net.minecraft.util.hit.EntityHitResult hitResult = new net.minecraft.util.hit.EntityHitResult(villager, hitPos);

        ActionResult result = mc.interactionManager.interactEntityAtLocation(mc.player, villager, hitResult, Hand.MAIN_HAND);
        if (!result.isAccepted()) result = mc.interactionManager.interactEntity(mc.player, villager, Hand.MAIN_HAND);

        if (result.isAccepted() && swing.get()) mc.player.swingHand(Hand.MAIN_HAND);

        actionDelay = 4;
    }

    private void closeTradeScreen() {
        if (mc.player != null) mc.player.closeHandledScreen();
        actionDelay = 4;
    }

    private void lockInCurrentTrade(MerchantScreenHandler screenHandler) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        screenHandler.setRecipeIndex(0);
        mc.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(0));

        mc.interactionManager.clickSlot(
            screenHandler.syncId,
            2,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }

    private BookOffer findEnchantedBookOffer(TradeOfferList offers) {
        for (TradeOffer offer : offers) {
            ItemStack stack = offer.getSellItem();
            if (!stack.isOf(Items.ENCHANTED_BOOK)) continue;

            ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
            Set<Object2IntMap.Entry<RegistryEntry<Enchantment>>> entries = enchantments.getEnchantmentEntries();
            if (entries.isEmpty()) continue;

            Object2IntMap.Entry<RegistryEntry<Enchantment>> first = entries.iterator().next();
            String id = first.getKey().getKey().map(key -> key.getValue().toString()).orElse(null);
            if (id == null) continue;

            BookOffer bookOffer = new BookOffer(id, first.getIntValue(), offer.getDisplayedFirstBuyItem().getCount());
            if (!bookOffer.isFullyValid(mc.world.getRegistryManager())) continue;

            return bookOffer;
        }

        return null;
    }

    private BlockHitSelection getPlacementHitResult(BlockPos targetPos) {
        Vec3d eyes = mc.player.getEyePos();
        BlockHitSelection best = null;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = targetPos.offset(direction);
            BlockState neighborState = mc.world.getBlockState(neighbor);
            if (neighborState.getOutlineShape(mc.world, neighbor).isEmpty() || neighborState.isReplaceable()) continue;

            Direction side = direction.getOpposite();
            Vec3d hitPos = neighbor.toCenterPos().add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            boolean lineOfSight = hasLineOfSight(eyes, hitPos);
            double distanceSq = eyes.squaredDistanceTo(hitPos);

            BlockHitSelection candidate = new BlockHitSelection(neighbor, side, hitPos, lineOfSight, distanceSq);
            if (best == null || candidate.isBetterThan(best)) best = candidate;
        }

        return best;
    }

    private boolean hasLineOfSight(Vec3d from, Vec3d to) {
        return mc.world.raycast(new net.minecraft.world.RaycastContext(
            from,
            to,
            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
            net.minecraft.world.RaycastContext.FluidHandling.NONE,
            mc.player
        )).getType() == net.minecraft.util.hit.HitResult.Type.MISS;
    }

    private boolean shouldPauseForRepair() {
        int threshold = repairMode.get();
        if (threshold <= 0) return false;

        ItemStack stack = mc.player.getMainHandStack();
        if (!stack.isDamageable()) return false;

        int remaining = stack.getMaxDamage() - stack.getDamage();
        return remaining <= threshold;
    }

    private static Box lerpedBox(VillagerEntity villager, float tickDelta) {
        double x = MathHelper.lerp(tickDelta, villager.lastRenderX, villager.getX()) - villager.getX();
        double y = MathHelper.lerp(tickDelta, villager.lastRenderY, villager.getY()) - villager.getY();
        double z = MathHelper.lerp(tickDelta, villager.lastRenderZ, villager.getZ()) - villager.getZ();
        return villager.getBoundingBox().offset(x, y, z);
    }

    private void resetState() {
        villager = null;
        jobSite = null;
        placingJobSite = false;
        breakingJobSite = false;
        actionDelay = 0;
    }

    public enum UpdateBooksMode {
        Off,
        Remove,
        Price
    }

}
