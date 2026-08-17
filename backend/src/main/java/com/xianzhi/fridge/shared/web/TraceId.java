package com.xianzhi.fridge.shared.web;

public final class TraceId {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceId() { }

    public static void set(String traceId) { CURRENT.set(traceId); }
    public static String get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
