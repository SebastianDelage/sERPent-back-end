package com.empresa.serpent.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated but is not allowed to perform this operation — typically
 * operating on a warehouse that is not among the ones assigned to them.
 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) { super(message); }
    @Override public HttpStatus status() { return HttpStatus.FORBIDDEN; }
}
