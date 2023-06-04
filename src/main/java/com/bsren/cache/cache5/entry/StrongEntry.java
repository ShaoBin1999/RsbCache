package com.bsren.cache.cache5.entry;

import com.bsren.cache.cache5.ReferenceEntry;
import com.bsren.cache.cache5.ValueReference;

import static com.bsren.cache.cache5.entry.UnsetValue.unset;

public  class StrongEntry<K,V> extends AbstractReferenceEntry<K,V> {
    final K key;

    public StrongEntry(K key, int hash, ReferenceEntry<K, V> next) {
        this.key = key;
        this.hash = hash;
        this.next = next;
    }

    @Override
    public K getKey() {
        return this.key;
    }

    // The code below is exactly the same for each entry type.

    final int hash;
    final ReferenceEntry<K, V> next;
    volatile ValueReference<K, V> valueReference = unset();

    @Override
    public ValueReference<K, V> getValueReference() {
        return valueReference;
    }

    @Override
    public void setValueReference(ValueReference<K, V> value) {
        this.valueReference = value;
    }

    @Override
    public int getHash() {
        return hash;
    }

    @Override
    public ReferenceEntry<K, V> getNext() {
        return next;
    }
}
//}
