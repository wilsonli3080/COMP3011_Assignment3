package comp3011;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SubtitleClient implements AutoCloseable {
    private static final URI SUBTITLES_URI = URI.create("http://localhost:8080/api/v1/subtitles");
    private static final int MAX_UPLOAD_BYTES = 25 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ExecutorService uploadExecutor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "subtitle-upload");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    CompletableFuture<List<SubtitleSegment>> transcribe(Path mediaFile) {
        return CompletableFuture.supplyAsync(() -> createUpload(mediaFile), uploadExecutor)
                .thenCompose(upload -> {
                    HttpRequest.BodyPublisher body;
                    try {
                        body = HttpRequest.BodyPublishers.ofFile(upload.path());
                    } catch (IOException exception) {
                        deleteUpload(upload.path());
                        throw new CompletionException(exception);
                    }
                    HttpRequest request = HttpRequest.newBuilder(SUBTITLES_URI)
                            .timeout(Duration.ofMinutes(5))
                            .header("Content-Type", "multipart/form-data; boundary=" + upload.boundary())
                            .POST(body)
                            .build();
                    return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenApply(this::parseResponse)
                            .whenComplete((result, error) -> deleteUpload(upload.path()));
                });
    }

    @Override
    public void close() {
        uploadExecutor.shutdownNow();
    }

    private Upload createUpload(Path mediaFile) {
        try {
            long fileSize = Files.size(mediaFile);
            if (fileSize == 0) {
                throw new IllegalArgumentException("The selected media file is empty.");
            }
            if (fileSize > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "The media file exceeds the 25 MB transcription upload limit.");
            }

            String boundary = "----SubtitleUpload" + UUID.randomUUID().toString().replace("-", "");
            Path bodyFile = Files.createTempFile("subtitle-upload-", ".multipart");
            String filename = mediaFile.getFileName().toString().replace("\"", "");
            String contentType = Files.probeContentType(mediaFile);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            try (var output = Files.newOutputStream(bodyFile)) {
                write(output, "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                        + "Content-Type: " + contentType + "\r\n\r\n");
                Files.copy(mediaFile, output);
                write(output, "\r\n--" + boundary + "--\r\n");
            } catch (IOException exception) {
                Files.deleteIfExists(bodyFile);
                throw exception;
            }
            return new Upload(bodyFile, boundary);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read media for subtitle transcription.", exception);
        }
    }

    private List<SubtitleSegment> parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body();
            if (detail.length() > 200) {
                detail = detail.substring(0, 200);
            }
            throw new IllegalStateException(
                    "Subtitle server returned HTTP " + response.statusCode() + ": " + detail);
        }

        try {
            JsonNode segments = JSON.readTree(response.body()).path("segments");
            if (!segments.isArray()) {
                throw new IllegalStateException("Subtitle server response has no segment list.");
            }
            List<SubtitleSegment> result = new ArrayList<>();
            for (JsonNode segment : segments) {
                double start = number(segment, "start");
                double end = number(segment, "end");
                String text = segment.path("text").asText("");
                if (!text.isBlank()) {
                    result.add(new SubtitleSegment(start, end, text));
                }
            }
            return List.copyOf(result);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse the subtitle server response.", exception);
        }
    }

    private static double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalStateException("Subtitle segment has no valid " + field + " timestamp.");
        }
        return value.asDouble();
    }

    private static void write(java.io.OutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteUpload(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            System.err.println("Unable to delete temporary subtitle upload file: " + path);
        }
    }

    private record Upload(Path path, String boundary) {
    }
}
