package com.bsren.cache.newCache;

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
}
