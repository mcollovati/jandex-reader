package io.github.mcollovati.jandexreader;

/**
 * An expected failure whose message is shown to the user without a stack trace.
 */
public class ToolException extends RuntimeException {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
