package com.empresa.serpent.shared.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BusinessException {
    public ValidationException(String message) { super(message); }
    @Override public HttpStatus status() { return HttpStatus.BAD_REQUEST; }
}