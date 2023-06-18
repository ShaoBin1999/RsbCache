package com.bsren.cache;

import java.util.concurrent.ExecutionException;

public abstract class AbstractLoadingCache<K,V> extends AbstractCache<K,V>
               implements LoadingCache<K,V>{

    protected AbstractLoadingCache() {}


    @Override
    public V get(K key) throws ExecutionException {
        return null;
    }

    @Override
    public void refresh(K key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V getIfPresent(Object key) {
        return null;
    }

    @Override
    public V apply(K input) {
        return null;
    }
}
