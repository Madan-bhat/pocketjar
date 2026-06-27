package com.mchost.service;

interface IServerHost {
    void sendCommand(String command);
    boolean isRunning();
    int exitCode();
}
