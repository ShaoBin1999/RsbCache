package com.bsren.cache.abstractCache;


import com.google.common.cache.CacheStats;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public interface Cache<K,V> {

    V get(K key, Callable<V> loader) throws ExecutionException;

    void put(K key,V value);

    void invalidate(Object key);

    long size();

    CacheStats stats();

    void cleanUp();

    ConcurrentMap<K, V> asMap();
}
