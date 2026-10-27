package com.emergency.ledger;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>("OK", "成功", data, Trace.id()); }
    public static ApiResponse<Void> error(String code, String message) { return new ApiResponse<>(code, message, null, Trace.id()); }
}
