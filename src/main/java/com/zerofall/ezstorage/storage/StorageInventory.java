package com.zerofall.ezstorage.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zerofall.ezstorage.config.EZConfig;

import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The content of one storage system: a list of distinct item types with a (long) amount each, the capacity
 * granted by the attached storage boxes, and the persistent 3x3 crafting grid of the crafting box.
 * <p>
 * Server instances live in {@link StorageManager}; clients only ever see copies received over the network.
 */
public final class StorageInventory {

    public record Entry(ItemResource resource, long count) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                ItemResource.CODEC.fieldOf("item")
                    .forGetter(Entry::resource),
                Codec.LONG.fieldOf("count")
                    .forGetter(Entry::count))
                .apply(i, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec
            .composite(ItemResource.STREAM_CODEC, Entry::resource, ByteBufCodecs.VAR_LONG, Entry::count, Entry::new);

        /** A display stack; its count is clamped to the item's stack size and must not be used as the amount. */
        public ItemStack toDisplayStack() {
            return resource.toStack(1);
        }
    }

    private record GridSlot(int slot, ItemStack stack) {

        static final Codec<GridSlot> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                Codec.INT.fieldOf("slot")
                    .forGetter(GridSlot::slot),
                ItemStack.CODEC.fieldOf("stack")
                    .forGetter(GridSlot::stack))
                .apply(i, GridSlot::new));
    }

    public static final Codec<StorageInventory> CODEC = RecordCodecBuilder.create(
        i -> i.group(
            UUIDUtil.CODEC.fieldOf("id")
                .forGetter(StorageInventory::getId),
            Entry.CODEC.listOf()
                .optionalFieldOf("items", List.of())
                .forGetter(StorageInventory::entries),
            Codec.LONG.optionalFieldOf("capacity", 0L)
                .forGetter(StorageInventory::getCapacity),
            GridSlot.CODEC.listOf()
                .optionalFieldOf("grid", List.of())
                .forGetter(StorageInventory::gridSlots))
            .apply(i, StorageInventory::fromCodec));

    /** What clients receive: the entries and the capacity (the grid is synced through menu slots). */
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageInventory> CLIENT_STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC,
        StorageInventory::getId,
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
        StorageInventory::entries,
        ByteBufCodecs.VAR_LONG,
        StorageInventory::getCapacity,
        (id, entries, capacity) -> fromCodec(id, entries, capacity, List.of()));

    private final UUID id;
    private List<Entry> entries = new ArrayList<>();
    private Map<ItemResource, Integer> indexCache;
    private long capacity;
    private long totalCountCache = -1;
    private final NonNullList<ItemStack> craftGrid = NonNullList.withSize(9, ItemStack.EMPTY);
    private Runnable changeListener = () -> {};
    private final List<Runnable> gridListeners = new ArrayList<>();

    public StorageInventory(UUID id) {
        this.id = id;
    }

    private static StorageInventory fromCodec(UUID id, List<Entry> entries, long capacity, List<GridSlot> grid) {
        StorageInventory inventory = new StorageInventory(id);
        for (Entry entry : entries) {
            if (!entry.resource()
                .isEmpty() && entry.count() > 0) {
                inventory.entries.add(entry);
            }
        }
        inventory.capacity = capacity;
        for (GridSlot slot : grid) {
            if (slot.slot() >= 0 && slot.slot() < 9) {
                inventory.craftGrid.set(slot.slot(), slot.stack());
            }
        }
        return inventory;
    }

    // ---------------------------------------------------------------------------------------------
    // Basic accessors
    // ---------------------------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    public Entry get(int index) {
        return entries.get(index);
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        if (this.capacity != capacity) {
            this.capacity = capacity;
            changed();
        }
    }

    public long getTotalCount() {
        if (totalCountCache < 0) {
            long total = 0;
            for (Entry entry : entries) {
                total += entry.count();
            }
            totalCountCache = total;
        }
        return totalCountCache;
    }

    public long getFreeSpace() {
        return Math.max(0, capacity - getTotalCount());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int indexOf(ItemResource resource) {
        if (indexCache == null) {
            indexCache = new HashMap<>(entries.size() * 2);
            for (int i = 0; i < entries.size(); i++) {
                indexCache.put(entries.get(i)
                    .resource(), i);
            }
        }
        Integer index = indexCache.get(resource);
        return index == null ? -1 : index;
    }

    public long getAmount(ItemResource resource) {
        int index = indexOf(resource);
        return index < 0 ? 0 : entries.get(index)
            .count();
    }

    /** Can a new item type be added without exceeding the configured type limit? */
    public boolean canAddNewType() {
        int maxTypes = EZConfig.maxItemTypes();
        return maxTypes <= 0 || entries.size() < maxTypes;
    }

    // ---------------------------------------------------------------------------------------------
    // Mutation
    // ---------------------------------------------------------------------------------------------

    /** @return the amount that was (or would be) inserted */
    public long insert(ItemResource resource, long amount, boolean simulate) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        long accepted = Math.min(amount, getFreeSpace());
        if (accepted <= 0) {
            return 0;
        }
        int index = indexOf(resource);
        if (index < 0 && !canAddNewType()) {
            return 0;
        }
        if (!simulate) {
            if (index < 0) {
                entries.add(new Entry(resource, accepted));
                if (indexCache != null) {
                    indexCache.put(resource, entries.size() - 1);
                }
            } else {
                Entry entry = entries.get(index);
                entries.set(index, new Entry(resource, entry.count() + accepted));
            }
            changed();
        }
        return accepted;
    }

    /** @return the amount that was (or would be) extracted */
    public long extract(ItemResource resource, long amount, boolean simulate) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        int index = indexOf(resource);
        if (index < 0) {
            return 0;
        }
        Entry entry = entries.get(index);
        long taken = Math.min(amount, entry.count());
        if (!simulate) {
            if (taken == entry.count()) {
                entries.remove(index);
                indexCache = null;
            } else {
                entries.set(index, new Entry(resource, entry.count() - taken));
            }
            changed();
        }
        return taken;
    }

    /**
     * Inserts as much of the stack as possible.
     *
     * @return the part of the stack that did not fit (a new stack, the argument is not modified)
     */
    public ItemStack insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long inserted = insert(ItemResource.of(stack), stack.getCount(), false);
        return stack.copyWithCount(stack.getCount() - (int) inserted);
    }

    /** Extracts up to {@code amount} items as a single stack (never more than the item's stack size). */
    public ItemStack extractStack(ItemResource resource, int amount) {
        int limited = Math.min(amount, resource.getMaxStackSize());
        long taken = extract(resource, limited, false);
        return taken <= 0 ? ItemStack.EMPTY : resource.toStack((int) taken);
    }

    /** Finds a stored item type matching the predicate (tested against a single-item stack). */
    public ItemResource findMatching(Predicate<ItemStack> predicate) {
        for (Entry entry : entries) {
            if (predicate.test(entry.toDisplayStack())) {
                return entry.resource();
            }
        }
        return ItemResource.EMPTY;
    }

    public void sortByCount() {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Entry::count)
            .reversed());
        if (!sorted.equals(entries)) {
            entries = sorted;
            indexCache = null;
            changed();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Transactions (used by the proxy port's resource handler)
    // ---------------------------------------------------------------------------------------------

    /** Cheap snapshot: entries are immutable records, so a shallow copy is enough. */
    public List<Entry> createSnapshot() {
        return new ArrayList<>(entries);
    }

    public void restoreSnapshot(List<Entry> snapshot) {
        entries = new ArrayList<>(snapshot);
        indexCache = null;
        totalCountCache = -1;
    }

    /** Called once a transaction touching this inventory was committed. */
    public void onExternalCommit() {
        changed();
    }

    // ---------------------------------------------------------------------------------------------
    // Crafting grid
    // ---------------------------------------------------------------------------------------------

    /** The shared backing list of the crafting box grid; every open crafting menu works on this list. */
    public NonNullList<ItemStack> getCraftGrid() {
        return craftGrid;
    }

    public void onGridChanged() {
        for (Runnable listener : List.copyOf(gridListeners)) {
            listener.run();
        }
        changeListener.run();
    }

    private List<GridSlot> gridSlots() {
        List<GridSlot> slots = new ArrayList<>();
        for (int i = 0; i < craftGrid.size(); i++) {
            if (!craftGrid.get(i)
                .isEmpty()) {
                slots.add(new GridSlot(i, craftGrid.get(i)));
            }
        }
        return slots;
    }

    // ---------------------------------------------------------------------------------------------
    // Change tracking
    // ---------------------------------------------------------------------------------------------

    void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    public void addGridListener(Runnable listener) {
        gridListeners.add(listener);
    }

    public void removeGridListener(Runnable listener) {
        gridListeners.remove(listener);
    }

    private void changed() {
        totalCountCache = -1;
        changeListener.run();
    }

    /** A detached copy of entries and capacity, safe to hand to the network layer or another thread. */
    public StorageInventory copyForClient() {
        StorageInventory copy = new StorageInventory(id);
        copy.entries = new ArrayList<>(entries);
        copy.capacity = capacity;
        return copy;
    }

    /** Copies entries and capacity from a freshly received client copy. */
    public void replaceContents(StorageInventory other) {
        this.entries = new ArrayList<>(other.entries);
        this.capacity = other.capacity;
        this.indexCache = null;
        this.totalCountCache = -1;
    }
}
