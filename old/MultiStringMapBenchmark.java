package org.glavo.plumo.benchmark;

import org.glavo.plumo.internal.util.MultiStringMap;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Map.entry;

@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@SuppressWarnings("unchecked")
public class MultiStringMapBenchmark {
    private static <T> T[] arrayOf(T... values) {
        return values;
    }

    private static <T> T[] concat(T[]... arrays) {
        return Arrays.stream(arrays)
                .flatMap(Arrays::stream)
                .toArray(n -> (T[]) Array.newInstance(arrays[0].getClass().getComponentType(), n));
    }

    private static String[] keys(Map.Entry<String, ?>[] pairs) {
        String[] res = new String[pairs.length];
        for (int i = 0; i < pairs.length; i++) {
            res[i] = pairs[i].getKey();
        }
        return res;
    }

    private static void addHeaderToHashMap(HashMap<String, Object> map, String key, String value) {
        map.compute(key, (k, v) -> {
            if (v == null) {
                return value;
            } else {
                ArrayList<String> list;
                if (v instanceof String) {
                    list = new ArrayList<>(4);
                    list.add((String) v);
                } else {
                    list = (ArrayList<String>) v;
                }
                list.add(value);
                return list;
            }
        });
    }

    private static final Map.Entry<String, String>[] TEST_DATA1 = arrayOf(
            entry("content-type", "text/html"),
            entry("content-length", "182"),
            entry("content-language", "en-US"),
            entry("collection", "keep-alive"),
            entry("cache-control", " private, must-revalidate, no-cache, no-store, max-age=0"),
            entry("date", "Wed, 16 Aug 2023 21:15:01 GMT"),
            entry("pragma", "no-cache"),
            entry("server", "CLOUD ELB 1.0.0")
    );

    private static final Map.Entry<String, String>[] TEST_DATA2 = concat(TEST_DATA1, arrayOf(
            entry("last-modified", "Mon, 23 Jan 2017 13:27:29 GMT"),
            entry("accept-ranges", "bytes"),
            entry("x-content-type-options", "nosniff"),
            entry("x-frame-options", "deny"),
            entry("x-powered-by", "Express"),
            entry("x-response-time", "118"),
            entry("x-xss-protection", "0")
    ));

    private static final Map.Entry<String, String>[] TEST_DATA3 = concat(TEST_DATA1, arrayOf(
            entry("set-cookie", "key1=value1"),
            entry("set-cookie", "key2=value2"),
            entry("set-cookie", "key3=value3"),
            entry("set-cookie", "key4=value4"),
            entry("set-cookie", "key5=value5"),
            entry("set-cookie", "key6=value6"),
            entry("set-cookie", "key7=value7")
    ));

    private static final HashMap<String, String> TEST_MAP1 = new HashMap<>();
    private static final HashMap<String, String> TEST_MAP2 = new HashMap<>();
    private static final HashMap<String, Object> TEST_MAP3 = new HashMap<>();
    private static final MultiStringMap TEST_MULTI_STRING_MAP1 = new MultiStringMap();
    private static final MultiStringMap TEST_MULTI_STRING_MAP2 = new MultiStringMap();
    private static final MultiStringMap TEST_MULTI_STRING_MAP3 = new MultiStringMap();
    private static final String[] TEST_KEYS1 = keys(TEST_DATA1);
    private static final String[] TEST_KEYS2 = keys(TEST_DATA2);
    private static final String[] TEST_KEYS3 = keys(TEST_DATA3);

    static {
        for (Map.Entry<String, String> data : TEST_DATA1) {
            TEST_MAP1.put(data.getKey(), data.getValue());
            TEST_MULTI_STRING_MAP1.putDirect(data.getKey(), data.getValue());
        }

        for (Map.Entry<String, String> data : TEST_DATA2) {
            TEST_MAP2.put(data.getKey(), data.getValue());
            TEST_MULTI_STRING_MAP2.putDirect(data.getKey(), data.getValue());
        }

        for (Map.Entry<String, String> data : TEST_DATA3) {
            addHeaderToHashMap(TEST_MAP3, data.getKey(), data.getValue());
            TEST_MULTI_STRING_MAP3.addDirect(data.getKey(), data.getValue());
        }
    }

