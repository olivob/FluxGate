package com.bryan.fluxgate.exception;

public class UnsupportedModelException extends RuntimeException {
    public UnsupportedModelException() {
        super("Unsupported model. Use 'mock-model' or 'openai'.");
    }
}
