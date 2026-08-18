package com.xianzhi.fridge.telemetry.application;

final class TelemetryRejectedException extends RuntimeException {
    private final String code;
    TelemetryRejectedException(String code, String message) { super(message); this.code = code; }
    String code() { return code; }
}
