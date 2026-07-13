package autismclient.util;

import autismclient.AutismClientAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class PersistentNbtManager<T> {
    protected final List<T> items = new ArrayList<>();
    private boolean loaded;

    protected abstract File saveFile();

    protected abstract String listKey();

    protected abstract T fromTag(CompoundTag tag);

    protected abstract CompoundTag toTag(T item);

    protected void readExtra(CompoundTag root) {
    }

    protected void writeExtra(CompoundTag root) {
    }

    protected abstract String describe();

    protected final synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = saveFile();
        if (!file.exists()) return;
        try {
            CompoundTag tag = NbtIo.read(file.toPath());
            if (tag == null) return;
            readExtra(tag);
            items.clear();
            ListTag list = tag.getListOrEmpty(listKey());
            for (Tag element : list) {
                if (element instanceof CompoundTag compoundTag) items.add(fromTag(compoundTag));
            }
        } catch (Exception e) {
            AutismClientAddon.LOG.error("Failed to load " + describe(), e);
        }
    }

    public void save() {
        CompoundTag tag;
        synchronized (this) {
            tag = new CompoundTag();
            writeExtra(tag);
            ListTag list = new ListTag();
            for (T item : items) list.add(toTag(item));
            tag.put(listKey(), list);
        }
        try {
            NbtIo.write(tag, saveFile().toPath());
        } catch (Exception e) {
            AutismClientAddon.LOG.error("Failed to save " + describe(), e);
        }
    }

    public synchronized List<T> all() {
        return new ArrayList<>(items);
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized boolean contains(T item) {
        return items.contains(item);
    }
}
