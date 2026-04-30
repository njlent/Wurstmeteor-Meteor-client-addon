package de.njlent.wurstmeteor.modules.world;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import de.njlent.wurstmeteor.modules.world.autolibrarian.BlockHitSelection;
import de.njlent.wurstmeteor.modules.world.autolibrarian.BookOffer;
import de.njlent.wurstmeteor.modules.world.autolibrarian.BookOfferListSetting;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.*;

public class AutoLibrarianModule extends Module {
    private static final List<BookOffer> DEFAULT_WANTED_BOOKS = List.of(
        new BookOffer("minecraft:feather_falling", 4, 64),
        new BookOffer("minecraft:fortune", 3, 64),
        new BookOffer("minecraft:looting", 3, 64),
        new BookOffer("minecraft:mending", 1, 64),
        new BookOffer("minecraft:silk_touch", 1, 64),
        new BookOffer("minecraft:unbreaking", 3, 64)
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<BookOffer>> wantedBooks = sgGeneral.add(new BookOfferListSetting.Builder()
        .name("wanted-books")
        .description("Wanted librarian enchanted books with level and max price.")
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

    private final Setting<Boolean> backgroundMode = sgGeneral.add(new BoolSetting.Builder()
        .name("background-mode")
        .description("Keeps rerolling while the client is unfocused by disabling pause-on-lost-focus while active.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> professionTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("profession-timeout")
        .description("Ticks to wait after placing a lectern before rerolling if the villager still is not a librarian. 0 = off.")
        .defaultValue(100)
        .range(0, 200)
        .sliderRange(0, 200)
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

    private Villager villager;
    private BlockPos jobSite;
    private boolean placingJobSite;
    private boolean breakingJobSite;
    private int actionDelay;
    private int noLecternWarnCooldown;
    private int nonLibrarianTicks;
    private boolean restorePauseOnLostFocus;
    private boolean previousPauseOnLostFocus;

    public AutoLibrarianModule() {
        super(WurstMeteorAddon.CATEGORY, "auto-librarian", "Cycles librarian lecterns until a wanted enchanted-book trade appears.");
    }

    @Override
    public void onActivate() {
        syncBackgroundMode();
        resetState();
    }

    @Override
    public void onDeactivate() {
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        restoreBackgroundMode();
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        syncBackgroundMode();

        if (actionDelay > 0) actionDelay--;
        if (noLecternWarnCooldown > 0) noLecternWarnCooldown--;

        if (villager == null || villager.isRemoved() || !villager.isAlive()) {
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

        if (handleProfessionTimeout()) return;

        if (!(mc.screen instanceof MerchantScreen merchantScreen)) {
            openTradeScreen();
            return;
        }

        MerchantMenu screenHandler = merchantScreen.getMenu();
        int experience = screenHandler.getTraderXp();
        if (experience > 0) {
            warning("Villager at %s is already experienced and cannot be retrained.", villager.blockPosition().toShortString());
            experiencedVillagers.add(villager.getUUID());
            villager = null;
            jobSite = null;
            closeTradeScreen();
            return;
        }

        BookOffer offer = findEnchantedBookOffer(screenHandler.getOffers());
        if (offer == null) {
            startLecternReroll("Villager is not selling an enchanted book; breaking lectern.");
            return;
        }

        info("Villager is selling %s for %s.", offer.nameWithLevel(mc.level.registryAccess()), offer.formattedPrice());

        List<BookOffer> wanted = WantedBooks.sanitize(wantedBooks.get());
        int wantedIndex = WantedBooks.indexOf(wanted, offer);
        if (wantedIndex < 0 || offer.price() > wanted.get(wantedIndex).price()) {
            startLecternReroll("Offer does not match wanted books; rerolling lectern.");
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
        if (mc.player == null || mc.level == null) return;

        Color greenSide = new Color(0, 255, 0, 40);
        Color greenLine = new Color(0, 255, 0, 180);

        Color redSide = new Color(255, 0, 0, 35);
        Color redLine = new Color(255, 0, 0, 170);

        if (villager != null && villager.isAlive()) {
            AABB box = lerpedBox(villager, event.tickDelta);
            event.renderer.box(box, greenSide, greenLine, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        }

        if (jobSite != null) {
            event.renderer.box(new AABB(jobSite), greenSide, greenLine, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        }

        for (UUID villagerId : experiencedVillagers) {
            if (mc.level.getEntity(villagerId) instanceof Villager experienced && experienced.isAlive()) {
                AABB box = lerpedBox(experienced, event.tickDelta);
                event.renderer.box(box, redSide, redLine, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
            }
        }
    }

    private void setTargetVillager() {
        double rangeSq = range.get() * range.get();
        Villager best = null;
        double bestDistance = Double.MAX_VALUE;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Villager candidate)) continue;
            if (candidate.isRemoved() || !candidate.isAlive()) continue;
            if (mc.player.distanceToSqr(candidate) > rangeSq) continue;
            if (!candidate.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) continue;
            if (candidate.getVillagerData().level() != 1) continue;
            if (experiencedVillagers.contains(candidate.getUUID())) continue;

            double distance = mc.player.distanceToSqr(candidate);
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

        info("Found villager at %s.", villager.blockPosition().toShortString());
    }

    private void setTargetJobSite() {
        if (villager == null) return;
        jobSite = resolveJobSite();

        if (jobSite == null) {
            error("Couldn't find a lectern right next to the librarian.");
            toggle();
            return;
        }

        info("Found lectern at %s.", jobSite.toShortString());
    }

    private void breakJobSite() {
        if (jobSite == null) return;

        if (mc.screen instanceof MerchantScreen) {
            closeTradeScreen(false);
            if (mc.screen instanceof MerchantScreen) return;
        }

        double rangeSq = range.get() * range.get();
        if (mc.player.distanceToSqr(jobSite.getCenter()) > rangeSq) {
            BlockPos corrected = resolveJobSite();
            if (corrected != null) {
                jobSite = corrected;
                return;
            }

            // Force a fresh adjacent-lectern lookup instead of breaking an out-of-range stale target.
            jobSite = null;
            return;
        }

        BlockState state = mc.level.getBlockState(jobSite);
        if (state.isAir() || state.canBeReplaced()) {
            BlockPos corrected = resolveJobSite();
            if (corrected != null && !corrected.equals(jobSite)) {
                jobSite = corrected.immutable();
                return;
            }

            breakingJobSite = false;
            placingJobSite = true;
            return;
        }

        if (shouldPauseForRepair()) {
            error("Repair mode triggered. Tool too close to breaking.");
            toggle();
            return;
        }

        if (rotate.get()) RotationPackets.face(jobSite.getCenter());

        if (meteordevelopment.meteorclient.utils.world.BlockUtils.breakBlock(jobSite, swing.get())) {
            actionDelay = 1;
        }
    }

    private void placeJobSite() {
        if (jobSite == null) return;

        BlockState state = mc.level.getBlockState(jobSite);
        if (!state.canBeReplaced()) {
            if (state.is(Blocks.LECTERN)) {
                placingJobSite = false;
            } else {
                placingJobSite = false;
                breakingJobSite = true;
            }
            return;
        }

        FindItemResult lectern = InvUtils.find(Items.LECTERN);
        if (!lectern.found()) {
            if (noLecternWarnCooldown <= 0) {
                warning("No lectern in inventory. Waiting for lectern item.");
                noLecternWarnCooldown = 40;
            }
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

        InteractionHand hand = lectern.getHand() != null ? lectern.getHand() : InteractionHand.MAIN_HAND;

        BlockHitSelection selection = getPlacementHitResult(jobSite);
        if (selection == null) return;

        mc.options.keyShift.setDown(true);

        if (rotate.get()) RotationPackets.face(selection.hitPos());

        InteractionResult result = mc.gameMode.useItemOn(mc.player, hand, selection.toHitResult());
        if (result.consumesAction()) {
            if (swing.get()) mc.player.swing(hand);
            actionDelay = 4;
        }

        KeyBindingUtils.resetPressedState(mc.options.keyShift);
    }

    private void openTradeScreen() {
        if (actionDelay > 0) return;
        if (villager == null || !villager.isAlive()) return;

        if (mc.player.distanceToSqr(villager) > range.get() * range.get()) {
            error("Villager is out of range.");
            toggle();
            return;
        }

        Vec3 boxCenter = villager.getBoundingBox().getCenter();
        if (rotate.get()) RotationPackets.face(boxCenter);

        Vec3 hitPos = villager.getBoundingBox().clip(mc.player.getEyePosition(), boxCenter).orElse(boxCenter);
        net.minecraft.world.phys.EntityHitResult hitResult = new net.minecraft.world.phys.EntityHitResult(villager, hitPos);

        InteractionResult result = mc.gameMode.interact(mc.player, villager, hitResult, InteractionHand.MAIN_HAND);
        if (result.consumesAction() && swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);

        actionDelay = 4;
    }

    private void closeTradeScreen() {
        closeTradeScreen(true);
    }

    private void closeTradeScreen(boolean applyDelay) {
        if (mc.player != null) mc.player.closeContainer();
        if (applyDelay) actionDelay = 4;
    }

    private boolean handleProfessionTimeout() {
        if (villager == null || placingJobSite || breakingJobSite) {
            resetProfessionTimeout();
            return false;
        }

        if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
            resetProfessionTimeout();
            return false;
        }

        int timeout = professionTimeout.get();
        if (timeout <= 0) {
            resetProfessionTimeout();
            return false;
        }

        nonLibrarianTicks++;
        if (nonLibrarianTicks < timeout) return false;

        startLecternReroll("Villager did not become a librarian in time; rerolling lectern.");
        return true;
    }

    private void startLecternReroll(String reason) {
        info(reason);
        closeTradeScreen(false);
        actionDelay = 0;
        placingJobSite = false;
        breakingJobSite = true;
        resetProfessionTimeout();
    }

    private void resetProfessionTimeout() {
        nonLibrarianTicks = 0;
    }

    private void lockInCurrentTrade(MerchantMenu screenHandler) {
        if (mc.player == null || mc.getConnection() == null) return;

        screenHandler.setSelectionHint(0);
        mc.getConnection().send(new ServerboundSelectTradePacket(0));

        mc.gameMode.handleContainerInput(
            screenHandler.containerId,
            2,
            0,
            ContainerInput.PICKUP,
            mc.player
        );
    }

    private BookOffer findEnchantedBookOffer(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            ItemStack stack = offer.getResult();
            if (!stack.is(Items.ENCHANTED_BOOK)) continue;

            ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
            Set<Object2IntMap.Entry<Holder<Enchantment>>> entries = enchantments.entrySet();
            if (entries.isEmpty()) continue;

            Object2IntMap.Entry<Holder<Enchantment>> first = entries.iterator().next();
            String id = first.getKey().unwrapKey().map(key -> key.identifier().toString()).orElse(null);
            if (id == null) continue;

            BookOffer bookOffer = new BookOffer(id, first.getIntValue(), offer.getCostA().getCount());
            if (!bookOffer.isFullyValid(mc.level.registryAccess())) continue;

            return bookOffer;
        }

        return null;
    }

    private BlockPos resolveJobSite() {
        return findAdjacentLecternNearVillager();
    }

    private BlockPos findAdjacentLecternNearVillager() {
        if (villager == null || mc.level == null || mc.player == null) return null;

        double rangeSq = range.get() * range.get();
        BlockPos villagerPos = villager.blockPosition();

        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        // Hard rule: lectern must touch the villager's block position.
        for (Direction direction : Direction.values()) {
            BlockPos candidate = villagerPos.relative(direction);
            if (!mc.level.getBlockState(candidate).is(Blocks.LECTERN)) continue;
            if (mc.player.distanceToSqr(candidate.getCenter()) > rangeSq) continue;

            double distance = villager.distanceToSqr(candidate.getCenter());
            if (distance >= bestDistance) continue;

            best = candidate.immutable();
            bestDistance = distance;
        }

        // Rare edge case: villager inside lectern block in very tight setups.
        if (best == null && mc.level.getBlockState(villagerPos).is(Blocks.LECTERN)
            && mc.player.distanceToSqr(villagerPos.getCenter()) <= rangeSq) {
            return villagerPos.immutable();
        }

        return best;
    }

    private BlockHitSelection getPlacementHitResult(BlockPos targetPos) {
        Vec3 eyes = mc.player.getEyePosition();
        BlockHitSelection best = null;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = targetPos.relative(direction);
            BlockState neighborState = mc.level.getBlockState(neighbor);
            if (neighborState.getShape(mc.level, neighbor).isEmpty() || neighborState.canBeReplaced()) continue;

            Direction side = direction.getOpposite();
            Vec3 hitPos = neighbor.getCenter().add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
            boolean lineOfSight = hasLineOfSight(eyes, hitPos);
            double distanceSq = eyes.distanceToSqr(hitPos);

            BlockHitSelection candidate = new BlockHitSelection(neighbor, side, hitPos, lineOfSight, distanceSq);
            if (best == null || candidate.isBetterThan(best)) best = candidate;
        }

        return best;
    }

    private boolean hasLineOfSight(Vec3 from, Vec3 to) {
        return mc.level.clip(new net.minecraft.world.level.ClipContext(
            from,
            to,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            mc.player
        )).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private boolean shouldPauseForRepair() {
        int threshold = repairMode.get();
        if (threshold <= 0) return false;

        ItemStack stack = mc.player.getMainHandItem();
        if (!stack.isDamageableItem()) return false;

        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        return remaining <= threshold;
    }

    private static AABB lerpedBox(Villager villager, float tickDelta) {
        double x = Mth.lerp(tickDelta, villager.xOld, villager.getX()) - villager.getX();
        double y = Mth.lerp(tickDelta, villager.yOld, villager.getY()) - villager.getY();
        double z = Mth.lerp(tickDelta, villager.zOld, villager.getZ()) - villager.getZ();
        return villager.getBoundingBox().move(x, y, z);
    }

    private void syncBackgroundMode() {
        if (mc.options == null) return;

        if (!backgroundMode.get()) {
            restoreBackgroundMode();
            return;
        }

        if (!restorePauseOnLostFocus) {
            previousPauseOnLostFocus = mc.options.pauseOnLostFocus;
            restorePauseOnLostFocus = true;
        }

        mc.options.pauseOnLostFocus = false;
    }

    private void restoreBackgroundMode() {
        if (!restorePauseOnLostFocus || mc.options == null) return;

        mc.options.pauseOnLostFocus = previousPauseOnLostFocus;
        restorePauseOnLostFocus = false;
    }

    private void resetState() {
        villager = null;
        jobSite = null;
        placingJobSite = false;
        breakingJobSite = false;
        actionDelay = 0;
        noLecternWarnCooldown = 0;
        nonLibrarianTicks = 0;
    }

    public enum UpdateBooksMode {
        Off,
        Remove,
        Price
    }

}
