package com.bsren.cache.newCache;

import java.lang.ref.ReferenceQueue;

public interface ValueReference<K,V> {

    V get();

    ReferenceEntry<K,V> getEntry();

    ValueReference<K,V> copyFor(ReferenceQueue<V> queue,V value,ReferenceEntry<K,V> entry);




}