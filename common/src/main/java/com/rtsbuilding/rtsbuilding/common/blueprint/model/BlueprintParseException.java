package com.rtsbuilding.rtsbuilding.common.blueprint.model;

/**
 * Blueprint parse exception — thrown when reading or parsing a blueprint file.
 * <p>
 * Encapsulates parsing failure scenarios such as file format errors, data corruption, or incompatibility.
 */
public final class BlueprintParseException extends Exception {

    /**
     * Constructs an exception with the specified error message.
     *
     * @param message the message describing the parse failure reason
     */
    public BlueprintParseException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with the specified error message and root cause.
     *
     * @param message the message describing the parse failure reason
     * @param cause   the underlying exception that caused the parse failure
     */
    public BlueprintParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
