package com.marketmono.service;

public interface WebSocketRelayPublisher {

    void publish(String destination, Object payload);
}