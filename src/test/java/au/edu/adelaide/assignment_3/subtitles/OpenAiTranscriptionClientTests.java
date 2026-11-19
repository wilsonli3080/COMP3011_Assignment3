package au.edu.adelaide.assignment_3.subtitles;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiTranscriptionClientTests {
    @Test
    void sendsWhisperRequestWithBothTimestampGranularities() throws IOException {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/transcriptions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"duration":1.0,"segments":[],"words":[]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            OpenAiTranscriptionClient client = new OpenAiTranscriptionClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/transcriptions",
                    "test-api-key",
                    "unused-key.txt");

            client.transcribe(new MockMultipartFile("file", "clip.wav", "audio/wav", new byte[]{1, 2, 3}));

            assertEquals("Bearer test-api-key", authorization.get());
            assertTrue(requestBody.get().contains("name=\"model\""));
            assertTrue(requestBody.get().contains("whisper-1"));
            assertTrue(requestBody.get().contains("name=\"response_format\""));
            assertTrue(requestBody.get().contains("verbose_json"));
            assertEquals(2, occurrences(requestBody.get(), "name=\"timestamp_granularities[]\""));
            assertTrue(requestBody.get().contains("\r\nword\r\n"));
            assertTrue(requestBody.get().contains("\r\nsegment\r\n"));
            assertTrue(requestBody.get().contains("name=\"file\"; filename=\"clip.wav\""));
        } finally {
            server.stop(0);
        }
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
