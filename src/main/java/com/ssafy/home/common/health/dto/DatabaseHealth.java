package com.ssafy.home.common.health.dto;

public record DatabaseHealth(boolean connected, Integer probe, String error) {

    public static DatabaseHealth connected(Integer probe) {
        return new DatabaseHealth(true, probe, null);
    }

    public static DatabaseHealth disconnected(String error) {
        return new DatabaseHealth(false, null, error);
    }
}
