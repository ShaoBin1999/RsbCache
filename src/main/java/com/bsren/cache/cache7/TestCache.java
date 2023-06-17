package com.bsren.cache.cache7;

import com.bsren.cache.abstractCache.CacheLoader;
import com.bsren.cache.abstractCache.LoadingCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sun.xml.internal.ws.api.ha.StickyFeature;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class TestCache {


    @Test
    public void test1() throws Exception {
        LocalCache<Integer,String> localCache = new LocalCache<>(10, 4,
                new CacheLoader<Integer, String>() {
                    @Override
                    public String load(Integer key) throws Exception {
                        return "1"+key;
                    }
                });
        localCache.setRefreshNanos(1000*1000*1000L*3);
        localCache.setExpireAfterWriteNanos(1000*1000*1000L*5);
        localCache.put(1,"22");

        System.out.println(localCache.getIfPresent(1));
        Thread.sleep(4000);
        System.out.println(localCache.get(1, new CacheLoader<Integer, String>() {
            @Override
            public String load(Integer key) throws Exception {
                return "2222"+key;
            }
        }));
        Thread.sleep(7000);
        System.out.println(localCache.get(1, new CacheLoader<Integer, String>() {
            @Override
            public String load(Integer key) throws Exception {
                return "3333"+key;
            }
        }));
        System.out.println(localCache.getIfPresent(1));
    }


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Cache<Integer, String> localCache = CacheBuilder.newBuilder()
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .expireAfterAccess(50,TimeUnit.SECONDS)
                .refreshAfterWrite(30, TimeUnit.SECONDS)
                .build(new com.google.common.cache.CacheLoader<Integer, String>() {
                    @Override
                    public String  load(Integer key) throws Exception {
                        return "1"+key;
                    }
                });
        localCache.put(1,"22");
        localCache.put(2,"222");
        localCache.getIfPresent(2);

        System.out.println(localCache.getIfPresent(1));
        Thread.sleep(4000);
        System.out.println(localCache.get(1, () -> "222"+1));
        Thread.sleep(7000);
        System.out.println(localCache.getIfPresent(1));

    }
}
