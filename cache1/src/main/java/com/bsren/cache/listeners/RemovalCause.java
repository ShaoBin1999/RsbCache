package com.bsren.cache.listeners;


public enum RemovalCause {

    /**
     * removed by the user
     */
    EXPLICIT {
        @Override
        public boolean wasEvicted() {
            return false;
        }
    },

    /**
     * replaced by the user
     */
    REPLACED {
        @Override
        public boolean wasEvicted() {
            return false;
        }
    },

    /**
     * garbage-collected
     */
    COLLECTED {
        @Override
        public boolean wasEvicted() {
            return true;
        }
    },

    /**
     * time expired
     */
    EXPIRED {
        @Override
        public boolean wasEvicted() {
            return true;
        }
    },


    /**
     * evicted due to size limitation
     */
    SIZE {
        @Override
        public boolean wasEvicted() {
            return true;
        }
    };

    /**
     * Returns {@code true} if there was an automatic removal due to eviction (the cause is neither
     * {@link #EXPLICIT} nor {@link #REPLACED}).
     */
    public abstract boolean wasEvicted();
}
