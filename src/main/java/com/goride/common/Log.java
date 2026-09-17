package com.goride.common;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class Log {
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {}

    public static void info(String msg) {
        System.out.println("[" + LocalTime.now().format(F) + "] " + msg);
    }

    public static void error(String msg, Throwable t) {
        System.err.println("[" + LocalTime.now().format(F) + "] ERROR " + msg + (t == null ? "" : " — " + t));
        if (t != null && !(t instanceof IllegalArgumentException)) t.printStackTrace();
    }
}
