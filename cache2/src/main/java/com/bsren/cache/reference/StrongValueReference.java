package com.bsren.cache.reference;



import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.ValueReference;

import java.lang.ref.ReferenceQueue;

public class StrongValueReference<K, V> implements ValueReference<K, V> {
    final V referent;

    public StrongValueReference(V referent) {
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

    @Override
    public int getWeight() {
        return 1;
    }
}
