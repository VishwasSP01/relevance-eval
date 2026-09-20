package io.github.vishwassp01.relevanceeval.io;

import java.nio.file.Path;

/**
 * Thrown when loading a judgment set fails due to missing files, I/O errors,
 * malformed YAML syntax/structure, or invalid judgment grades.
 * <p>
 * Provides user-readable error messages detailing the problematic file path and the cause.
 */
public class JudgmentSetException extends RuntimeException {

    private final Path path;

    /**
     * Constructs a new JudgmentSetException with the specified file path and user-readable problem description.
     *
     * @param path    the path to the judgment set file, may be null if unknown
     * @param problem a human-readable description of the problem
     */
    public JudgmentSetException(Path path, String problem) {
        super(formatMessage(path, problem));
        this.path = path;
    }

    /**
     * Constructs a new JudgmentSetException with the specified file path, description, and underlying cause.
     *
     * @param path    the path to the judgment set file, may be null if unknown
     * @param problem a human-readable description of the problem
     * @param cause   the underlying exception that caused this failure
     */
    public JudgmentSetException(Path path, String problem, Throwable cause) {
        super(formatMessage(path, problem), cause);
        this.path = path;
    }

    /**
     * Returns the file path associated with this error.
     *
     * @return the file path, or null if unknown
     */
    public Path getPath() {
        return path;
    }

    private static String formatMessage(Path path, String problem) {
        String pathDisplay = (path != null) ? path.toString() : "<unknown>";
        return "Failed to load judgment set from '" + pathDisplay + "': " + problem;
    }
}
