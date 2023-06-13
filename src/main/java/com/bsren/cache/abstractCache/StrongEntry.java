package com.bsren.cache.abstractCache;

import com.bsren.cache.abstractCache.AbstractReferenceEntry;
import com.bsren.cache.newCache.ReferenceEntry;
import com.bsren.cache.newCache.ValueReference;

public  class StrongEntry<K,V> extends AbstractReferenceEntry<K,V> {

    final K key;

    int hash;

    ReferenceEntry<K,V> next;

    volatile ValueReference<K,V> value;

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
}