    //
    // ------ forEach ------
    //

    private static void forEachHashMap(Blackhole blackhole, HashMap<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            blackhole.consume(entry.getKey());
            blackhole.consume(entry.getValue());
        }
    }

    private static void forEachMultiStringMap(Blackhole blackhole, MultiStringMap map) {
        map.forEachHeader((k, v) -> {
            blackhole.consume(k);
            blackhole.consume(v);
        });
    }

    @Benchmark
    public void testForEachHashMap1(Blackhole blackhole) {
        forEachHashMap(blackhole, TEST_MAP1);
    }

    @Benchmark
    public void testForEachHashMap2(Blackhole blackhole) {
        forEachHashMap(blackhole, TEST_MAP2);
    }

    @Benchmark
    public void testForEachHashMap3(Blackhole blackhole) {
        for (Map.Entry<String, Object> entry : TEST_MAP3.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof String) {
                blackhole.consume(key);
                blackhole.consume((String) value);
            } else if (value != null) {
                for (String v : ((ArrayList<String>) value)) {
                    blackhole.consume(key);
                    blackhole.consume(v);
                }
            }
        }
    }

    @Benchmark
    public void testForEachMultiStringMap1(Blackhole blackhole) {
        forEachMultiStringMap(blackhole, TEST_MULTI_STRING_MAP1);
    }

    @Benchmark
    public void testForEachMultiStringMap2(Blackhole blackhole) {
        forEachMultiStringMap(blackhole, TEST_MULTI_STRING_MAP2);
    }

    @Benchmark
    public void testForEachMultiStringMap3(Blackhole blackhole) {
        forEachMultiStringMap(blackhole, TEST_MULTI_STRING_MAP3);
    }

    //
    // ------ getHeader ------
    //

    private static void getHeaderFromHashMap(Blackhole blackhole, HashMap<String, String> map, String[] keys) {
        for (String key : keys) {
            blackhole.consume(map.get(key));
        }
    }

    private static void getHeaderFromMultiStringMap(Blackhole blackhole, MultiStringMap map, String[] keys) {
        for (String key : keys) {
            blackhole.consume(map.getFirst(key));
        }
    }

    @Benchmark
    public void testGetHeaderFromHashMap1(Blackhole blackhole) {
        getHeaderFromHashMap(blackhole, TEST_MAP1, TEST_KEYS1);
    }

    @Benchmark
    public void testGetHeaderFromHashMap2(Blackhole blackhole) {
        getHeaderFromHashMap(blackhole, TEST_MAP2, TEST_KEYS2);
    }

    @Benchmark
    public void testGetHeaderFromMultiStringMap1(Blackhole blackhole) {
        getHeaderFromMultiStringMap(blackhole, TEST_MULTI_STRING_MAP1, TEST_KEYS1);
    }

    @Benchmark
    public void testGetHeaderFromMultiStringMap2(Blackhole blackhole) {
        getHeaderFromMultiStringMap(blackhole, TEST_MULTI_STRING_MAP2, TEST_KEYS2);
    }

    //
    // ------ getHeaders ------
    //

    private static void getHeadersFromHashMap(Blackhole blackhole, HashMap<String, ?> map, String[] keys) {
        for (String key : keys) {
            blackhole.consume(MultiStringMap.mapValue(map.get(key)));
        }
    }

    private static void getHeadersFromMultiStringMap(Blackhole blackhole, MultiStringMap map, String[] keys) {
        for (String key : keys) {
            blackhole.consume(map.get(key));
        }
    }

    @Benchmark
    public void testGetHeadersFromHashMap1(Blackhole blackhole) {
        getHeadersFromHashMap(blackhole, TEST_MAP1, TEST_KEYS1);
    }

    @Benchmark
    public void testGetHeadersFromHashMap2(Blackhole blackhole) {
        getHeadersFromHashMap(blackhole, TEST_MAP2, TEST_KEYS2);
    }

    @Benchmark
    public void testGetHeadersFromHashMap3(Blackhole blackhole) {
        getHeadersFromHashMap(blackhole, TEST_MAP3, TEST_KEYS3);
    }

    @Benchmark
    public void testGetHeadersFromMultiStringMap1(Blackhole blackhole) {
        getHeadersFromMultiStringMap(blackhole, TEST_MULTI_STRING_MAP1, TEST_KEYS1);
    }

    @Benchmark
    public void testGetHeadersFromMultiStringMap2(Blackhole blackhole) {
        getHeadersFromMultiStringMap(blackhole, TEST_MULTI_STRING_MAP2, TEST_KEYS2);
    }

    @Benchmark
    public void testGetHeadersFromMultiStringMap3(Blackhole blackhole) {
        getHeadersFromMultiStringMap(blackhole, TEST_MULTI_STRING_MAP3, TEST_KEYS3);
    }

    //
    // ------ putHeader ------
    //

    private static HashMap<String, String> putToHashMap(Map.Entry<String, String>[] datas) {
        HashMap<String, String> res = new HashMap<>();
        for (Map.Entry<String, String> data : datas) {
            res.put(data.getKey(), data.getValue());
        }
        return res;
    }

    private static MultiStringMap putToMultiStringMap(Map.Entry<String, String>[] datas) {
        MultiStringMap res = new MultiStringMap();
        for (Map.Entry<String, String> data : datas) {
            res.putDirect(data.getKey(), data.getValue());
        }
        return res;
    }

    @Benchmark
    public Object testPutToHashMap1() {
        return putToHashMap(TEST_DATA1);
    }

    @Benchmark
    public Object testPutToHashMap2() {
        return putToHashMap(TEST_DATA2);
    }

    @Benchmark
    public Object testPutToMultiStringMap1() {
        return putToMultiStringMap(TEST_DATA1);
    }

    @Benchmark
    public Object testPutToMultiStringMap2() {
        return putToMultiStringMap(TEST_DATA2);
    }

    //
    // ------ addHeader ------
    //

    private static HashMap<String, Object> addToHashMap(Map.Entry<String, String>[] datas) {
        HashMap<String, Object> res = new HashMap<>();
        for (Map.Entry<String, String> data : datas) {
            addHeaderToHashMap(res, data.getKey(), data.getValue());
        }
        return res;
    }

    private static MultiStringMap addToMultiStringMap(Map.Entry<String, String>[] datas) {
        MultiStringMap res = new MultiStringMap();
        for (Map.Entry<String, String> data : datas) {
            res.addDirect(data.getKey(), data.getValue());
        }
        return res;
    }

    @Benchmark
    public Object testAddToHashMap1() {
        return addToHashMap(TEST_DATA1);
    }

    @Benchmark
    public Object testAddToHashMap2() {
        return addToHashMap(TEST_DATA2);
    }

    @Benchmark
    public Object testAddToHashMap3() {
        return addToHashMap(TEST_DATA3);
    }

    @Benchmark
    public Object testAddToMultiStringMap1() {
        return addToMultiStringMap(TEST_DATA1);
    }

    @Benchmark
    public Object testAddToMultiStringMap2() {
        return addToMultiStringMap(TEST_DATA2);
    }

    @Benchmark
    public Object testAddToMultiStringMap3() {
        return addToMultiStringMap(TEST_DATA3);
    }

    //
    // ------ clone ------
    //

    private static HashMap<String, Object> deepClone(HashMap<String, Object> map) {
        HashMap<String, Object> res = (HashMap<String, Object>) map.clone();
        res.replaceAll((k, v) -> {
            if ((v == null) || v instanceof String) {
                return v;
            } else {
                return ((ArrayList<String>) v).clone();
            }
        });
        return res;
    }

    @Benchmark
    public Object testCloneHashMap1() {
        return TEST_MAP1.clone();
    }

    @Benchmark
    public Object testCloneHashMap2() {
        return TEST_MAP2.clone();
    }

    @Benchmark
    public Object testCloneHashMap3() {
        return deepClone(TEST_MAP3);
    }

    @Benchmark
    public Object testCloneMultiStringMap1() {
        return TEST_MULTI_STRING_MAP1.clone();
    }

    @Benchmark
    public Object testCloneMultiStringMap2() {
        return TEST_MULTI_STRING_MAP2.clone();
    }

    @Benchmark
    public Object testCloneMultiStringMap3() {
        return TEST_MULTI_STRING_MAP3.clone();
    }
}



