package cbsp.lab03;
import java.util.Locale;
import javax.sound.sampled.LineUnavailableException;
/** Prepared CLI: run from the project root. */
public final class Main {
    private Main() { }
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) { help(); return; }
        if (args.length != 2 || !(args[0].equals("--parameters") || args[0].matches("--(task|check)[1-5]"))) {
            help(); System.exit(2); return;
        }
        try {
            TaskParameters p = GenerateParameters.generate(args[1]);
            if (args[0].equals("--parameters")) {
                System.out.printf("Generator=%s, student=%s%nL=%d, S=%d, comparison S=%d%nDifference threshold=%.2f, energy threshold=%.2f%nSynthetic seed=%d, frequency=%.0f Hz%n",
                        p.version(), p.studentId(), p.largeLength(), p.smallLength(), p.comparisonSmallLength(),
                        p.edgeThreshold(), p.energyThreshold(), p.seed(), p.syntheticFrequency());
            } else Experiments.run(Integer.parseInt(args[0].substring(args[0].length()-1)), p, args[0].startsWith("--task"));
        } catch (LineUnavailableException e) {
            System.err.println("Audio device unavailable. Use --checkN for diagnostics and exported WAV files."); System.exit(2);
        } catch (Exception e) {
            System.err.println(e.getClass().getSimpleName() + ": " + e.getMessage()); System.exit(2);
        }
    }
    private static void help() {
        System.out.println("Usage: Main <command> <eight-digit-student-id>");
        System.out.println("--parameters    Print assigned settings");
        System.out.println("--check1..5     Numerical diagnostics, no audio device needed");
        System.out.println("--task1..5      Same experiment plus playback (start at low device volume)");
        System.out.println("All five tasks are required. Input: media/pops.wav. Output: output/<student-id>/.");
    }
}
