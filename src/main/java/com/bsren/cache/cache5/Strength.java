package com.bsren.cache.cache5;

import com.bsren.cache.cache5.valueReference.*;
import com.google.common.base.Equivalence;

public enum Strength {
    STRONG {
        @Override
        <K, V> ValueReference<K, V> referenceValue(
                LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight) {
            return (weight == 1)
                    ? new StrongValueReference<K, V>(value)
                    : new WeightedStrongValueReference<K, V>(value, weight);
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
            return (weight == 1)
                    ? new SoftValueReference<K, V>(segment.valueReferenceQueue, value, entry)
                    : new WeightedSoftValueReference<K, V>(segment.valueReferenceQueue, value, entry, weight);
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
            return (weight == 1)
                    ? new WeakValueReference<K, V>(segment.valueReferenceQueue, value, entry)
                    : new WeightedWeakValueReference<K, V>(
                    segment.valueReferenceQueue, value, entry, weight);
        }

        @Override
        Equivalence<Object> defaultEquivalence() {
            return Equivalence.identity();
        }
    };

    abstract <K, V> ValueReference<K, V> referenceValue(
            LocalCache.Segment<K, V> segment, ReferenceEntry<K, V> entry, V value, int weight);

    abstract Equivalence<Object> defaultEquivalence();
}
