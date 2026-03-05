package de.njlent.wurstmeteor.modules.world.treebot.pathing;

import java.util.Comparator;
import java.util.PriorityQueue;

public class TreeBotPathQueue {
    private final PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparing(entry -> entry.priority));

    private static class Entry {
        private final TreeBotPathPos pos;
        private final float priority;

        private Entry(TreeBotPathPos pos, float priority) {
            this.pos = pos;
            this.priority = priority;
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void add(TreeBotPathPos pos, float priority) {
        queue.add(new Entry(pos, priority));
    }

    public int size() {
        return queue.size();
    }

    public TreeBotPathPos poll() {
        return queue.poll().pos;
    }

    public TreeBotPathPos[] toArray() {
        TreeBotPathPos[] array = new TreeBotPathPos[size()];
        int i = 0;
        for (Entry entry : queue) {
            if (i >= size()) break;
            array[i] = entry.pos;
            i++;
        }

        return array;
    }
}
