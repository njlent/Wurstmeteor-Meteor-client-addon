package de.njlent.wurstmeteor.modules.world.treebot;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

public final class TreeBotUtils {
    private TreeBotUtils() {
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    public static boolean isLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.WART_BLOCKS);
    }
}
