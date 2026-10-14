package cbsp.lab03;
import java.util.Random;
/** Prepared infrastructure; keep version and random call order unchanged. */
public final class GenerateParameters {
    public static final String VERSION = "lab03-v1";
    private GenerateParameters() { }
    public static TaskParameters generate(String studentId) {
        if (studentId == null || !studentId.matches("[0-9]{8}"))
            throw new IllegalArgumentException("Student ID must contain exactly eight digits.");
        Random r = new Random(Long.parseLong(studentId));
        double edge = (40 + r.nextInt(21)) / 100.0;
        double energy = (20 + r.nextInt(5)) / 100.0;
        int comparison = new int[]{12, 16, 24, 28}[r.nextInt(4)];
        long seed = r.nextLong();
        double frequency = 180 + r.nextInt(81);
        return new TaskParameters(VERSION, studentId, 2048, 20, comparison, edge, energy, seed, frequency);
    }
}
