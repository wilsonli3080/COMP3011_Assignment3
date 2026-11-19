package au.edu.adelaide.assignment_3.subtitles;

import java.util.List;

public record SubtitleResponse(
        String text,
        String language,
        double duration,
        List<Segment> segments,
        List<Word> words) {

    public record Segment(
            double start,
            double end,
            String text,
            double avgLogprob,
            double noSpeechProb) {
    }

    public record Word(double start, double end, String text) {
    }
}
