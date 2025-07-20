package com.cosmicdoc.inventoryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception thrown when a sales or stock transfer operation cannot be
 * completed because there is not enough quantity of an item in stock.
 * <p>
 * This is a specific business rule violation. It maps to a 409 Conflict
 * HTTP status, indicating that the request is valid but cannot be processed
 * due to the current state of the resource (the inventory).
 *
 * By extending RuntimeException, it is an "unchecked" exception.
 */
@ResponseStatus(HttpStatus.CONFLICT) // This annotation provides a default HTTP status code.
public class InsufficientStockException extends RuntimeException {

    /**
     * Constructs a new InsufficientStockException with the specified detail message.
     *
     * @param message the detail message (e.g., "Insufficient stock for medicine: Paracetamol 500mg").
     */
    public InsufficientStockException(String message) {
        super(message);
    }

    /**
     * Constructs a new InsufficientStockException with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause the cause of the exception.
     */
    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}