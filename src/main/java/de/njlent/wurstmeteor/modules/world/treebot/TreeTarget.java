package de.njlent.wurstmeteor.modules.world.treebot;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class TreeTarget {
    private final BlockPos stump;
    private final ArrayList<BlockPos> logs;

    public TreeTarget(BlockPos stump, ArrayList<BlockPos> logs) {
        this.stump = stump;
        this.logs = logs;
    }

    public void render(Render3DEvent event) {
        Color stumpSide = new Color(0, 255, 0, 52);
        Color stumpLine = new Color(0, 255, 0, 210);

        Color logSide = new Color(0, 255, 0, 30);
        Color logLine = new Color(0, 255, 0, 170);

        event.renderer.box(stump, stumpSide, stumpLine, ShapeMode.Both, 0);

        List<BlockPos> drawLogs = logs.stream().filter(pos -> !pos.equals(stump)).toList();
        for (BlockPos pos : drawLogs) {
            event.renderer.box(pos, logSide, logLine, ShapeMode.Both, 0);
        }
    }

    public BlockPos stump() {
        return stump;
    }

    public ArrayList<BlockPos> logs() {
        return logs;
    }
}
