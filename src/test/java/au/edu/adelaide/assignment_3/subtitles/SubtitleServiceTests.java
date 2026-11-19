package au.edu.adelaide.assignment_3.subtitles;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubtitleServiceTests {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void filtersLowConfidenceNoSpeechSegmentsAndAccumulatesUsage() {
        OpenAiTranscriptionClient transcriptionClient = mock(OpenAiTranscriptionClient.class);
        when(transcriptionClient.transcribe(any())).thenReturn(jsonMapper.readTree("""
                {
                  "language": "english",
                  "duration": 12.5,
                  "segments": [
                    {"start": 0.0, "end": 1.0, "text": " Hello.", "avg_logprob": -0.2, "no_speech_prob": 0.1},
                    {"start": 1.1, "end": 2.0, "text": " noise", "avg_logprob": -1.2, "no_speech_prob": 0.8}
                  ],
                  "words": [
                    {"start": 0.1, "end": 0.8, "word": "Hello."},
                    {"start": 1.2, "end": 1.8, "word": "noise"}
                  ]
                }
                """));
        SubtitleService service = new SubtitleService(transcriptionClient);

        SubtitleResponse response = service.transcribe(new MockMultipartFile("file", "clip.wav", "audio/wav", new byte[]{1}));

        assertEquals(" Hello.", response.text());
        assertEquals("english", response.language());
        assertEquals(12.5, response.duration());
        assertEquals(1, response.segments().size());
        assertEquals(1, response.words().size());
        assertEquals("Hello.", response.words().get(0).text());
        assertEquals(12.5, service.totalUsageSeconds());
    }

    @Test
    void includesSegmentsWhenOnlyOneLowConfidenceThresholdIsExceeded() {
        OpenAiTranscriptionClient transcriptionClient = mock(OpenAiTranscriptionClient.class);
        when(transcriptionClient.transcribe(any())).thenReturn(jsonMapper.readTree("""
                {
                  "duration": 4.0,
                  "segments": [
                    {"start": 0.0, "end": 1.0, "text": " Keep this.", "avg_logprob": -0.8, "no_speech_prob": 0.9}
                  ],
                  "words": [{"start": 0.1, "end": 0.8, "word": "Keep"}]
                }
                """));
        SubtitleService service = new SubtitleService(transcriptionClient);

        SubtitleResponse response = service.transcribe(new MockMultipartFile("file", "clip.wav", "audio/wav", new byte[]{1}));

        assertEquals(1, response.segments().size());
        assertEquals(4.0, service.totalUsageSeconds());
    }
}
