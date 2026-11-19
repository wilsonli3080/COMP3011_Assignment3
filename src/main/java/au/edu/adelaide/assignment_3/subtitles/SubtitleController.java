package au.edu.adelaide.assignment_3.subtitles;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class SubtitleController {
    private final SubtitleService subtitleService;

    public SubtitleController(SubtitleService subtitleService) {
        this.subtitleService = subtitleService;
    }

    @PostMapping(path = "/api/v1/subtitles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubtitleResponse subtitles(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "audio", required = false) MultipartFile audio) {
        MultipartFile media = file != null ? file : audio;
        if (media == null || media.isEmpty()) {
            throw new InvalidAudioException("Upload a non-empty audio file in the 'file' or 'audio' field.");
        }
        return subtitleService.transcribe(media);
    }

    @GetMapping("/api/v1/global/usage")
    public Map<String, Double> usage() {
        return Map.of("usage", subtitleService.totalUsageSeconds());
    }
}

@RestControllerAdvice
class SubtitleExceptionHandler {
    @ExceptionHandler(InvalidAudioException.class)
    ResponseEntity<Map<String, String>> invalidAudio(InvalidAudioException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MissingApiKeyException.class)
    ResponseEntity<Map<String, String>> missingApiKey(MissingApiKeyException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.client.RestClientResponseException.class)
    ResponseEntity<Map<String, String>> transcriptionFailed(
            org.springframework.web.client.RestClientResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Transcription service returned HTTP " + exception.getStatusCode().value()));
    }

    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    ResponseEntity<Map<String, String>> transcriptionUnavailable(
            org.springframework.web.client.RestClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "Unable to reach the transcription service."));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> invalidTranscription(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", exception.getMessage()));
    }
}

class InvalidAudioException extends RuntimeException {
    InvalidAudioException(String message) {
        super(message);
    }
}
