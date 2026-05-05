package de.njlent.wurstmeteor;

import com.mojang.logging.LogUtils;
import de.njlent.wurstmeteor.modules.combat.ArrowDmgModule;
import de.njlent.wurstmeteor.modules.combat.CriticalsModule;
import de.njlent.wurstmeteor.modules.combat.InfiniteReachModule;
import de.njlent.wurstmeteor.modules.combat.MaceDmgModule;
import de.njlent.wurstmeteor.modules.combat.MultiAuraModule;
import de.njlent.wurstmeteor.modules.misc.AntiSpamModule;
import de.njlent.wurstmeteor.modules.misc.AntiDropModule;
import de.njlent.wurstmeteor.modules.misc.AutoTraderModule;
import de.njlent.wurstmeteor.modules.misc.BeaconExploitModule;
import de.njlent.wurstmeteor.modules.misc.CommandScannerModule;
import de.njlent.wurstmeteor.modules.misc.DamageDetectModule;
import de.njlent.wurstmeteor.modules.movement.AntiVoidModule;
import de.njlent.wurstmeteor.modules.movement.CreativeFlightModule;
import de.njlent.wurstmeteor.modules.movement.ExtraElytraModule;
import de.njlent.wurstmeteor.modules.movement.InvWalkModule;
import de.njlent.wurstmeteor.modules.movement.SpeedHackModule;
import de.njlent.wurstmeteor.modules.player.PotionSaverModule;
import de.njlent.wurstmeteor.modules.render.BarrierEspModule;
import de.njlent.wurstmeteor.modules.render.BedrockStashModule;
import de.njlent.wurstmeteor.modules.render.HealthTagsModule;
import de.njlent.wurstmeteor.modules.render.ItemEspModule;
import de.njlent.wurstmeteor.modules.render.TrajectoriesModule;
import de.njlent.wurstmeteor.modules.world.AutoFarmModule;
import de.njlent.wurstmeteor.modules.world.AutoLibrarianModule;
import de.njlent.wurstmeteor.modules.world.AutoMineModule;
import de.njlent.wurstmeteor.modules.world.autolibrarian.BookOfferListSetting;
import de.njlent.wurstmeteor.modules.world.BonemealAuraModule;
import de.njlent.wurstmeteor.modules.world.FeedAuraModule;
import de.njlent.wurstmeteor.modules.world.NewChunksModule;
import de.njlent.wurstmeteor.modules.world.TargetPlaceModule;
import de.njlent.wurstmeteor.modules.world.TreeBotModule;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

public class WurstMeteorAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Wurst", () -> Items.NETHERITE_HOE.getDefaultInstance());

    @Override
    public void onInitialize() {
        LOG.info("Initializing Wurst for Meteor addon");
        SettingsWidgetFactory.registerCustomFactory(BookOfferListSetting.class, (theme) -> (table, setting) -> {
            WView offerView = table.add(theme.view()).expandX().widget();
            offerView.maxHeight = theme.scale(220);
            offerView.scrollOnlyWhenMouseOver = false;

            WTable offerTable = offerView.add(theme.table()).expandX().widget();
            BookOfferListSetting.fillTable(theme, offerTable, (BookOfferListSetting) setting);
        });

        Modules modules = Modules.get();
        modules.add(new AutoFarmModule());
        modules.add(new AutoLibrarianModule());
        modules.add(new AutoMineModule());
        modules.add(new AutoTraderModule());
        modules.add(new BeaconExploitModule());
        modules.add(new FeedAuraModule());
        modules.add(new MaceDmgModule());
        modules.add(new ArrowDmgModule());
        modules.add(new InfiniteReachModule());
        modules.add(new AntiSpamModule());
        modules.add(new AntiDropModule());
        modules.add(new CommandScannerModule());
        modules.add(new DamageDetectModule());
        modules.add(new PotionSaverModule());
        modules.add(new AntiVoidModule());
        modules.add(new CreativeFlightModule());
        modules.add(new ExtraElytraModule());
        modules.add(new InvWalkModule());
        modules.add(new SpeedHackModule());
        modules.add(new NewChunksModule());
        modules.add(new TargetPlaceModule());
        modules.add(new TreeBotModule());
        modules.add(new MultiAuraModule());
        modules.add(new BonemealAuraModule());
        modules.add(new CriticalsModule());
        modules.add(new BarrierEspModule());
        modules.add(new BedrockStashModule());
        modules.add(new HealthTagsModule());
        modules.add(new TrajectoriesModule());
        modules.add(new ItemEspModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "de.njlent.wurstmeteor";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("njlent", "wurst-for-meteor-client-addon");
    }
}
