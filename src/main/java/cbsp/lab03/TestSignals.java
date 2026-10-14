package cbsp.lab03;
import java.util.List;
import java.util.Random;
/** Prepared synthetic reference, solely for independent numerical evaluation. */
public final class TestSignals {
    public record Example(double[] clean, double[] corrupted, List<ClickInterval> clicks) { }
    private TestSignals() { }
    public static Example generate(TaskParameters p) {
        int fs = 44100;
        double[] clean = new double[fs];
        Random r = new Random(p.seed());
        double phase = r.nextDouble() * Math.PI * 2;
        for (int n = 0; n < clean.length; n++) {
            double angle = 2 * Math.PI * p.syntheticFrequency() * n / fs;
            clean[n] = 0.20 * Math.sin(angle + phase) + 0.04 * Math.sin(2 * angle + 0.4);
        }
        int a = 9000 + r.nextInt(1000), b = 27000 + r.nextInt(1000);
        int width = 12 + r.nextInt(9);
        List<ClickInterval> intervals = List.of(new ClickInterval(a, a+width-1), new ClickInterval(b, b+width-1));
        double[] damaged = clean.clone();
        for (ClickInterval i : intervals) for (int n = i.start(); n <= i.end(); n++)
            damaged[n] += n - i.start() < i.length()/2 ? 0.70 : -0.70;
        return new Example(clean, damaged, intervals);
    }
}
