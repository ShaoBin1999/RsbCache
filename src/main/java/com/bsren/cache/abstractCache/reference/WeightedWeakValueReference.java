package com.bsren.cache.abstractCache.reference;



import com.bsren.cache.cache5.ReferenceEntry;
import com.bsren.cache.cache5.ValueReference;
import com.bsren.cache.cache5.valueReference.WeakValueReference;

import java.lang.ref.ReferenceQueue;

public class WeightedWeakValueReference<K, V> extends WeakValueReference<K, V> {
    final int weight;

    public WeightedWeakValueReference(
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
        return new WeightedWeakValueReference<>(queue, value, entry, weight);
    }
}