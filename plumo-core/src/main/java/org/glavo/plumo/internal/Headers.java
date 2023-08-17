/*
 * Copyright 2023 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.plumo.internal;

import org.glavo.plumo.internal.util.UnsyncBufferedOutputStream;
import org.glavo.plumo.internal.util.Utils;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;

@SuppressWarnings("unchecked")
public final class Headers extends AbstractMap<String, List<String>> {

    // list of prime numbers
    private static final int[] CAPACITIES = {127, 269, 541, 1091, 2287, 4583, 9199, 19121, 39133};

    String[] keys;
    Object[] values;

    int size = 0;
    int threshold = 0;

    private void growIfNeeded() {
        if (size >= threshold) {
            grow();
        }
    }

    private void grow() {
        int currentSize = this.size;

        int nextCap = -1;
        int nextThreshold = -1;

        if (currentSize < 10) {
            nextCap = 13;
            nextThreshold = 10;
        } else if (currentSize < 24) {
            nextCap = 29;
            nextThreshold = 24;
        } else if (currentSize < 54) {
            nextCap = 67;
            nextThreshold = 54;
        } else {
            for (int capacity : CAPACITIES) {
                int threshold = (int) (capacity * 0.75);

                if (currentSize < threshold) {
                    nextCap = capacity;
                    nextThreshold = threshold;
                }
            }

            if (nextCap < 0) {
                throw new OutOfMemoryError("Map is too large");
            }
        }

        String[] newKeys = new String[nextCap];
        Object[] newValues = new Object[nextCap];

        if (currentSize > 0) {

            String[] oldKeys = this.keys;
            Object[] oldValues = this.values;

            for (int oldIdx = 0; oldIdx < oldKeys.length; oldIdx++) {
                String key = oldKeys[oldIdx];
                if (key != null) {
                    int newIdx = probe(newKeys, key);
                    if (newIdx < 0) {
                        newIdx = -(newIdx + 1);
                        newKeys[newIdx] = key;
                    }
                    newValues[newIdx] = oldValues[oldIdx];
                }
            }
        }

        this.keys = newKeys;
        this.values = newValues;
        this.threshold = nextThreshold;
    }

    private static int probe(String[] keys, String canonicalName) {
        final int cap = keys.length;
        int idx = Math.floorMod(canonicalName.hashCode(), cap);
        while (true) {
            String key = keys[idx];
            if (key == null) {
                return -idx - 1;
            } else if (canonicalName.equals(key)) {
                return idx;
            } else if (++idx == cap) {
                idx = 0;
            }
        }
    }

    static List<String> mapValue(Object value) {
        if (value instanceof String) {
            return Collections.singletonList((String) value);
        } else if (value != null) {
            return Collections.unmodifiableList((ArrayList<String>) value);
        } else {
            return null;
        }
    }

    public void putHeader(String canonicalName, Object value) {
        // assert value == null || value instanceof String || value instanceof ArrayList<?>;

        growIfNeeded();

        int idx = probe(this.keys, canonicalName);
        if (idx < 0) {
            idx = -(idx + 1);
            size++;
            keys[idx] = canonicalName;
        }
        values[idx] = value;
    }

    public void addHeader(String canonicalName, String value) {
        // assert value == null || value instanceof String || value instanceof ArrayList<?>;

        growIfNeeded();

        int idx = probe(this.keys, canonicalName);
        if (idx < 0) {
            idx = -(idx + 1);
            size++;
            keys[idx] = canonicalName;
            values[idx] = value;
        } else {
            Object old = values[idx];

            if (old == null) {
                values[idx] = value;
            } else if (old instanceof String) {
                ArrayList<String> list = new ArrayList<>(4);
                list.add((String) old);
                list.add(value);
                values[idx] = list;
            } else {
                ArrayList<String> list = (ArrayList<String>) values[idx];
                list.add(value);
            }
        }
    }

    public String getHeader(String canonicalName) {
        if (size == 0) {
            return null;
        }

        int idx = probe(this.keys, canonicalName);
        if (idx < 0) {
            return null;
        }

        Object value = values[idx];

        if (value == null || value instanceof String) {
            return (String) value;
        } else {
            return ((ArrayList<String>) value).get(0);
        }
    }

    public boolean containsHeader(String canonicalName) {
        return size > 0 && probe(this.keys, canonicalName) >= 0;
    }

    public void forEachHeader(BiConsumer<String, String> consumer) {
        for (int i = 0; i < this.keys.length; i++) {
            String key = this.keys[i];
            if (key != null) {
                Object value = values[i];
                if (value instanceof String) {
                    consumer.accept(key, (String) value);
                } else if (value != null) {
                    ArrayList<String> list = (ArrayList<String>) value;
                    for (String v : list) {
                        consumer.accept(key, v);
                    }
                }
            }
        }
    }

    public void writeTo(UnsyncBufferedOutputStream out) throws IOException {
        final String[] keys = this.keys;
        final Object[] values = this.values;

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            if (key != null) {
                Object value = values[i];

                if (value instanceof String) {
                    out.writeHttpHeader(key, (String) value);
                } else if (value != null) {
                    ArrayList<String> list = (ArrayList<String>) value;
                    for (String v : list) {
                        out.writeHttpHeader(key, v);
                    }
                }
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public List<String> get(Object key) {
        if (size == 0 || !(key instanceof String)) {
            return null;
        }

        String canonicalName;
        try {
            canonicalName = Utils.normalizeHttpHeaderFieldName((String) key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        int idx = probe(this.keys, canonicalName);
        if (idx < 0) {
            return null;
        }

        return mapValue(values[idx]);
    }

    @Override
    public boolean containsKey(Object key) {
        try {
            return containsHeader(Utils.normalizeHttpHeaderFieldName((String) key));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException ignored) {
            return false;
        }
    }

    @Override
    public boolean containsValue(Object value) {
        if (value != null && !(value instanceof List<?>)) {
            return false;
        }

        List<?> list = (List<?>) value;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != null) {
                Object v = values[i];
                if (v == null) {
                    return list == null;
                } else if (v instanceof String) {
                    return list != null && list.size() == 1 && v.equals(list.get(0));
                } else {
                    return v.equals(value);
                }
            }
        }

        return super.containsValue(value);
    }

    @Override
    public Headers clone() {
        Headers newHeaders = new Headers();
        if (size > 0) {
            Object[] values = this.values;
            Object[] newValues = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value instanceof String) {
                    newValues[i] = value;
                } else if (value != null) {
                    newValues[i] = ((ArrayList<?>) value).clone();
                }
            }

            newHeaders.keys = this.keys.clone();
            newHeaders.values = newValues;
            newHeaders.size = this.size;
            newHeaders.threshold = this.threshold;
        }
        return newHeaders;
    }

    @Override
    public Set<Entry<String, List<String>>> entrySet() {
        return new EntrySet();
    }

    private final class EntrySet extends AbstractSet<Entry<String, List<String>>> {
        @Override
        public int size() {
            return Headers.this.size();
        }

        @Override
        public Iterator<Entry<String, List<String>>> iterator() {
            return new EntryIterator(keys, values);
        }
    }

    private static final class EntryIterator implements Iterator<Entry<String, List<String>>> {
        private final String[] keys;
        private final Object[] values;
        private final int cap;

        private int idx = -1;

        EntryIterator(String[] keys, Object[] values) {
            this.keys = keys;
            this.values = values;
            this.cap = keys.length;

            scanNext();
        }

        private void scanNext() {
            for (int i = idx + 1; i < cap; i++) {
                if (keys[i] != null) {
                    this.idx = i;
                    return;
                }
            }

            idx = cap;
        }

        @Override
        public boolean hasNext() {
            return idx < cap;
        }

        @Override
        public Entry<String, List<String>> next() {
            if (idx < cap) {
                Entry<String, List<String>> res = new SimpleImmutableEntry<>(keys[idx], mapValue(values[idx]));
                scanNext();
                return res;
            } else {
                throw new NoSuchElementException();
            }
        }
    }
}
