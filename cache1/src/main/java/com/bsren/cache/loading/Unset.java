package com.bsren.cache.loading;

import com.bsren.cache.ReferenceEntry;
import com.bsren.cache.ValueReference;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ExecutionException;

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

                @Override
                public boolean isActive() {
                    return false;
                }

                @Override
                public Object waitForValue() throws ExecutionException {
                    return null;
                }

            };

    public static <K, V> ValueReference<K, V> unset() {
        return (ValueReference<K, V>) UNSET;
    }
}
