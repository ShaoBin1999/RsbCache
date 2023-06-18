package com.bsren.cache.abstractCache.reference;


public class WeightedStrongValueReference<K, V> extends StrongValueReference<K, V> {
    final int weight;

    public WeightedStrongValueReference(V referent, int weight) {
        super(referent);
        this.weight = weight;
    }
}
