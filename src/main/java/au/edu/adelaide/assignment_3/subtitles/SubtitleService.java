package au.edu.adelaide.assignment_3.subtitles;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.DoubleAdder;

@Service
public class SubtitleService {
    private final OpenAiTranscriptionClient transcriptionClient;
    private final DoubleAdder totalUsageSeconds = new DoubleAdder();

    public SubtitleService(OpenAiTranscriptionClient transcriptionClient) {
        this.transcriptionClient = transcriptionClient;
    }

    public SubtitleResponse transcribe(MultipartFile audio) {
        JsonNode transcription = transcriptionClient.transcribe(audio);
        double duration = requiredNumber(transcription, "duration");
        if (duration < 0 || !Double.isFinite(duration)) {
            throw new IllegalStateException("Transcription response contained an invalid duration.");
        }

        JsonNode sourceSegments = transcription.path("segments");
        JsonNode sourceWords = transcription.path("words");
        if (!sourceSegments.isArray() || !sourceWords.isArray()) {
            throw new IllegalStateException("Transcription response did not contain word and segment timestamps.");
        }

        List<SubtitleResponse.Segment> segments = new ArrayList<>();
        List<SubtitleResponse.Word> words = new ArrayList<>();
        for (JsonNode segment : sourceSegments) {
            double start = requiredNumber(segment, "start");
            double end = requiredNumber(segment, "end");
            double avgLogprob = optionalNumber(segment, "avg_logprob", 0.0);
            double noSpeechProb = optionalNumber(segment, "no_speech_prob", 0.0);
            if (noSpeechProb > 0.6 && avgLogprob < -1.0) {
                continue;
            }

            String text = segment.path("text").asText("");
            segments.add(new SubtitleResponse.Segment(start, end, text, avgLogprob, noSpeechProb));
            for (JsonNode word : sourceWords) {
                double wordStart = requiredNumber(word, "start");
                double wordEnd = requiredNumber(word, "end");
                if (wordStart <= end && wordEnd >= start) {
                    words.add(new SubtitleResponse.Word(wordStart, wordEnd, word.path("word").asText("")));
                }
            }
        }

        totalUsageSeconds.add(duration);
        String text = segments.stream()
                .map(SubtitleResponse.Segment::text)
                .filter(segmentText -> !segmentText.isBlank())
                .reduce((left, right) -> left + right)
                .orElse("");
        return new SubtitleResponse(
                text,
                transcription.path("language").asText(""),
                duration,
                List.copyOf(segments),
                List.copyOf(words));
    }

    public double totalUsageSeconds() {
        return totalUsageSeconds.sum();
    }

    private static double requiredNumber(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) {
            throw new IllegalStateException("Transcription response contained an invalid " + field + ".");
        }
        return value.asDouble();
    }

    private static double optionalNumber(JsonNode node, String field, double defaultValue) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : defaultValue;
    }
}
