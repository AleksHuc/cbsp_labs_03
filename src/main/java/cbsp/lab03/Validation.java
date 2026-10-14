package cbsp.lab03;
import java.util.List;
/** Prepared input validation helpers. */
public final class Validation {
    private Validation() { }
    public static void samples(double[] x) {
        if (x == null || x.length == 0) throw new IllegalArgumentException("Non-empty samples required.");
        for (double v : x) if (!Double.isFinite(v)) throw new IllegalArgumentException("Samples must be finite.");
    }
    public static void positive(double v, String name) {
        if (!Double.isFinite(v) || v <= 0) throw new IllegalArgumentException(name + " must be finite and positive.");
    }
    public static void windows(int large, int small) {
        if (small < 1 || large <= small) throw new IllegalArgumentException("Require 1 <= S < L.");
    }
    public static void intervals(double[] x, List<ClickInterval> intervals) {
        samples(x);
        if (intervals == null) throw new IllegalArgumentException("Intervals required.");
        long previousEnd = -2;
        for (ClickInterval i : intervals) {
            if (i == null || i.end() >= x.length || i.start() <= previousEnd + 1)
                throw new IllegalArgumentException("Intervals must be bounded, sorted, disjoint and non-adjacent.");
            previousEnd = i.end();
        }
    }
    /** Reject overflow in squaring and in the whole squared-sample sum. */
    public static double[] squares(double[] x) {
        samples(x);
        double[] squared = new double[x.length];
        double sum = 0;
        for (int n = 0; n < x.length; n++) {
            squared[n] = x[n] * x[n]; sum += squared[n];
            if (!Double.isFinite(sum)) throw new IllegalArgumentException("Squared-sample sum overflow.");
        }
        return squared;
    }
}
