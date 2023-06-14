package com.bsren.cache.cache6;

public interface Value<K,V> {

    V get();

    Entry<K,V> getEntry();

    int getWeight();

    boolean isLoading();

    V waitForValue();
}
