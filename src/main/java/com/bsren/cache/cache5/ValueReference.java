package com.bsren.cache.cache5;

import com.google.common.util.concurrent.ExecutionError;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ExecutionException;

public interface ValueReference<K,V> {

    V get();

    ReferenceEntry<K,V> getEntry();

    V waitForValue() throws ExecutionException;

    int getWeight();

    ValueReference<K,V> copyFor(
            ReferenceQueue<V> queue, V value, ReferenceEntry<K,V> referenceEntry
    );

    boolean isActive();

    void notifyNewValue(@Nullable V newValue);


    boolean isLoading();
}
