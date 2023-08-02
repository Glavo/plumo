package org.glavo.plumo.internal.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;

@SuppressWarnings("unchecked")
public final class MultiStringMap extends AbstractMap<String, List<String>> {
    public final HashMap<String, Object> map;

    public MultiStringMap() {
        this.map = new HashMap<>();
    }

    public MultiStringMap(HashMap<String, Object> map) {
        this.map = map;
    }

    @Override
    public @NotNull Set<Entry<String, List<String>>> entrySet() {
        return new EntrySet();
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public List<String> get(Object key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        } else if (v instanceof String) {
            return Collections.singletonList((String) v);
        } else {
            return (List<String>) v;
        }
    }

    public String getFirst(String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        } else if (v instanceof String) {
            return ((String) v);
        } else {
            return ((List<String>) v).get(0);
        }
    }

    public void add(String key, String value) {
        map.compute(key, (ignored, oldValue) -> {
            if (oldValue == null) {
                return value;
            }

            List<String> list;
            if (oldValue instanceof String) {
                list = new ArrayList<>(4);
                list.add((String) oldValue);
            } else {
                list = (List<String>) oldValue;
            }
            list.add(value);
            return list;
        });
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    private final class EntrySet extends AbstractSet<Entry<String, List<String>>> {

        @Override
        public Iterator<Entry<String, List<String>>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return MultiStringMap.this.size();
        }
    }

    private final class EntryIterator implements Iterator<Entry<String, List<String>>> {

        private final Iterator<Entry<String, Object>> it = map.entrySet().iterator();

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public Entry<String, List<String>> next() {
            Entry<String, Object> entry = it.next();
            if (entry.getValue() instanceof String) {
                return new SimpleImmutableEntry<>(entry.getKey(), Collections.singletonList((String) entry.getValue()));
            } else {
                return new SimpleImmutableEntry<>(entry.getKey(), (List<String>) entry.getValue());
            }
        }
    }
}
