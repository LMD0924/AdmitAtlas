package com.admitatlas.common.support;

import java.util.concurrent.atomic.AtomicLong;

/** Application-side IDs so the portable DDL needs no AUTO_INCREMENT / sequence dialect. */
public final class Ids {

    private static final AtomicLong SEQ =
            new AtomicLong(System.currentTimeMillis() * 1000L);

    private Ids() {
    }

    public static long next() {
        return SEQ.incrementAndGet();
    }

    public static String taskId() {
        return "MT-" + next();
    }
}
