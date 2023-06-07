package com.bsren.cache.abstractCache;

import com.google.common.base.Function;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public interface LoadingCache<K,V> extends Cache<K,V>, Function<K,V> {


    V get(K key) throws ExecutionException;

    void refresh(K key);

    ConcurrentMap<K,V> asMap();
}
