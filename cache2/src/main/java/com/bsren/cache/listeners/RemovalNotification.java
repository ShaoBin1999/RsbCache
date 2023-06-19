package com.bsren.cache.listeners;

import org.checkerframework.checker.nullness.qual.Nullable;

import javax.annotation.CheckForNull;
import java.util.AbstractMap;

import static com.google.common.base.Preconditions.checkNotNull;

public final class RemovalNotification<K, V>
        extends AbstractMap.SimpleImmutableEntry<@Nullable K, @Nullable V> {
    private final RemovalCause cause;

    /**
     * Creates a new {@code RemovalNotification} for the given {@code key}/{@code value} pair, with
     * the given {@code cause} for the removal. The {@code key} and/or {@code value} may be {@code
     * null} if they were already garbage collected.
     *
     * @since 19.0
     */
    public static <K, V> RemovalNotification<K, V> create(
            @CheckForNull K key, @CheckForNull V value, RemovalCause cause) {
        return new RemovalNotification<>(key, value, cause);
    }

    private RemovalNotification(@CheckForNull K key, @CheckForNull V value, RemovalCause cause) {
        super(key, value);
        this.cause = checkNotNull(cause);
    }

    /** Returns the cause for which the entry was removed. */
    public RemovalCause getCause() {
        return cause;
    }

    /**
     * Returns {@code true} if there was an automatic removal due to eviction (the cause is neither
     * {@link RemovalCause#EXPLICIT} nor {@link RemovalCause#REPLACED}).
     */
    public boolean wasEvicted() {
        return cause.wasEvicted();
    }

    private static final long serialVersionUID = 0;
}