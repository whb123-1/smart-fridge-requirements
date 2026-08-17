package com.xianzhi.fridge.shared.web;

public record ApiEnvelope<T>(String code, String message, T data, String traceId) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>("OK", "", data, TraceId.get());
    }

    public static <T> ApiEnvelope<T> error(String code, String message, T data) {
        return new ApiEnvelope<>(code, message, data, TraceId.get());
    }
}
