package com.bsren.cache.cache5.entry;


import com.bsren.cache.cache5.ReferenceEntry;
import com.bsren.cache.cache5.ValueReference;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.ref.ReferenceQueue;
import java.util.concurrent.ExecutionException;

public class UnsetValue {
    static final ValueReference<Object, Object> UNSET =
            new  ValueReference<Object, Object>() {
                @Override
                public Object get() {
                    return null;
                }

                @Override
                public int getWeight() {
                    return 0;
                }

                @Override
                public ReferenceEntry<Object, Object> getEntry() {
                    return null;
                }

                @Override
                public Object waitForValue() throws ExecutionException {
                    return null;
                }

                @Override
                public ValueReference<Object, Object> copyFor(
                        ReferenceQueue<Object> queue,
                        @Nullable Object value,
                        ReferenceEntry<Object, Object> referenceEntry) {
                    return this;
                }

                @Override
                public boolean isActive() {
                    return false;
                }

                @Override
                public void notifyNewValue(@Nullable Object newValue) {

                }

                @Override
                public boolean isLoading() {
                    return false;
                }
            };


    static <K, V> ValueReference<K, V> unset() {
        return (ValueReference<K, V>) UNSET;
    }
}
