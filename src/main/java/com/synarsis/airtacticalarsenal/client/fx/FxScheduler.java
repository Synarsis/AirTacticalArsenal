package com.synarsis.airtacticalarsenal.client.fx;

import java.util.ArrayList;
import java.util.List;

public final class FxScheduler {

    @FunctionalInterface
    public interface Task {
        boolean tick();
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> ADDED = new ArrayList<>();
    private static long now;

    private FxScheduler() {
    }

    public static long now() {
        return now;
    }

    public static void run(Task task) {
        ADDED.add(task);
    }

    public static void later(int ticks, Runnable action) {
        int[] left = {Math.max(0, ticks)};
        run(() -> {
            if (left[0]-- > 0) {
                return true;
            }
            action.run();
            return false;
        });
    }

    public static void during(int ticks, java.util.function.IntConsumer action) {
        int[] at = {0};
        run(() -> {
            action.accept(at[0]);
            return ++at[0] < ticks;
        });
    }

    public static void tick() {
        now++;
        TASKS.addAll(ADDED);
        ADDED.clear();
        for (int i = TASKS.size() - 1; i >= 0; i--) {
            boolean more;
            try {
                more = TASKS.get(i).tick();
            } catch (RuntimeException e) {
                more = false;
            }
            if (!more) {
                TASKS.remove(i);
            }
        }
    }

    public static int pending() {
        return TASKS.size() + ADDED.size();
    }

    public static void clear() {
        TASKS.clear();
        ADDED.clear();
    }
}
