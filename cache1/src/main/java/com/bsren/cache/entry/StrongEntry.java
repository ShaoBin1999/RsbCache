package com.bsren.cache.entry;


import com.bsren.cache.AbstractReferenceEntry;
import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.ValueReference;


import java.util.Objects;

import static com.bsren.cache.loading.Unset.unset;

public  class StrongEntry<K,V> extends AbstractReferenceEntry<K,V> {

    final K key;

    int hash;

    ReferenceEntry<K,V> next;

    volatile ValueReference<K,V> valueReference = unset();

    volatile long accessTime = Long.MAX_VALUE;

    volatile long writeTime = Long.MAX_VALUE;

    public StrongEntry(K key, int hash, ReferenceEntry<K,V> next){
        this.key = key;
        this.hash = hash;
        this.next = next;
    }

    @Override
    public ValueReference<K, V> getValueReference() {
        return valueReference;
    }

    @Override
    public void setValueReference(ValueReference<K, V> value) {
        this.valueReference = value;
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
        return Objects.hash(key, hash, next, valueReference, accessTime, writeTime);
    }
}
