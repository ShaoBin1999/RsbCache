package com.bsren.cache;

import com.google.common.util.concurrent.UncheckedExecutionException;



public class LocalLoadingCache<K,V>
        extends LocalManualCache<K,V> implements LoadingCache<K,V>{

    public LocalLoadingCache(
            CacheBuilder<? super K, ? super V> builder, CacheLoader<? super K, V> loader) {
        super(new LocalCache<K, V>(builder, loader));
    }
    @Override
    public V get(K key) throws Exception {
        return localCache.getOrLoad(key);
    }

    @Override
    public V getUnchecked(K key) {
        try {
            return get(key);
        } catch (Exception e) {
            throw new UncheckedExecutionException(e.getCause());
        }
    }


    @Override
    public void refresh(K key) {
        localCache.refresh(key);
    }

    // Serialization Support

    private static final long serialVersionUID = 1;

    @Override
    public V apply(K key) {
        return getUnchecked(key);
    }
}
