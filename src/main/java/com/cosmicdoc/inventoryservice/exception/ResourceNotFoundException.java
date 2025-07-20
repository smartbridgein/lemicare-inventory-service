package com.cosmicdoc.inventoryservice.exception;


import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception thrown when a requested resource cannot be found in the system.
 * <p>
 * This is typically used when an operation attempts to fetch an entity by its
 * unique identifier (e.g., finding a user by ID) and no such entity exists.
 * It is mapped to a 404 Not Found HTTP status.
 *
 * By extending RuntimeException, it is an "unchecked" exception.
 */
@ResponseStatus(HttpStatus.NOT_FOUND) // This annotation provides a default HTTP status code.
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Constructs a new ResourceNotFoundException with the specified detail message.
     *
     * @param message the detail message (e.g., "User with ID 123 not found.").
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new ResourceNotFoundException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause of the exception.
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}