package com.goride.server;

/** Lỗi nghiệp vụ — message được gửi nguyên văn về client. */
public class AppException extends RuntimeException {
    public AppException(String message) { super(message); }
}
