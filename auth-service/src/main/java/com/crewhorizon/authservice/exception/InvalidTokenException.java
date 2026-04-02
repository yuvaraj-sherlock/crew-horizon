package com.crewhorizon.authservice.exception;
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) { super(message); }
}
