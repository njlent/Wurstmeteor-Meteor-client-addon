package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import de.njlent.wurstmeteor.util.KeyBindingUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

public abstract class TreeBotPathProcessor {
    protected final Minecraft mc;

    protected final ArrayList<TreeBotPathPos> path;
    protected int index;
    protected boolean done;
    protected int ticksOffPath;

    protected TreeBotPathProcessor(Minecraft mc, ArrayList<TreeBotPathPos> path) {
        if (path.isEmpty()) throw new IllegalStateException("There is no path!");

        this.mc = mc;
        this.path = path;
    }

    public abstract void process();

    public abstract boolean canBreakBlocks();

    public int getIndex() {
        return index;
    }

    public boolean isDone() {
        return done;
    }

    public int getTicksOffPath() {
        return ticksOffPath;
    }

    protected void facePosition(BlockPos pos) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(pos);

        double xDiff = target.x - eyes.x;
        double zDiff = target.z - eyes.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90.0F);

        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setYBodyRot(yaw);
    }

    protected float getHorizontalAngleTo(Vec3 lookVec) {
        Vec3 eyes = mc.player.getEyePosition();

        double xDiff = lookVec.x - eyes.x;
        double zDiff = lookVec.z - eyes.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90.0F);

        return yaw - mc.player.getYRot();
    }

    protected int indexOfPos(BlockPos pos) {
        for (int i = 0; i < path.size(); i++) {
            if (pos.equals(path.get(i))) return i;
        }

        return -1;
    }

    public static void lockControls(Minecraft mc) {
        getControls(mc).forEach(key -> key.setDown(false));
        mc.player.setSprinting(false);
    }

    public static void releaseControls(Minecraft mc) {
        getControls(mc).forEach(KeyBindingUtils::resetPressedState);
    }

    private static ArrayList<KeyMapping> getControls(Minecraft mc) {
        ArrayList<KeyMapping> controls = new ArrayList<>(6);
        controls.add(mc.options.keyUp);
        controls.add(mc.options.keyDown);
        controls.add(mc.options.keyRight);
        controls.add(mc.options.keyLeft);
        controls.add(mc.options.keyJump);
        controls.add(mc.options.keyShift);
        return controls;
    }
}
