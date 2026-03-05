package de.njlent.wurstmeteor;

import com.mojang.logging.LogUtils;
import de.njlent.wurstmeteor.modules.combat.CriticalsModule;
import de.njlent.wurstmeteor.modules.combat.MaceDmgModule;
import de.njlent.wurstmeteor.modules.combat.MultiAuraModule;
import de.njlent.wurstmeteor.modules.render.ItemEspModule;
import de.njlent.wurstmeteor.modules.world.AutoFarmModule;
import de.njlent.wurstmeteor.modules.world.BonemealAuraModule;
import de.njlent.wurstmeteor.modules.world.FeedAuraModule;
import de.njlent.wurstmeteor.modules.world.NewChunksModule;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.Items;
import org.slf4j.Logger;

public class WurstMeteorAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Wurst", Items.NETHERITE_HOE.getDefaultStack());

    @Override
    public void onInitialize() {
        LOG.info("Initializing Wurst for Meteor addon");

        Modules modules = Modules.get();
        modules.add(new AutoFarmModule());
        modules.add(new FeedAuraModule());
        modules.add(new MaceDmgModule());
        modules.add(new NewChunksModule());
        modules.add(new MultiAuraModule());
        modules.add(new BonemealAuraModule());
        modules.add(new CriticalsModule());
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
