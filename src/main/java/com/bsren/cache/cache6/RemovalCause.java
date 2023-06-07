package com.bsren.cache.cache6;

public enum RemovalCause {


    EXPLICIT {
        @Override
        boolean wasEvicted() {
            return false;
        }
    },

    COLLECTED {
        @Override
        boolean wasEvicted() {
            return true;
        }
    },


    EXPIRED {
        @Override
        boolean wasEvicted() {
            return true;
        }
    },


    SIZE {
        @Override
        boolean wasEvicted() {
            return true;
        }
    };

    abstract boolean wasEvicted();
}
