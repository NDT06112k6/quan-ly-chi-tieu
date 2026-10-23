package com.example.expensetracker.common.util;

import java.nio.charset.StandardCharsets;

public final class PasswordUtil {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private PasswordUtil() {
    }

    public static boolean isWithinBcryptLimit(String password) {
        int passwordByteLength = password.getBytes(StandardCharsets.UTF_8).length;
        boolean withinLimit = passwordByteLength <= BCRYPT_MAX_PASSWORD_BYTES;
        return withinLimit;
    }
}
