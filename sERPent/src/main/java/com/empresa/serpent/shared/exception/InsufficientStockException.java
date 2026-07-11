package com.empresa.serpent.shared.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) { super(message); }
    @Override public HttpStatus status() { return HttpStatus.CONFLICT; }
}