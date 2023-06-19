package com.bsren.cache.weigher;

@FunctionalInterface
public interface Weigher<K,V> {

    int weigh(K key,V value);
}
