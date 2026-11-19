package au.edu.adelaide.assignment_3.subtitles;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.MultipartBodyBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class OpenAiTranscriptionClient {
    private final RestClient restClient;
    private final String apiKey;
    private final Path apiKeyFile;

    public OpenAiTranscriptionClient(
            @Value("${openai.transcriptions-url}") String transcriptionsUrl,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.api-key-file:key.txt}") String apiKeyFile) {
        this.restClient = RestClient.create();
        this.transcriptionsUrl = transcriptionsUrl;
        this.apiKey = apiKey;
        this.apiKeyFile = Path.of(apiKeyFile);
    }

    private final String transcriptionsUrl;

    public JsonNode transcribe(MultipartFile audio) {
        String key = resolveApiKey();
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        Resource audioResource = audio.getResource();
        bodyBuilder.part("file", audioResource)
                .filename(audio.getOriginalFilename() == null ? "audio" : audio.getOriginalFilename())
                .contentType(audio.getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(audio.getContentType()));
        bodyBuilder.part("model", "whisper-1");
        bodyBuilder.part("response_format", "verbose_json");
        bodyBuilder.part("timestamp_granularities[]", "word");
        bodyBuilder.part("timestamp_granularities[]", "segment");

        MultiValueMap<String, org.springframework.http.HttpEntity<?>> request = bodyBuilder.build();
        return restClient.post()
                .uri(transcriptionsUrl)
                .header("Authorization", "Bearer " + key)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }

    private String resolveApiKey() {
        if (!apiKey.isBlank()) {
            return apiKey.trim();
        }
        if (Files.isRegularFile(apiKeyFile)) {
            try {
                String key = Files.readString(apiKeyFile).trim();
                if (!key.isEmpty()) {
                    return key;
                }
            } catch (IOException exception) {
                throw new MissingApiKeyException("Unable to read the OpenAI API key file: " + apiKeyFile);
            }
        }
        throw new MissingApiKeyException(
                "Set OPENAI_API_KEY or provide a non-empty key.txt file before requesting subtitles.");
    }
}
