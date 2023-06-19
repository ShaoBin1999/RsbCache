package com.bsren.cache.reference;



public class WeightedStrongValueReference<K, V> extends StrongValueReference<K, V> {
    final int weight;

    WeightedStrongValueReference(V referent, int weight) {
        super(referent);
        this.weight = weight;
    }

    @Override
    public int getWeight() {
        return weight;
    }
}
