package com.bsren.cache.reference;

import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.ValueReference;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

public class WeakValueReference<K, V> extends WeakReference<V> implements ValueReference<K, V> {

    final ReferenceEntry<K, V> entry;

    public WeakValueReference(ReferenceQueue<V> queue, V referent, ReferenceEntry<K, V> entry) {
        super(referent, queue);
        this.entry = entry;
    }

    @Override
    public ReferenceEntry<K, V> getEntry() {
        return entry;
    }

    @Override
    public ValueReference<K, V> copyFor(
            ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
        return new WeakValueReference<>(queue, value, entry);
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