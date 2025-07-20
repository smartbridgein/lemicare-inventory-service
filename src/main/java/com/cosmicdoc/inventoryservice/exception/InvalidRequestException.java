package com.cosmicdoc.inventoryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException (String message) {
        super(message);
    }
}
