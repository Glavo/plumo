package org.glavo.webdav.nanohttpd.internal;

import java.util.Arrays;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public final class SimpleStringMap<V> {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];

    private String[] names = EMPTY_STRING_ARRAY;
    private Object[] values = EMPTY_OBJECT_ARRAY;
    private int size;

    private int indexOf(String name) {
        for (int i = 0; i < size; i++) {
            if (name.equals(names[i])) {
                return i;
            }
        }

        return -1;
    }

    public V get(String name) {
        int idx = indexOf(name);
        if (idx < 0) {
            return null;
        }

        return (V) values[idx];
    }

    private void append(String name, V value) {
        final int oldSize = this.size;

        if (names.length == oldSize) {
            int newCapacity = oldSize << 1;
            if (newCapacity < oldSize) {
                throw new OutOfMemoryError("Size: " + oldSize);
            }

            String[] newNames = Arrays.copyOf(names, newCapacity);
            Object[] newValues = Arrays.copyOf(values, newCapacity);

            this.names = newNames;
            this.values = newValues;
        }

        names[oldSize] = name;
        values[oldSize] = value;
        size = oldSize + 1;
    }

    public void put(String name, V value) {
        int idx = indexOf(name);
        if (idx >= 0) {
            values[idx] = value;
        } else {
            append(name, value);
        }
    }

    public void remove(String name) {
        int idx = indexOf(name);
        if (idx < 0) {
            return;
        }

        int lastIndex = size - 1;
        if (idx < lastIndex) {
            int num = lastIndex - idx;

            System.arraycopy(names, idx + 1, names, idx, num);
            System.arraycopy(values, idx + 1, values, idx, num);
        } else {
            names[lastIndex] = null;
            values[lastIndex] = null;
        }
        size = lastIndex;
    }

    public V getOrPut(String name, Supplier<V> supplier) {
        V res = get(name);
        if (res == null) {
            res = supplier.get();
            append(name, res);
        }
        return res;
    }

    public <E extends Throwable> void forEach(CheckedBiConsumer<String, V, E> consumer) throws E {
        for (int i = 0; i < size; i++) {
            consumer.accept(names[i], (V) values[i]);
        }
    }
}
