package cbsp.lab03;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Checks for completed student implementations of the five tasks. No audio device or external test library needed. */
public final class AlgorithmChecks {
    private static int checks;
    private AlgorithmChecks() { }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void close(double actual, double expected) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                "Expected " + expected + ", got " + actual);
    }
    private static void same(double[] actual, double[] expected) {
        require(actual != null && actual.length == expected.length, "Length mismatch");
        for (int n = 0; n < actual.length; n++) close(actual[n], expected[n]);
    }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    public static void main(String[] args) throws Exception {
        // Task 1: previous corrected output, strict threshold and propagation through a pulse.
        same(ClickFilter.simple(new double[]{0, .2, .8, .8}, .5), new double[]{0, .2, 0, 0});
        same(ClickFilter.simple(new double[]{0, .5}, .5), new double[]{0, .5});
        same(ClickFilter.simple(new double[]{0, 1, 1}, .5), new double[]{0, 0, 0});
        same(ClickFilter.simple(new double[]{0, .8, .8, .8, 0}, .226), new double[5]);
        same(ClickFilter.simple(new double[]{0, .8, .2, .25}, .226), new double[]{0, 0, .2, .25});
        same(ClickFilter.simple(new double[]{0, 1, 0}, .5), new double[]{0, 0, 0});
        same(ClickFilter.simple(new double[]{.7}, .5), new double[]{.7});
        double[] input = {0, 1, -.8, .2};
        double[] saved = input.clone();
        require(ClickFilter.simple(input, .5) != input, "Fresh simple output");
        require(Arrays.equals(input, saved), "Simple input unchanged");

        // Tasks 2 and 3: direct/rolling agreement, all window alignments and input immutability.
        same(ClickFilter.windowScores(new double[]{0,0,2,0,0}, 4, 1), new double[]{-1,3});
        same(ClickFilter.windowed(new double[]{0,0,2,0,0}, 4, 1, 1), new double[5]);
        same(ClickFilter.windowed(new double[]{2}, 4, 1, 1), new double[]{2});
        Random random = new Random(42);
        for (int length : new int[]{1,2,5,30,120}) {
            double[] samples = new double[length];
            for (int n = 0; n < length; n++) samples[n] = random.nextDouble() * 2 - 1;
            double[] original = samples.clone();
            for (int large : new int[]{2,3,4,9,31,121}) {
                for (int small : new int[]{1,2,3,8,20}) {
                    if (small >= large) continue;
                    double[] expected = ClickFilter.windowScores(samples, large, small);
                    same(ClickFilter.windowScoresRolling(samples, large, small), expected);
                    List<ClickInterval> found = ClickFilter.detectIntervals(expected, length, large, small, .05);
                    boolean[] mask = new boolean[length];
                    int offset = (large - small) / 2;
                    for (int n = 0; n < expected.length; n++) if (expected[n] > .05)
                        Arrays.fill(mask, n + offset, n + offset + small, true);
                    boolean[] actual = new boolean[length];
                    for (ClickInterval interval : found) Arrays.fill(actual, interval.start(), interval.end() + 1, true);
                    require(Arrays.equals(actual, mask), "Flag entire small windows");
                    Validation.intervals(samples, found);
                    double[] repaired = ClickFilter.windowed(samples, large, small, .05);
                    same(repaired, ClickFilter.interpolate(samples, found));
                    unchangedOutside(samples, repaired, found, Integer.MAX_VALUE);
                }
            }
            require(Arrays.equals(samples, original), "Window input unchanged");
        }
        require(ClickFilter.detectIntervals(new double[]{1}, 4, 4, 1, 1).isEmpty(), "Strict energy threshold");
        require(ClickFilter.detectIntervals(new double[]{2,2}, 5, 4, 2, 1)
                .equals(List.of(new ClickInterval(1,3))), "Overlapping windows merged");
        require(ClickFilter.detectIntervals(new double[]{2,2}, 3, 2, 1, 1)
                .equals(List.of(new ClickInterval(0,1))), "Adjacent windows merged");
        same(ClickFilter.interpolate(new double[]{0,9,9,3}, List.of(new ClickInterval(1,2))), new double[]{0,1,2,3});
        same(ClickFilter.interpolate(new double[]{0,9,2}, List.of(new ClickInterval(1,1))), new double[]{0,1,2});
        same(ClickFilter.interpolate(new double[]{9,2,9}, List.of(new ClickInterval(0,0), new ClickInterval(2,2))), new double[]{9,2,9});
        require(ClickFilter.interpolate(input, List.of()) != input, "Empty intervals return a copy");

        // Task 4: width is inclusive; long intervals are skipped, not partially repaired.
        double[] damaged = {0,9,9,3,9,5};
        List<ClickInterval> intervals = List.of(new ClickInterval(1,2), new ClickInterval(4,4));
        same(ClickFilter.interpolateLimited(damaged, intervals, 1), new double[]{0,9,9,3,4,5});
        same(ClickFilter.interpolateLimited(damaged, intervals, 2), new double[]{0,1,2,3,4,5});
        invalid(() -> ClickFilter.interpolateLimited(damaged, intervals, 0));

        // Task 5: known local statistics, clipped end contexts and threshold floors.
        double[] scores = {-.1,.2,.4,0,1.2,.3,.02};
        double[] originalScores = scores.clone();
        same(ClickFilter.adaptiveThresholds(new double[]{0,2,0}, 1, 0, .01),
                new double[]{1, 2.0/3, 1});
        same(ClickFilter.adaptiveThresholds(new double[]{0,2,0}, 1, 1, .01),
                new double[]{2, (2 + Math.sqrt(8))/3, 2});
        for (int radius : new int[]{20,Integer.MAX_VALUE}) {
            same(ClickFilter.adaptiveThresholds(new double[]{0,2,0}, radius, 0, .01),
                    new double[]{2.0/3, 2.0/3, 2.0/3});
        }
        double[] thresholds = ClickFilter.adaptiveThresholds(scores, 2, 3, .01);
        require(thresholds.length == scores.length, "One threshold per score");
        for (double threshold : thresholds)
            require(Double.isFinite(threshold) && threshold >= .01, "Finite threshold above floor");
        require(Arrays.equals(scores, originalScores), "Adaptive statistics leave scores unchanged");
        same(ClickFilter.adaptiveThresholds(new double[]{0,0}, 2, 3, .02), new double[]{.02,.02});
        same(ClickFilter.adaptiveThresholds(new double[]{.5,.5}, 1, 3, .02), new double[]{.5,.5});
        same(ClickFilter.adaptiveThresholds(new double[0], 2, 3, .02), new double[0]);
        require(ClickFilter.detectIntervals(new double[]{2,2}, 5, 4, 1, new double[]{3,1})
                .equals(List.of(new ClickInterval(2,2))), "Threshold varies with position");
        double[] changingLevel = {.1,.1,.1,.1,.8,.8,.8,.8};
        double[] adaptive = ClickFilter.adaptiveThresholds(changingLevel, 1, 0, .01);
        require(adaptive[6] > adaptive[1], "Adaptive threshold follows changing local level");

        // Shared validation policies.
        for (double[] bad : new double[][]{null, new double[0], {Double.NaN}, {Double.POSITIVE_INFINITY}, {0, Double.NaN}}) {
            invalid(() -> ClickFilter.simple(bad, .5));
            invalid(() -> ClickFilter.windowed(bad, 4, 1, .2));
            invalid(() -> ClickFilter.windowScoresRolling(bad, 4, 1));
            invalid(() -> ClickFilter.interpolate(bad, List.of()));
        }
        for (double threshold : new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            invalid(() -> ClickFilter.simple(input, threshold));
            invalid(() -> ClickFilter.windowed(input, 4, 1, threshold));
        }
        for (int[] windows : new int[][]{{0,1},{1,1},{2,0},{2,3}}) {
            invalid(() -> ClickFilter.windowScores(input, windows[0], windows[1]));
            invalid(() -> ClickFilter.windowScoresRolling(input, windows[0], windows[1]));
        }
        for (List<ClickInterval> bad : Arrays.asList(null, Arrays.asList((ClickInterval)null),
                List.of(new ClickInterval(0,4)), List.of(new ClickInterval(2,2), new ClickInterval(1,1)),
                List.of(new ClickInterval(1,2), new ClickInterval(3,3)))) {
            invalid(() -> ClickFilter.interpolate(input, bad));
            invalid(() -> ClickFilter.interpolateLimited(input, bad, 1));
        }
        invalid(() -> ClickFilter.windowScores(new double[]{Double.MAX_VALUE}, 4, 1));
        invalid(() -> ClickFilter.windowScoresRolling(new double[]{1e154,1e154}, 2, 1));
        invalid(() -> ClickFilter.detectIntervals(new double[]{0}, 5, 4, 1, .2));
        invalid(() -> ClickFilter.detectIntervals(new double[]{Double.NaN}, 4, 4, 1, .2));
        invalid(() -> ClickFilter.detectIntervals(new double[]{1}, 4, 4, 1, new double[0]));
        invalid(() -> ClickFilter.detectIntervals(new double[]{1}, 4, 4, 1, new double[]{0}));
        invalid(() -> ClickFilter.adaptiveThresholds(null, 2, 3, .01));
        invalid(() -> ClickFilter.adaptiveThresholds(scores, 0, 3, .01));
        invalid(() -> ClickFilter.adaptiveThresholds(scores, 2, -1, .01));
        invalid(() -> ClickFilter.adaptiveThresholds(scores, 2, Double.NaN, .01));
        invalid(() -> ClickFilter.adaptiveThresholds(scores, 2, 3, 0));
        invalid(() -> ClickFilter.adaptiveThresholds(new double[]{Double.NaN}, 2, 3, .01));
        require(Arrays.equals(input, saved), "Invalid filters leave input unchanged");
        for (String id : new String[]{"", "123", "123456789", "abcd1234"}) invalid(() -> GenerateParameters.generate(id));
        require(GenerateParameters.generate("00000001").equals(GenerateParameters.generate("00000001")), "Reproducible leading-zero ID");

        // Actual recording: all assigned base settings cover the ten diagnostic examples.
        AudioFiles.AudioData recording = AudioFiles.read(Path.of("media/pops.wav"));
        RecordingExamples.requireRecording(recording);
        double[] samples = recording.samples();
        double[] savedRecording = samples.clone();
        for (int small : new int[]{12,16,20,24,28}) {
            double[] rolling = ClickFilter.windowScoresRolling(samples, 2048, small);
            for (double threshold : new double[]{.20,.21,.22,.23,.24}) {
                List<ClickInterval> found = ClickFilter.detectIntervals(rolling, samples.length, 2048, small, threshold);
                for (ClickInterval known : RecordingExamples.CLICKS)
                    require(found.stream().anyMatch(i -> i.start() <= known.start() && i.end() >= known.end()), "Recording pulse coverage");
                double[] repaired = ClickFilter.interpolateLimited(samples, found, 64);
                unchangedOutside(samples, repaired, found, 64);
            }
        }
        same(ClickFilter.windowScoresRolling(samples,2048,20), ClickFilter.windowScores(samples,2048,20));
        for (String id : new String[]{"00000000","11111111","99999999"}) {
            TestSignals.Example example = TestSignals.generate(GenerateParameters.generate(id));
            double[] repaired = ClickFilter.windowed(example.corrupted(),2048,20,.2);
            require(error(example.clean(), repaired) < error(example.clean(), example.corrupted()), "Synthetic interpolation improvement");
        }
        require(Arrays.equals(samples,savedRecording), "Recording unchanged");
        Path path = Files.createTempFile("lab03-roundtrip-", ".wav");
        try {
            double[] pcm = {-1,-.5,0,.5,32767/32768.0};
            AudioFiles.write(path,pcm,44100);
            AudioFiles.AudioData loaded = AudioFiles.read(path);
            same(loaded.samples(),pcm);
            require(loaded.sampleRate() == 44100, "WAV sample rate");
        } finally { Files.deleteIfExists(path); }
        System.out.println("Verification passed: " + checks + " assertions across all five tasks, invalid inputs, recording settings and WAV I/O.");
    }

    private static void unchangedOutside(double[] original, double[] result, List<ClickInterval> intervals, int width) {
        require(result != original && result.length == original.length, "Fresh repair of same length");
        boolean[] repaired = new boolean[original.length];
        for (ClickInterval i : intervals) if (i.start() > 0 && i.end() < original.length-1 && i.length() <= width)
            Arrays.fill(repaired,i.start(),i.end()+1,true);
        for (int n = 0; n < original.length; n++) {
            if (!Double.isFinite(result[n]) || (!repaired[n] && result[n] != original[n]))
                throw new AssertionError("Changed sample outside eligible repair at " + n);
        }
        checks++;
    }
    private static double error(double[] clean, double[] samples) {
        double sum = 0;
        for (int n = 0; n < clean.length; n++) sum += Math.pow(samples[n]-clean[n],2);
        return sum;
    }
}
