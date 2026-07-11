package com.empresa.serpent.shared.exception;

import org.springframework.http.HttpStatus;

/** Base for expected business errors whose message is safe to show to the user (in Spanish). */
public abstract class BusinessException extends RuntimeException {
    protected BusinessException(String message) {
        super(message);
    }
    public abstract HttpStatus status();
}