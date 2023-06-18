package com.bsren.cache;

import org.junit.Test;
import org.omg.CORBA.TIMEOUT;

import java.util.concurrent.TimeUnit;

public class CacheTest {

    @Test
    public void test1() throws InterruptedException {
        CacheBuilder<String,String> builder = new CacheBuilder<>();
        Cache<String, String> cache = builder.expireAfterAccess(10, TimeUnit.SECONDS)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .initialCapacity(100).build(new CacheLoader<String, String>() {
                    @Override
                    public String load(String key) throws Exception {
                        return "1" + key;
                    }
                });
        cache.put("1","2");
        Thread.sleep(5000);
        System.out.println(cache.getIfPresent("1"));
    }
}
