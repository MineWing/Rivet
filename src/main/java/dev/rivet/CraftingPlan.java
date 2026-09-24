package dev.rivet;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Bounded maximum-flow matching handles overlapping recipe choices without consuming items. */
final class CraftingPlan {
    private CraftingPlan() { }

    static int[] allocate(boolean[][] matches, int[] amounts) {
        int slotsStart = 1 + matches.length;
        int sink = slotsStart + amounts.length;
        int[][] capacity = new int[sink + 1][sink + 1];
        for (int ingredient = 0; ingredient < matches.length; ingredient++) {
            capacity[0][ingredient + 1] = 1;
            for (int slot = 0; slot < amounts.length; slot++) {
                if (matches[ingredient][slot]) capacity[ingredient + 1][slotsStart + slot] = 1;
            }
        }
        for (int slot = 0; slot < amounts.length; slot++) {
            capacity[slotsStart + slot][sink] = Math.max(0, amounts[slot]);
        }
        for (int flow = 0; flow < matches.length; flow++) {
            int[] parent = new int[sink + 1];
            Arrays.fill(parent, -1);
            parent[0] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(0);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int node = queue.remove();
                for (int next = 1; next <= sink; next++) {
                    if (parent[next] < 0 && capacity[node][next] > 0) {
                        parent[next] = node;
                        queue.add(next);
                    }
                }
            }
            if (parent[sink] < 0) return null;
            for (int node = sink; node != 0; node = parent[node]) {
                capacity[parent[node]][node]--;
                capacity[node][parent[node]]++;
            }
        }
        int[] used = new int[amounts.length];
        for (int slot = 0; slot < amounts.length; slot++) used[slot] = capacity[sink][slotsStart + slot];
        return used;
    }
}
