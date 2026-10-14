package cbsp.lab03;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Prepared CSV exports using unattenuated input and filter output samples. */
public final class Diagnostics {
    private Diagnostics() { }

    public static void intervals(Path path, List<ClickInterval> intervals, int sampleRate,
            int sampleCount, int maxRepairWidth) throws IOException {
        int repaired = 0, skipped = 0;
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("start,end,start_seconds,end_exclusive_seconds,samples,repair_skipped\n");
            for (int n = 0; n < intervals.size(); n++) {
                ClickInterval interval = intervals.get(n);
                boolean skip = interval.start() == 0 || interval.end() == sampleCount - 1
                        || interval.length() > maxRepairWidth;
                if (skip) skipped++; else repaired += interval.length();
                String row = String.format(Locale.ROOT, "%d,%d,%.9f,%.9f,%d,%s%n", interval.start(), interval.end(),
                        interval.start() / (double)sampleRate, (interval.end() + 1) / (double)sampleRate,
                        interval.length(), skip);
                writer.write(row);
                if (n < 12) System.out.print(row);
            }
        }
        System.out.printf("Detected intervals=%d, repaired samples=%d, skipped intervals=%d%n", intervals.size(), repaired, skipped);
    }

    public static void scores(Path path, double[] scores, int large, int small, int sampleRate,
            double[] thresholds) throws IOException {
        Files.createDirectories(path.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("large_start,small_start,small_center_seconds,score,threshold\n");
            int offset = (large - small) / 2;
            for (int n = 0; n < scores.length; n++) {
                writer.write(n + "," + (n + offset) + "," + ((n + offset + (small - 1) / 2.0) / sampleRate)
                        + "," + scores[n] + "," + thresholds[n] + "\n");
            }
        }
    }

    public static void differences(Path path, double[] samples, double[] filtered,
            double threshold, int sampleRate) throws IOException {
        Files.createDirectories(path.getParent());
        int count = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("index,seconds,previous_output,current_input,difference,flagged\n");
            for (int n = 1; n < samples.length; n++) {
                double difference = Math.abs(samples[n] - filtered[n - 1]);
                boolean flagged = difference > threshold;
                if (flagged) count++;
                writer.write(n + "," + n / (double)sampleRate + "," + filtered[n - 1] + "," + samples[n]
                        + "," + difference + "," + flagged + "\n");
            }
        }
        System.out.println("Flagged output samples=" + count);
    }
}
