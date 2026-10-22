package com.synarsis.airtacticalarsenal.client.fx.light;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LightLevelUpdates {
    public static final int RETRY_TICKS = 10;
    public static final int ROUND_PER_FRAME = 4;
    private final Set<Long> dirty = new LinkedHashSet<>();
    private final Map<Long, Long> retries = new LinkedHashMap<>();
    private int round;

    public void changed(int sectionX, int sectionY, int sectionZ, long tick) {
        int[][] offsets = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            long key = OccupancyGrid.sectionKey(sectionX + offset[0], sectionY + offset[1], sectionZ + offset[2]);
            this.dirty.add(key);
            this.retries.put(key, tick + RETRY_TICKS);
        }
    }

    public void loaded(int sectionX, int sectionY, int sectionZ) {
        this.dirty.add(OccupancyGrid.sectionKey(sectionX, sectionY, sectionZ));
    }

    public List<Long> take(long tick, int budget) {
        var pending = this.retries.entrySet().iterator();
        while (pending.hasNext()) {
            var retry = pending.next();
            if (retry.getValue() <= tick) {
                this.dirty.add(retry.getKey());
                pending.remove();
            }
        }
        var result = new java.util.ArrayList<Long>();
        var iterator = this.dirty.iterator();
        while (iterator.hasNext() && result.size() < budget) {
            result.add(iterator.next());
            iterator.remove();
        }
        return result;
    }

    public int nextRound() {
        int result = this.round;
        this.round = (this.round + 1) % OccupancyGrid.SECTION_COUNT;
        return result;
    }

    public void clear() {
        this.dirty.clear();
        this.retries.clear();
        this.round = 0;
    }
}
