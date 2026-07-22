package com.rtsbuilding.rtsbuilding.client.application.port;

public interface KeyMappingPort {
    void registerKeyMapping(String name, int defaultKey);
    boolean isKeyPressed(String name);
}
