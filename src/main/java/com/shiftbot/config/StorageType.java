package com.shiftbot.config;

public enum StorageType {
    SQLITE,
    SHEETS;

    public static StorageType from(String value) {
        if (value == null || value.isBlank()) {
            return SQLITE;
        }
        return switch (value.trim().toLowerCase()) {
            case "sheets" -> SHEETS;
            case "sqlite" -> SQLITE;
            default -> throw new IllegalArgumentException("Unknown storage type: " + value);
        };
    }
}
