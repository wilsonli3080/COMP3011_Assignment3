package au.edu.adelaide.assignment_3.subtitles;

public class MissingApiKeyException extends RuntimeException {
    public MissingApiKeyException(String message) {
        super(message);
    }
}
