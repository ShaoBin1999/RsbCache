package com.bsren.cache.abstractCache.loading;

import com.bsren.cache.abstractCache.ReferenceEntry;
import com.bsren.cache.abstractCache.ValueReference;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;

public class Unset {
    static final ValueReference<Object, Object> UNSET =
            new ValueReference<Object, Object>() {
                @Override
                public Object get() {
                    return null;
                }


                @Override
                public ReferenceEntry<Object, Object> getEntry() {
                    return null;
                }

                @Override
                public ValueReference<Object, Object> copyFor(
                        ReferenceQueue<Object> queue,
                        @Nullable Object value,
                        ReferenceEntry<Object, Object> entry) {
                    return this;
                }

                @Override
                public boolean isLoading() {
                    return false;
                }

            };

    public static <K, V> ValueReference<K, V> unset() {
        return (ValueReference<K, V>) UNSET;
    }
}
