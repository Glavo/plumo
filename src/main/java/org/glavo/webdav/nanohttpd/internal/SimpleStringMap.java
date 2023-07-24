package org.glavo.webdav.nanohttpd.internal;

import java.util.*;

@SuppressWarnings("unchecked")
public final class SimpleStringMap<V> extends AbstractMap<String, V> {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private String[] keys = EMPTY_STRING_ARRAY;
    private Object[] values = EMPTY_OBJECT_ARRAY;
    private int size;

    private int indexOf(Object key) {
        if (!(key instanceof String)) {
            return -1;
        }

        for (int i = 0; i < size; i++) {
            if (key.equals(keys[i])) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    @Override
    public V get(Object key) {
        int idx = indexOf(key);
        if (idx < 0) {
            return null;
        }

        return (V) values[idx];
    }

    private void append(String key, V value) {
        final int oldSize = this.size;

        if (keys.length == oldSize) {
            int newCapacity = oldSize == 0 ? 8 : oldSize << 1;
            if (newCapacity < oldSize) {
                throw new OutOfMemoryError("Size: " + oldSize);
            }

            String[] newNames = Arrays.copyOf(keys, newCapacity);
            Object[] newValues = Arrays.copyOf(values, newCapacity);

            this.keys = newNames;
            this.values = newValues;
        }

        keys[oldSize] = key;
        values[oldSize] = value;
        size = oldSize + 1;
    }

    @Override
    public V put(String key, V value) {
        int idx = indexOf(key);
        if (idx >= 0) {
            V res = (V) values[idx];
            values[idx] = value;
            return res;
        } else {
            append(key, value);
            return null;
        }
    }

    @Override
    public V remove(Object key) {
        int idx = indexOf(key);
        if (idx < 0) {
            return null;
        }

        V res = (V) values[idx];

        int lastIndex = size - 1;
        if (idx < lastIndex) {
            int num = lastIndex - idx;

            System.arraycopy(keys, idx + 1, keys, idx, num);
            System.arraycopy(values, idx + 1, values, idx, num);
        } else {
            keys[lastIndex] = null;
            values[lastIndex] = null;
        }
        size = lastIndex;

        return res;
    }

    @Override
    public void clear() {
        if (size > 0) {
            Arrays.fill(keys, 0, size, null);
            Arrays.fill(values, 0, size, null);
            this.size = 0;
        }
    }

    @Override
    public Set<Entry<String, V>> entrySet() {
        return new EntrySet();
    }

    public <E extends Throwable> void forEachChecked(CheckedBiConsumer<String, V, E> consumer) throws E {
        for (int i = 0; i < size; i++) {
            consumer.accept(keys[i], (V) values[i]);
        }
    }

    private final class EntryIterator implements Iterator<Entry<String, V>> {
        private int idx = 0;

        @Override
        public boolean hasNext() {
            return idx < size;
        }

        @Override
        public Entry<String, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Entry<String, V> entry = new SimpleImmutableEntry<>(keys[idx], (V) values[idx]);
            idx++;
            return entry;
        }
    }

    private final class EntrySet extends AbstractSet<Entry<String, V>> {

        @Override
        public Iterator<Entry<String, V>> iterator() {
            return SimpleStringMap.this.new EntryIterator();
        }

        @Override
        public int size() {
            return SimpleStringMap.this.size();
        }
    }
}
