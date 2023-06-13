package com.bsren.cache.abstractCache;

import com.bsren.cache.cache6.CacheLoader;
import com.bsren.cache.cache6.LocalCache;
import com.google.common.cache.CacheStats;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import static com.google.common.base.Preconditions.checkNotNull;

public class LocalManualCache<K,V> implements Cache<K,V>, Serializable {

    LocalCache<K,V> localCache;

    public LocalManualCache(LocalCache<K,V> localCache){
        this.localCache = localCache;
    }

    public V get(K key, Callable<V> valueLoader) throws ExecutionException {
        checkNotNull(valueLoader);
        return localCache.get(
                key,
                new CacheLoader<K,V>(){
                    @Override
                    public V load(Object key) throws Exception {
                        return localCache.get(key);
                    }
                }
        );
    }

    @Override
    public V getIfPresent(Object key) {
        return null;
    }

    public void put(K key,V value){
        localCache.put(key,value);
    }

    public void invalidate(Object key){
        checkNotNull(key);
        localCache.remove(key);
    }

    public long size(){
        return localCache.getSize();
    }

    @Override
    public CacheStats stats() {
        AbstractCache.SimpleStatsCounter aggregator = new AbstractCache.SimpleStatsCounter();
        aggregator.incrementBy(localCache.globalStatsCounter);
        for (LocalCache.Segment<K, V> segment : localCache.segments) {
            aggregator.incrementBy(segment.statsCounter);
        }
        return aggregator.snapshot();
    }

    public void cleanUp(){
        localCache.cleanUp();
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return null;
    }

    private static final long serialVersionUID = 1;







}
