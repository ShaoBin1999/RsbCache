package com.bsren.cache.abstractCache;

public abstract class AbstractLoadingCache<K,V> extends AbstractCache<K,V>
               implements LoadingCache<K,V>{

    protected AbstractLoadingCache() {}


    @Override
    public void refresh(K key) {
        throw new UnsupportedOperationException();
    }
}
