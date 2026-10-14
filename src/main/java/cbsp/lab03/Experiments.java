package cbsp.lab03;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Prepared experiments: five cases corresponding directly to the five tasks. */
public final class Experiments {
    private Experiments() { }

    public static void run(int task, TaskParameters p, boolean play) throws Exception {
        AudioFiles.AudioData audio = AudioFiles.read(Path.of("media/pops.wav"));
        RecordingExamples.requireRecording(audio);
        double[] samples = audio.samples();
        double[] original = samples.clone();
        int sampleRate = audio.sampleRate();
        Path output = Path.of("output", p.studentId());
        System.out.printf("Task %d: N=%d, fs=%d Hz, duration=%.5f s%n", task,
                samples.length, sampleRate, samples.length / (double)sampleRate);

        switch (task) {
            case 1 -> {
                double[] repaired = ClickFilter.simple(samples, p.edgeThreshold());
                System.out.println("Difference threshold=" + p.edgeThreshold());
                Diagnostics.differences(output.resolve("task1-differences.csv"), samples, repaired,
                        p.edgeThreshold(), sampleRate);
                render(output, "task1", "simple", samples, repaired, sampleRate, play);
            }
            case 2 -> {
                double[] repaired = ClickFilter.windowed(samples, p.largeLength(), p.smallLength(), p.energyThreshold());
                double[] scores = ClickFilter.windowScores(samples, p.largeLength(), p.smallLength());
                double[] thresholds = constantThresholds(scores.length, p.energyThreshold());
                List<ClickInterval> intervals = ClickFilter.detectIntervals(scores, samples.length,
                        p.largeLength(), p.smallLength(), p.energyThreshold());
                exportDetection(output, "task2", scores, thresholds, intervals, samples.length,
                        p.largeLength(), p.smallLength(), sampleRate, samples.length);
                checkSame(repaired, ClickFilter.interpolate(samples, intervals));
                render(output, "task2", "windowed", samples, repaired, sampleRate, play);
            }
            case 3 -> {
                long start = System.nanoTime();
                double[] direct = ClickFilter.windowScores(samples, p.largeLength(), p.smallLength());
                long middle = System.nanoTime();
                double[] rolling = ClickFilter.windowScoresRolling(samples, p.largeLength(), p.smallLength());
                long end = System.nanoTime();
                checkSame(rolling, direct);
                System.out.printf("Direct=%.2f ms, rolling=%.2f ms (timing is informational)%n",
                        (middle - start) / 1e6, (end - middle) / 1e6);
                List<ClickInterval> intervals = ClickFilter.detectIntervals(rolling, samples.length,
                        p.largeLength(), p.smallLength(), p.energyThreshold());
                double[] repaired = ClickFilter.interpolate(samples, intervals);
                checkSame(repaired, ClickFilter.windowed(samples, p.largeLength(), p.smallLength(), p.energyThreshold()));
                exportDetection(output, "task3", rolling, constantThresholds(rolling.length, p.energyThreshold()),
                        intervals, samples.length, p.largeLength(), p.smallLength(), sampleRate, samples.length);
                render(output, "task3", "rolling", samples, repaired, sampleRate, play);
            }
            case 4 -> {
                for (int small : new int[]{p.smallLength(), p.comparisonSmallLength()}) {
                    double[] scores = ClickFilter.windowScoresRolling(samples, p.largeLength(), small);
                    for (double factor : new double[]{0.5, 1, 1.5}) {
                        double threshold = p.energyThreshold() * factor;
                        List<ClickInterval> intervals = ClickFilter.detectIntervals(scores, samples.length,
                                p.largeLength(), small, threshold);
                        for (int width : new int[]{32, 64}) {
                            String name = "task4-S" + small + "-threshold" + factor + "-width" + width;
                            System.out.printf("%s: L=%d, S=%d, threshold=%.5f, maximum repair width=%d%n",
                                    name, p.largeLength(), small, threshold, width);
                            double[] repaired = ClickFilter.interpolateLimited(samples, intervals, width);
                            exportDetection(output, name, scores, constantThresholds(scores.length, threshold), intervals,
                                    samples.length, p.largeLength(), small, sampleRate, width);
                            render(output, name, "limited", samples, repaired, sampleRate, play);
                        }
                    }
                }
                compareSynthetic(p);
            }
            case 5 -> {
                double[] scores = ClickFilter.windowScoresRolling(samples, p.largeLength(), p.smallLength());
                int radius = 2048;
                double floor = p.energyThreshold() * 0.1;
                for (double k : new double[]{3, 6}) {
                    double[] thresholds = ClickFilter.adaptiveThresholds(scores, radius, k, floor);
                    List<ClickInterval> intervals = ClickFilter.detectIntervals(scores, samples.length,
                            p.largeLength(), p.smallLength(), thresholds);
                    double[] repaired = ClickFilter.interpolateLimited(samples, intervals, 64);
                    String name = "task5-k" + (int)k;
                    System.out.printf("Adaptive k=%.0f, radius=%d scores, threshold floor=%.5f, maximum width=64%n", k, radius, floor);
                    exportDetection(output, name, scores, thresholds, intervals, samples.length,
                            p.largeLength(), p.smallLength(), sampleRate, 64);
                    render(output, name, "adaptive", samples, repaired, sampleRate, play);
                }
                List<ClickInterval> fixed = ClickFilter.detectIntervals(scores, samples.length,
                        p.largeLength(), p.smallLength(), p.energyThreshold());
                double[] repaired = ClickFilter.interpolateLimited(samples, fixed, 64);
                exportDetection(output, "task5-fixed", scores, constantThresholds(scores.length, p.energyThreshold()), fixed,
                        samples.length, p.largeLength(), p.smallLength(), sampleRate, 64);
                render(output, "task5-fixed", "fixed", samples, repaired, sampleRate, play);
            }
            default -> throw new IllegalArgumentException("Task must be 1 through 5.");
        }
        if (!Arrays.equals(samples, original)) throw new IllegalArgumentException("Original input was modified.");
        System.out.println("Experiment completed. Inspect the CSVs and listen to the WAV comparisons.");
    }

