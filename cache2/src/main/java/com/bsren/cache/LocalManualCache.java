package com.bsren.cache;



import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;


public class LocalManualCache<K, V> implements Cache<K, V>, Serializable {

    final LocalCache<K, V> localCache;

    LocalManualCache(LocalCache<K, V> localCache) {
        this.localCache = localCache;
    }

    public LocalManualCache(CacheBuilder<? super K, ? super V> builder) {
        this(new LocalCache<K, V>(builder, null));
    }

    @Override
    public  V getIfPresent(Object key) {
        return localCache.getIfPresent(key);
    }

    @Override
    public V get(K key, final Callable<V> valueLoader) throws Exception {
        checkNotNull(valueLoader);
        return localCache.get(
                key,
                new CacheLoader<K, V>() {
                    @Override
                    public V load(Object key) throws Exception {
                        return valueLoader.call();
                    }
                });
    }

    @Override
    public void put(K key, V value) {
        localCache.put(key,value);
    }

    @Override
    public void invalidate(Object key) {
        checkNotNull(key);
        localCache.remove(key);
    }

    @Override
    public long size() {
        return localCache.longSize();
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

    @Override
    public void cleanUp() {
        localCache.cleanUp();
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        //todo
        return null;
    }


}