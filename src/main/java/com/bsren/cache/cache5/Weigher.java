package com.bsren.cache.cache5;


@FunctionalInterface
public interface Weigher<K,V> {

    int weigh(K key, V value);

}
