package com.bsren.cache;


public enum RemovalCause {

    /**
     * removed by the user
     */
    EXPLICIT {
        @Override
        boolean wasEvicted() {
            return false;
        }
    },

    /**
     * replaced by the user
     */
    REPLACED {
        @Override
        boolean wasEvicted() {
            return false;
        }
    },

    /**
     * garbage-collected
     */
    COLLECTED {
        @Override
        boolean wasEvicted() {
            return true;
        }
    },

    /**
     * time expired
     */
    EXPIRED {
        @Override
        boolean wasEvicted() {
            return true;
        }
    },


    /**
     * evicted due to size limitation
     */
    SIZE {
        @Override
        boolean wasEvicted() {
            return true;
        }
    };

    /**
     * Returns {@code true} if there was an automatic removal due to eviction (the cause is neither
     * {@link #EXPLICIT} nor {@link #REPLACED}).
     */
    abstract boolean wasEvicted();
}
