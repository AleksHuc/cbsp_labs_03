# Lab 03: Click filters

This project contains starter code for **five required tasks**.
First build a simple filter, then a window-based filter, and finally improve
its speed and detection. All tasks use `media/pops.wav`.

The algorithms are together in **`ClickFilter.java`**, ordered by task.
Implement the methods marked `TODO` according to the tasks below. Audio I/O,
parameters, validation helpers and experiments are prepared infrastructure.
The starter project compiles, but task commands report `UnsupportedOperationException`
until the methods they use are implemented. Parameter preview and help work immediately.

## Setup and running

Use JDK 21 or newer and Maven 3.9.x or newer. Open the directory containing
`pom.xml` as a Maven project. Initial Maven plugin downloads need internet access;
there are no external runtime libraries.

Run from the project root:

```bash
mvn clean compile
mvn -q compile exec:java -Dexec.args="--parameters 11111111"
mvn -q compile exec:java -Dexec.args="--check1 11111111"
mvn -q compile exec:java -Dexec.args="--task1 11111111"
```

Replace `11111111` with your eight-digit student ID, including leading zeroes.
`--checkN` runs the experiment and writes outputs without an audio device.
`--taskN` also plays the original followed by the filtered version. Start with
low device volume. N is **1 through 5**; all five tasks are required.
Each comparison plays the complete original recording followed by the complete
filtered recording, even when a width limit skips every candidate repair.

| Task | Comparisons | Full-recording playbacks with `--taskN` |
| --- | --- | --- |
| 1 | One simple-filter result | 2 |
| 2 | One direct window-filter result | 2 |
| 3 | One rolling window-filter result | 2 |
| 4 | 2 small-window lengths × 3 thresholds × 2 width limits | 24 |
| 5 | Adaptive k=3, adaptive k=6, fixed threshold | 6 |

Task 4 playback takes about 4 minutes 25 seconds, plus processing time;
Task 5 takes about 66 seconds, plus processing time. `--checkN` plays nothing.
The Task 4 synthetic comparison prints metrics without playback or audio export.

In IntelliJ, run `Main.main` as an **Application** configuration with
`--check2 11111111` in **Program arguments** and the project root as the
**Working directory**. For a Maven configuration, use
`compile exec:java -Dexec.args="--check2 11111111"` in its command field,
without the initial `mvn`.

Outputs are in `output/<student-id>/`. Repeating an experiment replaces files
with the same names. Other existing output files are retained; the filenames
below describe the current experiments.

The parameter generator assigns these settings reproducibly from your ID:

| Parameter | Assigned value or range | Used by |
| --- | --- | --- |
| `edgeThreshold` | 0.40–0.60, in steps of 0.01 | Task 1; simple filter in the Task 4 synthetic comparison |
| `energyThreshold` | 0.20–0.24, in steps of 0.01 | Tasks 2–5 recording experiments |
| `largeLength` | 2048 samples | Tasks 2–5 |
| `smallLength` | 20 samples | Tasks 2, 3 and 5; one Task 4 setting |
| `comparisonSmallLength` | 12, 16, 24 or 28 samples | The other Task 4 setting |
| `seed`, `syntheticFrequency` | Deterministic seed; frequency 180–260 Hz | Task 4 synthetic comparison |

Run `--parameters` to see your exact settings. Keep the generator unchanged.

## Task 1 — Simple filter in one pass

Implement **`ClickFilter.simple(samples, threshold)`**.
Visit the samples in order and compare each input sample with the previous
corrected output. Perform detection and correction together:

```text
result[0] = samples[0]
for n = 1,...,N-1:
    difference = abs(samples[n] - result[n-1])
    result[n] = difference > threshold ? 0 : samples[n]
```

Here “above threshold” means the difference between the **current input sample**
and the **previous corrected output sample**. Validate finite samples during the
same processing pass. Return a new buffer and leave the input unchanged; using
`result[n-1]` gives the same correction behavior as modifying the array in place.

Once a sample becomes zero, the next sample is compared with that zero. This can
propagate zero replacement through a multi-sample pulse until a sample is within
the threshold of zero. It can also suppress a legitimate loud passage following
a detected jump, and transitions to zero may introduce new artifacts.

