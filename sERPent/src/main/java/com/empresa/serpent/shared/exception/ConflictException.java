package com.empresa.serpent.shared.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {
    public ConflictException(String message) { super(message); }
    @Override public HttpStatus status() { return HttpStatus.CONFLICT; }
}