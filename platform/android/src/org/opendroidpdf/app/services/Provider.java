package org.opendroidpdf.app.services;

/**
 * Minimal provider interface to avoid java.util.function.Supplier (API24 lint).
 */
public interface Provider<T> {
    T get();
}