Checks: `[0,0.2,0.8,0.8]`, threshold 0.5 → `[0,0.2,0,0]`;
`[0,1,1]`, threshold 0.5 → `[0,0,0]`;
`[0,0.8,0.8,0.8,0]`, threshold 0.226 → all zeros.
`[0,0.8,0.2,0.25]`, threshold 0.226 → `[0,0,0.2,0.25]`, showing recovery.
A difference equal to threshold is unflagged; a single sample is unchanged.
The runner uses your assigned `edgeThreshold`; the example threshold 0.226 is
illustrative and is not hard-coded into the filter. The difference CSV records
the previous corrected output, current input and actual decision at each sample.
Its rows start at index 1 because index 0 has no preceding sample. The reported
flagged-sample count is the number of threshold decisions to write zero.

Outputs: `task1-original.wav`, `task1-simple.wav`, `task1-differences.csv`.

Check without playback, then run with playback:

```bash
mvn -q compile exec:java -Dexec.args="--check1 11111111"
mvn -q compile exec:java -Dexec.args="--task1 11111111"
```

## Task 2 — Window detection and interpolation

Implement the following methods in `ClickFilter`:

- `windowScores`: directly calculate small- and large-window mean-square levels.
- `detectIntervals` with a fixed threshold, and its `mergeFlaggedWindows` helper:
  mark entire small windows, then merge their union into damaged regions.
- `interpolate`: bridge each region using its intact neighboring samples.
- `windowed`: connect those steps into a complete filter.

For a large window starting at n:

```text
m = n + (L-S)/2                         // integer division
E_large = sum(x[n+k]^2, k=0,...,L-1) / L
E_small = sum(x[m+k]^2, k=0,...,S-1) / S
score[n] = E_small - E_large
```

When `score[n] > threshold`, flag `[m,m+S-1]`. Detect on the original recording
before changing any samples. Merge overlapping and adjacent flagged windows to
avoid repeated repairs and anchors inside another flagged window.

For a resulting region `[a,b]`, interpolate between anchors `a-1` and `b+1`,
assumed intact because they are outside the detected region:

```text
fraction = (n-a+1) / (double)(b-a+2)
result[n] = (1-fraction)*samples[a-1] + fraction*samples[b+1]
```

For one flagged small window these are the samples immediately outside its edges.
For merged windows they are immediately outside the merged region. Leave both
anchors and every sample outside the region unchanged. Skip regions without both
anchors. Do not use corrupted endpoints as anchors.
The anchors are preserved exactly; their being unflagged does not guarantee
that the detector has correctly located the entire click. Task 2 has no maximum
repair-width limit; that additional restriction is introduced in Task 4.

Start with L=2048, S=20 and your assigned energy threshold. The detector receives
no manually annotated click positions. `RecordingExamples.CLICKS` is used only
for independent checks against selected obvious disturbances in the supplied WAV.

Checks: `[0,0,2,0,0]`, L=4, S=1 → scores `[-1,3]`; threshold 1 detects `[2,2]`
and repairs the signal to zero. `[0,9,9,3]` with interval `[1,2]` → `[0,1,2,3]`.
Also test overlapping windows, odd offsets, boundary regions and N<L.

Outputs: `task2-original.wav`, `task2-windowed.wav`, `task2-scores.csv`,
`task2-intervals.csv`. Compare its sound with Task 1.

Check without playback, then run with playback:

```bash
mvn -q compile exec:java -Dexec.args="--check2 11111111"
mvn -q compile exec:java -Dexec.args="--task2 11111111"
```

## Task 3 — Faster window calculations

Implement **`ClickFilter.windowScoresRolling`**.
Initialize both squared-sample sums once. When advancing one sample, subtract
the outgoing square and add the incoming square for each window. Divide by the
window lengths to obtain the same mean-square scores as Task 2.

This changes score computation from O(N*L) to O(N). Reuse the same interval
selection and interpolation. Do not call the direct method inside each rolling
window. The runner compares every score and the final repaired signal with Task 2,
then prints both calculation times. Small floating-point differences are allowed;
timing is informational, not a pass/fail speed threshold.

Outputs: `task3-original.wav`, `task3-rolling.wav`, `task3-scores.csv`,
`task3-intervals.csv`. The repaired sound should match Task 2.
The runner permits an absolute difference of `1e-9` when comparing scores and
repaired samples. It measures score-calculation time, excluding audio I/O,
interval detection and interpolation. Timing is a single measurement without
benchmark warm-up. Task 3 retains Task 2's unrestricted repair widths.

