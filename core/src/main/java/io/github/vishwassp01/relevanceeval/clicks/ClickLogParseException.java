package io.github.vishwassp01.relevanceeval.clicks;

/**
 * Thrown when parsing a click log CSV file fails due to malformed syntax,
 * missing headers, or invalid row values.
 */
public class ClickLogParseException extends RuntimeException {

    private final int lineNumber;

    public ClickLogParseException(int lineNumber, String message) {
        super("Line " + lineNumber + ": " + message);
        this.lineNumber = lineNumber;
    }

    public int lineNumber() {
        return lineNumber;
    }
}
