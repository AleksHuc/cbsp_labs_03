package cbsp.lab03;
/** Inclusive sample interval [start, end]. Bounds relative to a buffer are checked by repairs. */
public record ClickInterval(int start, int end) {
    public ClickInterval {
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid interval.");
    }
    public int length() { return end - start + 1; }
}
