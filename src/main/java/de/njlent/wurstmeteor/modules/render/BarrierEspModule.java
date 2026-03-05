package de.njlent.wurstmeteor.modules.render;

import de.njlent.wurstmeteor.WurstMeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Module;

public class BarrierEspModule extends Module {
    public BarrierEspModule() {
        super(WurstMeteorAddon.CATEGORY, "barrier-esp", "Forces barrier particles to render even when not holding barrier blocks.");
    }
}
