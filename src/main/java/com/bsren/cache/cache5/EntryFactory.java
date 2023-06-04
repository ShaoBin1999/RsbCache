package com.bsren.cache.cache5;

import com.bsren.cache.cache5.entry.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import static com.bsren.cache.cache5.LocalCache.*;

public enum EntryFactory {
    STRONG {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new StrongEntry<>(key, hash, next);
        }
    },
    STRONG_ACCESS {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new StrongAccessEntry<>(key, hash, next);
        }

        @Override
        <K, V>  ReferenceEntry<K, V> copyEntry(
                 LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
            ReferenceEntry<K, V> newEntry = super.copyEntry(segment, original, newNext);
            copyAccessEntry(original, newEntry);
            return newEntry;
        }
    },
    STRONG_WRITE {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new StrongWriteEntry<>(key, hash, next);
        }

        @Override
        <K, V>  ReferenceEntry<K, V> copyEntry(
                 LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
             ReferenceEntry<K, V> newEntry = super.copyEntry(segment, original, newNext);
            copyWriteEntry(original, newEntry);
            return newEntry;
        }
    },
    STRONG_ACCESS_WRITE {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new StrongAccessWriteEntry<>(key, hash, next);
        }

        @Override
        <K, V>  ReferenceEntry<K, V> copyEntry(
                 LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
             ReferenceEntry<K, V> newEntry = super.copyEntry(segment, original, newNext);
            copyAccessEntry(original, newEntry);
            copyWriteEntry(original, newEntry);
            return newEntry;
        }
    },
    WEAK {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new WeakEntry<>(segment.keyReferenceQueue, key, hash, next);
        }
    },
    WEAK_ACCESS {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new  WeakAccessEntry<>(segment.keyReferenceQueue, key, hash, next);
        }

        @Override
        <K, V>  ReferenceEntry<K, V> copyEntry(
                 LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
             ReferenceEntry<K, V> newEntry = super.copyEntry(segment, original, newNext);
            copyAccessEntry(original, newEntry);
            return newEntry;
        }
    },
    WEAK_WRITE {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new  WeakWriteEntry<>(segment.keyReferenceQueue, key, hash, next);
        }

        @Override
        <K, V>  ReferenceEntry<K, V> copyEntry(
                 LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
             ReferenceEntry<K, V> newEntry = super.copyEntry(segment, original, newNext);
            copyWriteEntry(original, newEntry);
            return newEntry;
        }
    },
    WEAK_ACCESS_WRITE {
        @Override
        <K, V>  ReferenceEntry<K, V> newEntry(
                 LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next) {
            return new  WeakAccessWriteEntry<>(segment.keyReferenceQueue, key, hash, next);
        }

        @Override
        <K, V>  ReferenceEntry<K, V> copyEntry(
                 LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
             ReferenceEntry<K, V> newEntry = super.copyEntry(segment, original, newNext);
            copyAccessEntry(original, newEntry);
            copyWriteEntry(original, newEntry);
            return newEntry;
        }
    };

    // Masks used to compute indices in the following table.

    static final int ACCESS_MASK = 1;
    static final int WRITE_MASK = 2;
    static final int WEAK_MASK = 4;

    /** Look-up table for factories. */
    static final  EntryFactory[] factories = {
            STRONG,
            STRONG_ACCESS,
            STRONG_WRITE,
            STRONG_ACCESS_WRITE,
            WEAK,
            WEAK_ACCESS,
            WEAK_WRITE,
            WEAK_ACCESS_WRITE,
    };

    static  EntryFactory getFactory(
              Strength keyStrength, boolean usesAccessQueue, boolean usesWriteQueue) {
        int flags =
                ((keyStrength ==   Strength.WEAK) ? WEAK_MASK : 0)
                        | (usesAccessQueue ? ACCESS_MASK : 0)
                        | (usesWriteQueue ? WRITE_MASK : 0);
        return factories[flags];
    }

    /**
     * Creates a new entry.
     *
     * @param segment to create the entry for
     * @param key of the entry
     * @param hash of the key
     * @param next entry in the same bucket
     */
    abstract <K, V>  ReferenceEntry<K, V> newEntry(
             LocalCache.Segment<K, V> segment, K key, int hash, @Nullable  ReferenceEntry<K, V> next);

    /**
     * Copies an entry, assigning it a new {@code next} entry.
     *
     * @param original the entry to copy
     * @param newNext entry in the same bucket
     */
    // Guarded By Segment.this
    <K, V>  ReferenceEntry<K, V> copyEntry(
             LocalCache.Segment<K, V> segment,  ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newNext) {
        return newEntry(segment, original.getKey(), original.getHash(), newNext);
    }

    // Guarded By Segment.this
    <K, V> void copyAccessEntry( ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newEntry) {
        // away, as can connectAccessOrder, nullifyAccessOrder.
        newEntry.setAccessTime(original.getAccessTime());

        connectAccessOrder(original.getPreviousInAccessQueue(), newEntry);
        connectAccessOrder(newEntry, original.getNextInAccessQueue());

        nullifyAccessOrder(original);
    }

    // Guarded By Segment.this
    <K, V> void copyWriteEntry( ReferenceEntry<K, V> original,  ReferenceEntry<K, V> newEntry) {
        // away, as can connectWriteOrder, nullifyWriteOrder.
        newEntry.setWriteTime(original.getWriteTime());

        connectWriteOrder(original.getPreviousInWriteQueue(), newEntry);
        connectWriteOrder(newEntry, original.getNextInWriteQueue());

        nullifyWriteOrder(original);
    }
}