package com.bsren.cache.cache4;

public enum RemovalCause {


    EXPLICIT {
        @Override
        boolean wasEvicted() {
            return false;
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