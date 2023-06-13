package com.bsren.cache.abstractCache;

import com.bsren.cache.newCache.ReferenceEntry;
import com.bsren.cache.newCache.ValueReference;

public abstract  class AbstractReferenceEntry<K, V> implements ReferenceEntry<K, V> {
    @Override
    public ValueReference<K, V> getValueReference() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setValueReference(ValueReference<K, V> valueReference) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ReferenceEntry<K, V> getNext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public K getKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getAccessTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAccessTime(long time) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long getWriteTime() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setWriteTime(long time) {
        throw new UnsupportedOperationException();
    }
}

