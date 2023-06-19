package com.bsren.cache.reference;

import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.ValueReference;

import java.lang.ref.ReferenceQueue;

public class WeightedSoftValueReference<K, V> extends SoftValueReference<K, V> {
    final int weight;

    WeightedSoftValueReference(
            ReferenceQueue<V> queue, V referent, ReferenceEntry<K, V> entry, int weight) {
        super(queue, referent, entry);
        this.weight = weight;
    }

    @Override
    public int getWeight() {
        return weight;
    }

    @Override
    public ValueReference<K, V> copyFor(
            ReferenceQueue<V> queue, V value, ReferenceEntry<K, V> entry) {
        return new WeightedSoftValueReference<>(queue, value, entry, weight);
    }
}
