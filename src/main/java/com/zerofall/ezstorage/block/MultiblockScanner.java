package com.zerofall.ezstorage.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Walks the blocks connected to a storage system. Replaces the recursive scan of the 1.7.10 version with a bounded
 * breadth-first search that never loads chunks.
 */
public final class MultiblockScanner {

    /** Upper bound of blocks one system may consist of. */
    public static final int MAX_BLOCKS = 16384;

    private MultiblockScanner() {}

    public record Result(Set<BlockPos> members, long capacity, boolean hasCraftingBox, List<BlockPos> proxies) {}

    private static Block blockAt(Level level, BlockPos pos) {
        return level.isLoaded(pos) ? level.getBlockState(pos)
            .getBlock() : null;
    }

    /** Scans everything connected to the core at {@code corePos}, without walking through other cores. */
    public static Result scan(Level level, BlockPos corePos) {
        Set<BlockPos> members = new LinkedHashSet<>();
        List<BlockPos> proxies = new ArrayList<>();
        long capacity = 0;
        boolean hasCraftingBox = false;

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        members.add(corePos);
        queue.add(corePos);
        while (!queue.isEmpty() && members.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (members.contains(next)) {
                    continue;
                }
                Block block = blockAt(level, next);
                if (!(block instanceof StorageMultiblockBlock) || block instanceof StorageCoreBlock) {
                    continue;
                }
                members.add(next);
                queue.add(next);
                if (block instanceof StorageBoxBlock box) {
                    capacity += box.getCapacity();
                } else if (block instanceof CraftingBoxBlock) {
                    hasCraftingBox = true;
                } else if (block instanceof InventoryProxyBlock) {
                    proxies.add(next);
                }
            }
        }
        return new Result(members, capacity, hasCraftingBox, proxies);
    }

    /** Finds every core reachable from the given positions through storage blocks. */
    public static Set<BlockPos> findCores(Level level, Iterable<BlockPos> starts) {
        Set<BlockPos> cores = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos start : starts) {
            if (visited.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty() && visited.size() < MAX_BLOCKS) {
            BlockPos current = queue.poll();
            Block block = blockAt(level, current);
            if (block instanceof StorageCoreBlock) {
                cores.add(current);
                continue;
            }
            if (!(block instanceof StorageMultiblockBlock)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return cores;
    }

    /** Finds the core a storage block at {@code pos} belongs to. */
    public static Optional<BlockPos> findCore(Level level, BlockPos pos) {
        return findCores(level, List.of(pos)).stream()
            .findFirst();
    }

    public static List<BlockPos> neighbors(BlockPos pos) {
        List<BlockPos> list = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            list.add(pos.relative(direction));
        }
        return list;
    }
}
