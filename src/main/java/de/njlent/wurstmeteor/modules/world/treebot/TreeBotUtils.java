package de.njlent.wurstmeteor.modules.world.treebot;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;

public final class TreeBotUtils {
    private TreeBotUtils() {
    }

    public static boolean isLog(BlockState state) {
        return state.isIn(BlockTags.LOGS);
    }

    public static boolean isLeaves(BlockState state) {
        return state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.WART_BLOCKS);
    }
}
