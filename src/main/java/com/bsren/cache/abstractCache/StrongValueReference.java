package com.bsren.cache.abstractCache;

import com.bsren.cache.newCache.ReferenceEntry;
import com.bsren.cache.newCache.ValueReference;

import java.lang.ref.ReferenceQueue;

public class StrongValueReference<K,V> implements ValueReference<K,V> {

    V referent;

    public StrongValueReference(V referent){
        this.referent = referent;
    }

    @Override
    public V get() {
        return referent;
    }

    @Override
    public ReferenceEntry<K, V> getEntry() {
        return null;
    }

    @Override
    public ValueReference<K, V> copyFor(ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
        return null;
    }
}
