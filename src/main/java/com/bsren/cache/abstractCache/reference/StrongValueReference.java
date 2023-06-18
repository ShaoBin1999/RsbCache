package com.bsren.cache.abstractCache.reference;



import com.bsren.cache.abstractCache.ReferenceEntry;
import com.bsren.cache.abstractCache.ValueReference;

import java.lang.ref.ReferenceQueue;

public class StrongValueReference<K, V> implements ValueReference<K, V> {
    final V referent;

    StrongValueReference(V referent) {
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
    public ValueReference<K, V> copyFor(
            ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
        return this;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public V waitForValue() {
        return get();
    }
}
