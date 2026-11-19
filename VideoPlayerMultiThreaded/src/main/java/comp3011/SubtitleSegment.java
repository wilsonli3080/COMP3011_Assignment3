package comp3011;

public record SubtitleSegment(double start, double end, String text) {
    public SubtitleSegment {
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid subtitle time range");
        }
        text = text == null ? "" : text;
    }
}