Check without playback, then run with playback:

```bash
mvn -q compile exec:java -Dexec.args="--check3 11111111"
mvn -q compile exec:java -Dexec.args="--task3 11111111"
```

## Task 4 — Tune detection and limit repair width

Implement **`ClickFilter.interpolateLimited(samples, intervals, maxRepairWidth)`**.
Reuse Task 2 interpolation, but skip complete merged regions wider than the
positive maximum width. Do not truncate a region and repair only part of it.
Regions equal to the limit are eligible; boundary regions still need both anchors.
Width is the inclusive length `end-start+1` of the **merged detected region**.
It can exceed the actual click duration because complete small windows are marked.
For example, a short pulse can produce a region wider than 32 samples and be skipped.

The experiment compares:

- Small-window lengths 20 and your assigned comparison length.
- Energy thresholds 0.5, 1 and 1.5 times your assigned threshold.
- Maximum repair widths 32 and 64 samples.

Inspect interval counts, repaired sample counts and skipped region counts, then
listen to each result. A low threshold can modify natural attacks. A small maximum
width can preserve useful sound while leaving real clicks unrepaired. A wide small
window can include too much intact audio. There is no universally best setting.

For example, the combination S=20, assigned threshold ×1.0 and width limit 64
produces these files:

```text
task4-S20-threshold1.0-width64-original.wav
task4-S20-threshold1.0-width64-limited.wav
task4-S20-threshold1.0-width64-intervals.csv
task4-S20-threshold1.0-width64-scores.csv
```

The threshold number in the filename is a **multiplier**, not the numerical
threshold. Interval CSVs include all detected regions and explicitly mark skipped
repairs. Reported repaired-sample counts cover eligible regions, including samples
whose interpolated value happens to equal the original.

A prepared synthetic example also compares corrupted audio, the simple filter and
the window filter. Only this example has a clean reference and known click mask,
so it reports the window detector's damaged-sample coverage and falsely marked
clean samples, plus RMSE for the corrupted, simple-filtered and window-filtered
signals over the full signal and over damaged samples. This separate example uses
one second of synthetic audio, L=2048, S=20 and a fixed energy threshold of 0.20;
it applies no width limit. Its simple filter uses the assigned difference threshold.
These quality metrics are not claimed for `pops.wav`.

Check without playback, then run with playback:

```bash
mvn -q compile exec:java -Dexec.args="--check4 11111111"
mvn -q compile exec:java -Dexec.args="--task4 11111111"
```

## Task 5 — Adaptive threshold

Implement **`ClickFilter.adaptiveThresholds(scores, radius, k, minimumThreshold)`**.
Estimate the score mean and **population** standard deviation in a local context
around each score position:

```text
threshold[n] = max(minimumThreshold, localMean[n] + k*localStd[n])
```

Use score positions from `n-radius` through `n+radius`, inclusive, clipped at the
array boundaries. Include the current score. Efficient prefix sums give local
sums and squared sums without repeatedly scanning the context. Clamp small
negative variance caused by roundoff to zero. Require radius>0, finite k>=0 and
a finite positive floor. Empty scores produce empty thresholds.
Use population variance `mean(score²)-mean(score)²`. The implementation first
scales scores by their maximum absolute value to avoid overflow in the prefix
sums, then restores the original score scale when calculating thresholds.
All-zero scores produce the minimum threshold at every position. Reject
non-finite scores or a non-finite calculated threshold.

The runner uses radius=2048 score positions, k=3 and 6, a floor of 0.1 times the
assigned energy threshold, and maximum repair width 64. The prepared varying-
threshold overload of `detectIntervals` applies the threshold at each position.
A fixed-threshold comparison uses the same width limit for fairness.

Unlike estimating one threshold from one short segment, this threshold changes
throughout the recording. Local clicks can influence their own statistics;
changes in level and legitimate transients can still cause missed or false
candidate regions. Compare the threshold curves in the CSVs and listen.

The radius is measured in **score positions**, not in large windows of 2048
samples each. Because the large window advances one audio sample at a time,
an interior context contains 4097 scores, spanning about 92.9 ms at 44,100 Hz.

Outputs:

