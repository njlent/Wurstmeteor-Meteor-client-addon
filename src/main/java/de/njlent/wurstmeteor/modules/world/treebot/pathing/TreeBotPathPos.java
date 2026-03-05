package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import net.minecraft.util.math.BlockPos;

public class TreeBotPathPos extends BlockPos {
    private final boolean jumping;

    public TreeBotPathPos(BlockPos pos) {
        this(pos, false);
    }

    public TreeBotPathPos(BlockPos pos, boolean jumping) {
        super(pos.getX(), pos.getY(), pos.getZ());
        this.jumping = jumping;
    }

    public boolean isJumping() {
        return jumping;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TreeBotPathPos node)) return false;

        return getX() == node.getX()
            && getY() == node.getY()
            && getZ() == node.getZ()
            && isJumping() == node.isJumping();
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 2 + (isJumping() ? 1 : 0);
    }
}
