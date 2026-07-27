package com.rtsbuilding.rtsbuilding.server;

/**
 * Marker interface for all RTS server-side services.
 *
 * <p>Service classes implementing this interface are automatically discovered and registered
 * via {@link java.util.ServiceLoader}, without needing to be manually registered one by one
 * in {@link RtsServer#init()}.
 *
 * <p>Service classes must provide a public no-argument constructor.
 */
public interface RtsService {
}
