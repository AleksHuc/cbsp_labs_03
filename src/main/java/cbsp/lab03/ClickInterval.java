package cbsp.lab03;
/** Inclusive sample interval [start, end], assumed valid and within the signal. */
public record ClickInterval(int start, int end) {
    public int length() { return end - start + 1; }
}