- `task5-k3-original.wav` and `task5-k3-adaptive.wav`.
- `task5-k6-original.wav` and `task5-k6-adaptive.wav`.
- `task5-fixed-original.wav` and `task5-fixed-fixed.wav`.
- Matching `*-scores.csv` and `*-intervals.csv` for each comparison.

Check without playback, then run with playback:

```bash
mvn -q compile exec:java -Dexec.args="--check5 11111111"
mvn -q compile exec:java -Dexec.args="--task5 11111111"
```

## Conventions and architecture

- The input is signed 16-bit mono PCM at 44,100 Hz, 486,912 samples, about 11.04 s.
  `AudioFiles` divides PCM integers by 32768; it does not normalize or resample.
  Run the experiments with the supplied `media/pops.wav`; the runner checks
  its sample rate and sample count before processing.
- Sample time is `n/(double)sampleRate`. Signals must be non-null, non-empty and
  finite. Every filter returns a distinct same-length buffer without modifying input.
- Invalid arguments throw `IllegalArgumentException`. Energy calculations reject
  overflow in the sum of all squared samples. `Validation` supplies reusable guards.
- Require integer `1<=S<L`; evaluate only full large windows. Score arrays have
  `N-L+1` elements, or zero elements if N<L. The window filters return unchanged
  copies when N<L because no full window can be evaluated. Task 1 still processes
  short signals; it preserves the first sample.
- Threshold comparisons are strict. Difference thresholds and energy thresholds
  have different units and are assigned separately.
- `ClickInterval(a,b)` includes both ends. Lists are sorted, bounded, disjoint and
  non-adjacent. Empty lists are valid. Invalid interval lists are rejected in full.
- Raw input is used for detection and measurements. Each original/repaired WAV
  pair receives one common attenuation factor to peak <=0.8, without amplification.
  WAV exports are listening copies with ordinary 16-bit quantization; CSVs are raw.
- Window regions at either buffer boundary remain unchanged without two anchors.
  Only full windows are scanned, so recording ends have less detection coverage.
- The parameter generator retains reproducible settings from the eight-digit ID.

The class responsibilities are:

| File | Responsibility |
| --- | --- |
| `ClickFilter.java` | Student methods for all five tasks, marked TODO |
| `Experiments.java` | Five task runners, comparisons and playback/export |
| `Main.java` | Command-line parsing |
| `AudioFiles.java`, `AudioOutput.java` | Prepared WAV I/O and playback |
| `GenerateParameters.java`, `TaskParameters.java` | Reproducible assignment settings |
| `ClickInterval.java` | Inclusive interval data |
| `Validation.java`, `Diagnostics.java` | Prepared input guards and CSV exports |
| `RecordingExamples.java`, `TestSignals.java` | Diagnostic recording labels and synthetic checks |

Tasks 1 and 2 can be developed independently. Task 3 reuses Task 2 detection
and interpolation; Task 4 reuses those steps with rolling scores and a width limit;
Task 5 reuses Tasks 2–4 with a threshold array. The runners do not read another
task's output files, so no previous command needs to have been run.

Score CSVs contain the large-window start, small-window start, small-window center
time, detection score and applied threshold. Interval CSV sample endpoints are
inclusive; `end_exclusive_seconds` describes the time immediately after the final
sample. A detected region remains listed even when its repair is skipped.

## Verification

After implementing all five tasks, run the checks without an audio device:

```bash
mvn test-compile
java -cp target/classes:target/test-classes cbsp.lab03.AlgorithmChecks
```

On Windows, separate classpath entries with `;` instead of `:`. These are explicit
executable checks; `mvn test` does not automatically run them. They cover single-
pass behavior, direct/rolling agreement, merged windows and interpolation,
maximum widths, adaptive threshold examples, invalid inputs, actual recording
settings, synthetic improvement and WAV roundtrip. Inspect waveforms and listen
as well: numerical correctness does not guarantee perceptual repair quality.
The `--checkN` commands run their task experiments and basic diagnostics;
they do not execute the complete `AlgorithmChecks` suite.

## Submission

Submit `pom.xml`, `README.md` and `src/` in one ZIP. Keep the provided public
signatures and parameter generator unchanged. Exclude `target/`, `output/` and
IDE settings. The supplied `media/pops.wav` is the input recording and does not
need to be resubmitted. All five tasks must compile and run with the commands
above; also check valid and invalid inputs beyond your assigned parameters.
