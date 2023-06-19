package com.bsren.cache;

import com.bsren.cache.listeners.RemovalListener;
import com.bsren.cache.listeners.RemovalNotification;
import org.junit.Test;
import org.omg.CORBA.TIMEOUT;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class CacheTest {

    @Test
    public void test1() throws Exception {
        CacheBuilder<String,String> builder = new CacheBuilder<>();
        Cache<String, String> cache = builder.expireAfterAccess(5, TimeUnit.SECONDS)
                .expireAfterWrite(3, TimeUnit.SECONDS)
                .removalListener(new RemovalListener<String, String>() {
                    @Override
                    public void onRemoval(RemovalNotification<String, String> notification) {
                        System.out.println(notification.getKey()+" replaced");
                    }
                })
                .initialCapacity(100).build(new CacheLoader<String, String>() {
                    @Override
                    public String load(String key) throws Exception {
                        return "1" + key;
                    }
                });
        cache.put("1","2");
        cache.put("1","11");
        Thread.sleep(2);
        System.out.println(cache.getIfPresent("1"));
        String key = "key";
        System.out.println(cache.get(key, new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "load"+key;
            }
        }));
        Thread.sleep(10000);
        System.out.println(cache.getIfPresent("1"));
    }
}
