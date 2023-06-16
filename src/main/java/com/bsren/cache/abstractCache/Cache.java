package com.bsren.cache.abstractCache;


import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public interface Cache<K,V> {

    /**
     * if cached,return
     * otherwise create,cache and return
     *
     */
    V get(K key, Callable<V> loader) throws ExecutionException;


    V getIfPresent(Object key);

    void put(K key,V value);

    void invalidate(Object key);

    long size();

    CacheStats stats();

    void cleanUp();

    ConcurrentMap<K, V> asMap();
}
