package com.bsren.cache.cache5.valueReference;

public class WeightedStrongValueReference<K, V> extends StrongValueReference<K, V> {
    final int weight;

    public WeightedStrongValueReference(V referent, int weight) {
        super(referent);
        this.weight = weight;
    }

    @Override
    public int getWeight() {
        return weight;
    }
}
