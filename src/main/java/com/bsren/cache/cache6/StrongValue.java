package com.bsren.cache.cache6;

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

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public V waitForValue() {
        return null;
    }
}
