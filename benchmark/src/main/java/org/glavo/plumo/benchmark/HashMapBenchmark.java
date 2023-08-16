package org.glavo.plumo.benchmark;

import org.glavo.plumo.internal.Headers;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Map.entry;

@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@SuppressWarnings("unchecked")
public class HashMapBenchmark {
    private static <T> T[] arrayOf(T... values) {
        return values;
    }

    private static <T> T[] concat(T[]... arrays) {
        return Arrays.stream(arrays)
                .flatMap(Arrays::stream)
                .toArray(n -> (T[]) Array.newInstance(arrays[0].getClass().getComponentType(), n));
    }

    private static final Map.Entry<String, String>[] TEST_DATA1 = arrayOf(
            entry("server", "CLOUD ELB 1.0.0"),
            entry("content-type", "text/html"),
            entry("content-length", "182"),
            entry("content-language", "en-US"),
            entry("collection", "keep-alive"),
            entry("cache-control", " private, must-revalidate, no-cache, no-store, max-age=0"),
            entry("date", "Wed, 16 Aug 2023 21:15:01 GMT")
    );

    private static final Map.Entry<String, String>[] TEST_DATA2 = concat(TEST_DATA1, arrayOf(
            entry("last-modified", "Mon, 23 Jan 2017 13:27:29 GMT"),
            entry("accept-ranges", "bytes"),
            entry("x-frame-options", "deny"),
            entry("x-content-type-options", "nosniff"),
            entry("x-xss-protection", "0")
    ));

    private static final HashMap<String, String> TEST_MAP1 = new HashMap<>();
    private static final HashMap<String, String> TEST_MAP2 = new HashMap<>();
    private static final Headers TEST_HEADERS1 = new Headers();
    private static final Headers TEST_HEADERS2 = new Headers();

    static {
        for (Map.Entry<String, String> data : TEST_DATA1) {
            TEST_MAP1.put(data.getKey(), data.getValue());
            TEST_HEADERS1.putHeader(data.getKey(), data.getValue());
        }

        for (Map.Entry<String, String> data : TEST_DATA2) {
            TEST_MAP2.put(data.getKey(), data.getValue());
            TEST_HEADERS2.putHeader(data.getKey(), data.getValue());
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

    private static void forEachHeaders(Blackhole blackhole, Headers map) {
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
    public void testForEachHeaders1(Blackhole blackhole) {
        forEachHeaders(blackhole, TEST_HEADERS1);
    }

    @Benchmark
    public void testForEachHeaders2(Blackhole blackhole) {
        forEachHeaders(blackhole, TEST_HEADERS2);
    }

    //
    // ------ put ------
    //

    private static HashMap<String, String> putToHashMap(Map.Entry<String, String>[] datas) {
        HashMap<String, String> res = new HashMap<>();
        for (Map.Entry<String, String> data : datas) {
            res.put(data.getKey(), data.getValue());
        }
        return res;
    }

    private static Headers putToHeaders(Map.Entry<String, String>[] datas) {
        Headers res = new Headers();
        for (Map.Entry<String, String> data : datas) {
            res.putHeader(data.getKey(), data.getValue());
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
    public Object testPutToHeaders1() {
        return putToHeaders(TEST_DATA1);
    }

    @Benchmark
    public Object testPutToHeaders2() {
        return putToHeaders(TEST_DATA2);
    }

}



