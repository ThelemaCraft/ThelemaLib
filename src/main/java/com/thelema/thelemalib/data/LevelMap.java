package com.thelema.thelemalib.data;

import com.thelema.thelemalib.data.tool.MapConverter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

@SuppressWarnings({"unchecked", "SuspiciousMethodCalls", "NullableProblems"})
public class LevelMap<K, V> extends SavedData implements Map<K, V> {

    private final Map<K, V> delegate = new HashMap<>();
    public final ServerLevel level;
    public final String file;

    private LevelMap(ServerLevel level, String file) {
        this.level = level;
        this.file = file;
    }

    public static <K, V> LevelMap<K, V> global(ServerLevel level, String file) {
        return common(level.getServer().overworld(), file);
    }

    public static <K, V> LevelMap<K, V> common(ServerLevel level, String file) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(
                        () -> new LevelMap<>(level, file),
                        (nbt, provider) -> {
                            LevelMap<K, V> lm = new LevelMap<>(level, file);
                            lm.delegate.putAll((Map<? extends K, ? extends V>) MapConverter.fromNbt(nbt, provider));
                            return lm;
                        }
                ),
                file
        );
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public V get(Object key) {
        V v = delegate.get(key);
        if (v != null) setDirty();
        return v;
    }

    public V get(Object key, boolean setDirty) {
        V v = delegate.get(key);
        if (v != null && setDirty) setDirty();
        return v;
    }

    @Override
    public V put(K key, V value) {
        V old = delegate.put(key, value);
        setDirty();
        return old;
    }

    @Override
    public V remove(Object key) {
        V old = delegate.remove(key);
        if (old != null) setDirty();
        return old;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        if (m.isEmpty()) return;
        delegate.putAll(m);
        setDirty();
    }

    @Override
    public void clear() {
        if (delegate.isEmpty()) return;
        delegate.clear();
        setDirty();
    }

    @Override
    public Set<K> keySet() {
        return new DelegatingSet<>(delegate.keySet(), this);
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new DelegatingEntrySet<>(delegate.entrySet(), this);
    }

    @Override
    public Collection<V> values() {
        return new DelegatingCollection<>(delegate.values(), this);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        return MapConverter.toNBT((Map<Object, Object>) delegate, provider);
    }

    private static class DelegatingSet<E> extends AbstractSet<E> {
        private final Set<E> delegate;
        private final LevelMap<?, ?> parent;

        DelegatingSet(Set<E> delegate, LevelMap<?, ?> parent) {
            this.delegate = delegate;
            this.parent = parent;
        }

        @Override
        public Iterator<E> iterator() {
            Iterator<E> it = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }
                @Override
                public E next() { return it.next(); }
                @Override
                public void remove() {
                    it.remove();
                    parent.setDirty();
                }
            };
        }

        @Override
        public int size() { return delegate.size(); }

        @Override
        public boolean remove(Object o) {
            if (delegate.remove(o)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (delegate.removeAll(c)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            if (delegate.retainAll(c)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            if (!delegate.isEmpty()) {
                delegate.clear();
                parent.setDirty();
            }
        }
    }

    private static class DelegatingEntrySet<K, V> extends AbstractSet<Entry<K, V>> {
        private final Set<Entry<K, V>> delegate;
        private final LevelMap<K, V> parent;

        DelegatingEntrySet(Set<Entry<K, V>> delegate, LevelMap<K, V> parent) {
            this.delegate = delegate;
            this.parent = parent;
        }

        @Override
        public Iterator<Entry<K, V>> iterator() {
            Iterator<Entry<K, V>> it = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    Entry<K, V> original = it.next();
                    // 返回包装的 Entry，setValue 时触发 setDirty
                    return new Entry<>() {
                        @Override
                        public K getKey() {
                            return original.getKey();
                        }

                        @Override
                        public V getValue() {
                            return original.getValue();
                        }

                        @Override
                        public V setValue(V value) {
                            V old = original.setValue(value);
                            parent.setDirty();
                            return old;
                        }
                    };
                }

                @Override
                public void remove() {
                    it.remove();
                    parent.setDirty();
                }
            };
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean remove(Object o) {
            if (delegate.remove(o)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (delegate.removeAll(c)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            if (delegate.retainAll(c)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            if (!delegate.isEmpty()) {
                delegate.clear();
                parent.setDirty();
            }
        }
    }

    private static class DelegatingCollection<E> extends AbstractCollection<E> {
        private final Collection<E> delegate;
        private final LevelMap<?, ?> parent;

        DelegatingCollection(Collection<E> delegate, LevelMap<?, ?> parent) {
            this.delegate = delegate;
            this.parent = parent;
        }

        @Override
        public Iterator<E> iterator() {
            Iterator<E> it = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() { return it.hasNext(); }
                @Override
                public E next() { return it.next(); }
                @Override
                public void remove() {
                    it.remove();
                    parent.setDirty();
                }
            };
        }

        @Override
        public int size() { return delegate.size(); }

        @Override
        public boolean remove(Object o) {
            if (delegate.remove(o)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (delegate.removeAll(c)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            if (delegate.retainAll(c)) {
                parent.setDirty();
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            if (!delegate.isEmpty()) {
                delegate.clear();
                parent.setDirty();
            }
        }
    }
}