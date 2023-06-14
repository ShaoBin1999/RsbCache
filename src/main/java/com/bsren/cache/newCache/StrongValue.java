package com.bsren.cache.newCache;

import java.lang.ref.ReferenceQueue;

public class StrongValue<K,V> implements ValueReference<K,V> {

    @Override
    public V get() {
        return null;
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
