package com.bsren.cache;

import java.lang.ref.ReferenceQueue;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

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

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public V waitForValue() throws ExecutionException {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StrongValueReference<?, ?> that = (StrongValueReference<?, ?>) o;
        return Objects.equals(referent, that.referent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referent);
    }
}
