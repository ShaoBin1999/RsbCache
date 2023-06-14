package com.bsren.cache.abstractCache;

import com.bsren.cache.abstractCache.AbstractReferenceEntry;
import com.bsren.cache.newCache.ReferenceEntry;
import com.bsren.cache.newCache.ValueReference;

import java.util.Objects;

public  class StrongEntry<K,V> extends AbstractReferenceEntry<K,V> {

    final K key;

    int hash;

    ReferenceEntry<K,V> next;

    volatile ValueReference<K,V> value;

    volatile long accessTime = Long.MAX_VALUE;

    volatile long writeTime = Long.MAX_VALUE;

    public StrongEntry(K key, int hash, ReferenceEntry<K,V> next){
        this.key = key;
        this.hash = hash;
        this.next = next;
    }

    @Override
    public ValueReference<K, V> getValueReference() {
        return value;
    }

    @Override
    public void setValueReference(ValueReference<K, V> value) {
        this.value = value;
    }

    @Override
    public ReferenceEntry<K, V> getNext() {
        return next;
    }

    @Override
    public int getHash() {
        return hash;
    }

    @Override
    public K getKey() {
        return key;
    }

    @Override
    public long getAccessTime() {
        return accessTime;
    }

    @Override
    public void setAccessTime(long time) {
        this.accessTime = time;
    }

    @Override
    public long getWriteTime() {
        return writeTime;
    }

    @Override
    public void setWriteTime(long time) {
        this.writeTime = time;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StrongEntry<?, ?> that = (StrongEntry<?, ?>) o;
        return hash == that.hash && Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, hash, next, value, accessTime, writeTime);
    }
}
