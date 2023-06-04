package com.bsren.cache.cache4;

public interface Value<K,V> {

    V get();

    Entry<K,V> getEntry();

    int getWeight();
}
