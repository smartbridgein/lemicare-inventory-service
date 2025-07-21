package com.cosmicdoc.inventoryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * A global exception handler to provide consistent, detailed error responses
 * for all controllers in the application.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions thrown by @Valid on request bodies.
     * It extracts all field errors and formats them into a structured JSON response.
     *
     * @param ex The MethodArgumentNotValidException that was thrown.
     * @return A ResponseEntity with a 400 Bad Request status and a map of field errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        // Loop through all the errors found during validation
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            // Get the name of the field that failed validation
            String fieldName = ((FieldError) error).getField();
            // Get the error message from the validation annotation (e.g., "Supplier ID is required.")
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    // You can also centralize other exception handlers here

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<String> handleInsufficientStockException(InsufficientStockException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    /**
     * Handles IllegalStateException, which we use for business rule violations.
     * For example, trying to delete a supplier with an outstanding balance.
     */
    @ExceptionHandler(IllegalStateException.class) // <-- MUST be this exact class
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException ex) { // <-- Parameter must match
        // The response body will now be the clear message from your exception.
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

}