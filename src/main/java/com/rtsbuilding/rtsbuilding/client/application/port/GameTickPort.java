package com.rtsbuilding.rtsbuilding.client.application.port;

public interface GameTickPort {
    void onTickPre();
    void onTickPost();
}
