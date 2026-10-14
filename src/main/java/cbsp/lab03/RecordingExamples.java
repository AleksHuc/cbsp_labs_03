package cbsp.lab03;
import java.util.List;
/** Prepared waveform-inspected teaching intervals, not exhaustive ground truth. */
public final class RecordingExamples {
    public static final int SAMPLE_RATE = 44_100;
    public static final int FRAME_COUNT = 486_912;
    // First disturbed sample through last disturbed sample; both included.
    // Chosen from abrupt positive/negative pulses and recovery to surrounding waveform.
    public static final List<ClickInterval> CLICKS = List.of(
        new ClickInterval(58082, 58094), new ClickInterval(98018, 98034),
        new ClickInterval(158434, 158446), new ClickInterval(191714, 191726),
        new ClickInterval(260322, 260334), new ClickInterval(297186, 297198),
        new ClickInterval(338658, 338670), new ClickInterval(371938, 371950),
        new ClickInterval(401634, 401646), new ClickInterval(446690, 446702));
    private RecordingExamples() { }
    public static void requireRecording(AudioFiles.AudioData data) {
        if (data.sampleRate() != SAMPLE_RATE || data.samples().length != FRAME_COUNT)
            throw new IllegalArgumentException("Teaching intervals require the supplied original media/pops.wav.");
    }
}
