package com.bsren.cache;



import com.bsren.cache.reference.SoftValueReference;
import com.bsren.cache.reference.StrongValueReference;
import com.bsren.cache.reference.WeakValueReference;
import com.google.common.base.Equivalence;

public enum Strength {
    STRONG {
        @Override
        <K, V> ValueReference<K, V> referenceValue(
                LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight) {
            return new StrongValueReference<K, V>(value);
        }

        @Override
        Equivalence<Object> defaultEquivalence() {
            return Equivalence.equals();
        }
    },
    SOFT {
        @Override
        <K, V> ValueReference<K, V> referenceValue(
                LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight) {
            return new SoftValueReference<K, V>(segment.valueReferenceQueue, value, entry);
        }

        @Override
        Equivalence<Object> defaultEquivalence() {
            return Equivalence.identity();
        }
    },
    WEAK {
        @Override
        <K, V> ValueReference<K, V> referenceValue(
                LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight) {
            return new WeakValueReference<K, V>(segment.valueReferenceQueue, value, entry);
        }

        @Override
        Equivalence<Object> defaultEquivalence() {
            return Equivalence.identity();
        }
    };

    abstract <K, V> ValueReference<K, V> referenceValue(
            LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight);

    /**
     * Returns the default equivalence strategy used to compare and hash keys or values referenced
     * at this strength. This strategy will be used unless the user explicitly specifies an
     * alternate strategy.
     */
    abstract Equivalence<Object> defaultEquivalence();
}
