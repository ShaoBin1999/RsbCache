package com.bsren.cache;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ExecutionException;

public interface ValueReference<K,V> {

    V get();

    ReferenceEntry<K,V> getEntry();

    ValueReference<K,V> copyFor(ReferenceQueue<V> queue,V value,ReferenceEntry<K,V> entry);


    boolean isLoading();

    /**
     * return true if a reference contains an active value,
     * meaning one that is still considered present in the cache.
     * non-active values consist strictly of loading values,
     * though during refresh a value may be both active and loading
     */
    boolean isActive();

    V waitForValue() throws ExecutionException;
}
