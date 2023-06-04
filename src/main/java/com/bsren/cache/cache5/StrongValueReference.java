package com.bsren.cache.cache5;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ExecutionException;

public class StrongValueReference<K,V> implements ValueReference<K,V> {

    V value;

    public StrongValueReference(V value) {
        this.value = value;
    }

    @Override
    public V get() {
        return value;
    }

    @Override
    public ReferenceEntry<K,V> getEntry() {
        return null;
    }

    @Override
    public V waitForValue() throws ExecutionException {
        return null;
    }

    @Override
    public int getWeight() {
        return 1;
    }

    @Override
    public ValueReference<K, V> copyFor(ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> referenceEntry) {
        return null;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void notifyNewValue(@Nullable V newValue) {

    }

    @Override
    public boolean isLoading() {
        return false;
    }
}
