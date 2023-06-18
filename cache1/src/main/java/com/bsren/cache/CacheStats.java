package com.bsren.cache;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.annotation.CheckForNull;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.math.LongMath.saturatedAdd;
import static com.google.common.math.LongMath.saturatedSubtract;

public final class CacheStats {

    private long hitCount;

    private long missCount;

    private long loadSuccessCount;

    private long loadExceptionCount;

    private long totalLoadTime;

    private long evictionCount;

    public CacheStats(long hitCount,long missCount,
                      long loadSuccessCount,
                      long loadExceptionCount,
                      long totalLoadTime,
                      long evictionCount){
        checkArgument(hitCount >= 0);
        checkArgument(missCount >= 0);
        checkArgument(loadSuccessCount >= 0);
        checkArgument(loadExceptionCount >= 0);
        checkArgument(totalLoadTime >= 0);
        checkArgument(evictionCount >= 0);

        this.hitCount = hitCount;
        this.missCount = missCount;
        this.loadSuccessCount = loadSuccessCount;
        this.loadExceptionCount = loadExceptionCount;
        this.totalLoadTime = totalLoadTime;
        this.evictionCount = evictionCount;

    }

    public long requestCount() {
        return saturatedAdd(hitCount, missCount);
    }

    public long hitCount(){
        return hitCount;
    }

    public double hitRate(){
        long requestCount = requestCount();
        return (requestCount==0)?1.0:(double) hitCount/requestCount;
    }

    public long missCount(){
        return missCount;
    }

    public double missRate() {
        long requestCount = requestCount();
        return (requestCount == 0) ? 0.0 : (double) missCount / requestCount;
    }

    public long loadCount() {
        return saturatedAdd(loadSuccessCount, loadExceptionCount);
    }

    public long loadSuccessCount(){
        return loadSuccessCount;
    }

    public long loadExceptionCount(){
        return loadExceptionCount;
    }

    public double loadExceptionRate() {
        long totalLoadCount = saturatedAdd(loadSuccessCount, loadExceptionCount);
        return (totalLoadCount == 0) ? 0.0 : (double) loadExceptionCount / totalLoadCount;
    }

    public long totalLoadTime() {
        return totalLoadTime;
    }

    public double averageLoadPenalty() {
        long totalLoadCount = saturatedAdd(loadSuccessCount, loadExceptionCount);
        return (totalLoadCount == 0) ? 0.0 : (double) totalLoadTime / totalLoadCount;
    }

    public long evictionCount() {
        return evictionCount;
    }

    public CacheStats minus(CacheStats other) {
        return new CacheStats(
                Math.max(0, saturatedSubtract(hitCount, other.hitCount)),
                Math.max(0, saturatedSubtract(missCount, other.missCount)),
                Math.max(0, saturatedSubtract(loadSuccessCount, other.loadSuccessCount)),
                Math.max(0, saturatedSubtract(loadExceptionCount, other.loadExceptionCount)),
                Math.max(0, saturatedSubtract(totalLoadTime, other.totalLoadTime)),
                Math.max(0, saturatedSubtract(evictionCount, other.evictionCount)));
    }


    public CacheStats plus(CacheStats other) {
        return new CacheStats(
                saturatedAdd(hitCount, other.hitCount),
                saturatedAdd(missCount, other.missCount),
                saturatedAdd(loadSuccessCount, other.loadSuccessCount),
                saturatedAdd(loadExceptionCount, other.loadExceptionCount),
                saturatedAdd(totalLoadTime, other.totalLoadTime),
                saturatedAdd(evictionCount, other.evictionCount));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                hitCount, missCount, loadSuccessCount, loadExceptionCount, totalLoadTime, evictionCount);
    }

    @Override
    public boolean equals(@CheckForNull Object object) {
        if (object instanceof CacheStats) {
            CacheStats other = (CacheStats) object;
            return hitCount == other.hitCount
                    && missCount == other.missCount
                    && loadSuccessCount == other.loadSuccessCount
                    && loadExceptionCount == other.loadExceptionCount
                    && totalLoadTime == other.totalLoadTime
                    && evictionCount == other.evictionCount;
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("hitCount", hitCount)
                .add("missCount", missCount)
                .add("loadSuccessCount", loadSuccessCount)
                .add("loadExceptionCount", loadExceptionCount)
                .add("totalLoadTime", totalLoadTime)
                .add("evictionCount", evictionCount)
                .toString();
    }
}
