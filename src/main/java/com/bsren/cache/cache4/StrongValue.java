package com.bsren.cache.cache4;

public class StrongValue<K,V> implements Value<K,V> {

    V value;

    public StrongValue(V value) {
        this.value = value;
    }

    @Override
    public V get() {
        return value;
    }

    @Override
    public Entry<K,V> getEntry() {
        return null;
    }

    @Override
    public int getWeight() {
        return 1;
    }
}
