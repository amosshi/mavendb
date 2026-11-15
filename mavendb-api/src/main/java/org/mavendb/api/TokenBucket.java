package org.mavendb.api;

/**
 * Token bucket: holds up to <code>capacity</code> tokens, refilled at a steady rate; each request takes one.
 * Thread safe.
 */
final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final double capacity;
    private final double tokensPerNano;
    private double tokens;
    private long lastRefill;

    /**
     * @param capacity        Largest burst
     * @param tokensPerSecond Refill rate
     * @param now             Current {@link System#nanoTime()}
     */
    TokenBucket(int capacity, double tokensPerSecond, long now) {
        this.capacity = capacity;
        this.tokensPerNano = tokensPerSecond / NANOS_PER_SECOND;
        this.tokens = capacity;
        this.lastRefill = now;
    }

    /**
     * Take one token.
     *
     * @param now Current {@link System#nanoTime()}
     * @return Whole tokens left after taking one, or -1 when the bucket is empty
     */
    synchronized long tryConsume(long now) {
        refill(now);
        if (this.tokens < 1) {
            return -1;
        }
        this.tokens -= 1;
        return (long) this.tokens;
    }

    /**
     * @param now Current {@link System#nanoTime()}
     * @return Whole seconds until the next token, at least 1: the value of a <code>Retry-After</code> header
     */
    synchronized long secondsUntilNextToken(long now) {
        refill(now);
        double missing = Math.max(0, 1 - this.tokens);
        return Math.max(1, (long) Math.ceil(missing / this.tokensPerNano / NANOS_PER_SECOND));
    }

    /**
     * @param now Current {@link System#nanoTime()}
     * @return True when the bucket is full again, so the client has been idle and can be forgotten
     */
    synchronized boolean isFull(long now) {
        refill(now);
        return this.tokens >= this.capacity;
    }

    private void refill(long now) {
        long elapsed = now - this.lastRefill;
        if (elapsed > 0) {
            this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.tokensPerNano);
            this.lastRefill = now;
        }
    }
}
