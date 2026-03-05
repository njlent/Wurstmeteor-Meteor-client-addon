package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import de.njlent.wurstmeteor.util.KeyBindingUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;

public abstract class TreeBotPathProcessor {
    protected final MinecraftClient mc;

    protected final ArrayList<TreeBotPathPos> path;
    protected int index;
    protected boolean done;
    protected int ticksOffPath;

    protected TreeBotPathProcessor(MinecraftClient mc, ArrayList<TreeBotPathPos> path) {
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
        Vec3d eyes = mc.player.getEyePos();
        Vec3d target = Vec3d.ofCenter(pos);

        double xDiff = target.x - eyes.x;
        double zDiff = target.z - eyes.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90.0F);

        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setBodyYaw(yaw);
    }

    protected float getHorizontalAngleTo(Vec3d lookVec) {
        Vec3d eyes = mc.player.getEyePos();

        double xDiff = lookVec.x - eyes.x;
        double zDiff = lookVec.z - eyes.z;
        float yaw = (float) (Math.toDegrees(Math.atan2(zDiff, xDiff)) - 90.0F);

        return yaw - mc.player.getYaw();
    }

    protected int indexOfPos(BlockPos pos) {
        for (int i = 0; i < path.size(); i++) {
            if (pos.equals(path.get(i))) return i;
        }

        return -1;
    }

    public static void lockControls(MinecraftClient mc) {
        getControls(mc).forEach(key -> key.setPressed(false));
        mc.player.setSprinting(false);
    }

    public static void releaseControls(MinecraftClient mc) {
        getControls(mc).forEach(KeyBindingUtils::resetPressedState);
    }

    private static ArrayList<KeyBinding> getControls(MinecraftClient mc) {
        ArrayList<KeyBinding> controls = new ArrayList<>(6);
        controls.add(mc.options.forwardKey);
        controls.add(mc.options.backKey);
        controls.add(mc.options.rightKey);
        controls.add(mc.options.leftKey);
        controls.add(mc.options.jumpKey);
        controls.add(mc.options.sneakKey);
        return controls;
    }
}
