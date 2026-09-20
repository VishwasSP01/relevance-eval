package io.github.vishwassp01.relevanceeval.backend;

/**
 * Thrown when a search backend fails to execute a query or communicate with the underlying search system.
 */
public class SearchBackendException extends RuntimeException {

    public SearchBackendException(String message) {
        super(message);
    }

    public SearchBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}