    private static void exportDetection(Path output, String name, double[] scores, double[] thresholds,
            List<ClickInterval> intervals, int sampleCount, int large, int small, int sampleRate, int width) throws Exception {
        Diagnostics.intervals(output.resolve(name + "-intervals.csv"), intervals, sampleRate, sampleCount, width);
        Diagnostics.scores(output.resolve(name + "-scores.csv"), scores, large, small, sampleRate, thresholds);
    }

    private static double[] constantThresholds(int length, double threshold) {
        double[] values = new double[length];
        Arrays.fill(values, threshold);
        return values;
    }

    private static void checkSame(double[] actual, double[] expected) {
        if (actual == null || actual.length != expected.length) throw new IllegalArgumentException("Incorrect result length.");
        for (int n = 0; n < actual.length; n++) {
            if (!Double.isFinite(actual[n]) || Math.abs(actual[n] - expected[n]) > 1e-9)
                throw new IllegalArgumentException("Results differ at sample " + n);
        }
    }

    private static void render(Path output, String name, String label, double[] original,
            double[] repaired, int sampleRate, boolean play) throws Exception {
        Validation.samples(repaired);
        if (repaired == original || repaired.length != original.length)
            throw new IllegalArgumentException("Filter must return a new buffer of the same length.");
        double peak = 0;
        for (double[] buffer : new double[][]{original, repaired})
            for (double value : buffer) peak = Math.max(peak, Math.abs(value));
        double gain = peak > 0.8 ? 0.8 / peak : 1;
        double[] originalCopy = original.clone();
        double[] repairedCopy = repaired.clone();
        for (int n = 0; n < original.length; n++) {
            originalCopy[n] *= gain;
            repairedCopy[n] *= gain;
        }
        AudioFiles.write(output.resolve(name + "-original.wav"), originalCopy, sampleRate);
        Path path = output.resolve(name + "-" + label + ".wav");
        AudioFiles.write(path, repairedCopy, sampleRate);
        System.out.println("Filtered audio: " + path + "; common listening gain=" + gain);
        if (play) {
            AudioOutput.play(originalCopy, sampleRate);
            AudioOutput.play(repairedCopy, sampleRate);
        }
    }

    private static void compareSynthetic(TaskParameters p) {
        TestSignals.Example example = TestSignals.generate(p);
        double[] simple = ClickFilter.simple(example.corrupted(), p.edgeThreshold());
        double[] windowed = ClickFilter.windowed(example.corrupted(), 2048, 20, 0.2);
        List<ClickInterval> intervals = ClickFilter.detectIntervals(
                ClickFilter.windowScoresRolling(example.corrupted(), 2048, 20), example.clean().length, 2048, 20, 0.2);
        boolean[] truth = new boolean[example.clean().length];
        boolean[] detected = new boolean[truth.length];
        for (ClickInterval interval : example.clicks()) Arrays.fill(truth, interval.start(), interval.end() + 1, true);
        for (ClickInterval interval : intervals) Arrays.fill(detected, interval.start(), interval.end() + 1, true);
        int total = 0, covered = 0, falseSamples = 0;
        for (int n = 0; n < truth.length; n++) {
            if (truth[n]) { total++; if (detected[n]) covered++; }
            else if (detected[n]) falseSamples++;
        }
        System.out.printf("Synthetic only: damaged-sample coverage=%.2f%%, clean samples flagged=%d%n", 100.0 * covered / total, falseSamples);
        String[] labels = {"corrupted", "simple", "windowed"};
        double[][] versions = {example.corrupted(), simple, windowed};
        for (int i = 0; i < versions.length; i++) {
            double all = 0, damaged = 0;
            for (int n = 0; n < truth.length; n++) {
                double error = versions[i][n] - example.clean()[n];
                all += error * error;
                if (truth[n]) damaged += error * error;
            }
            System.out.printf("Synthetic %s: RMSE(all)=%.8f, RMSE(damaged)=%.8f%n",
                    labels[i], Math.sqrt(all / truth.length), Math.sqrt(damaged / total));
        }
    }
}
