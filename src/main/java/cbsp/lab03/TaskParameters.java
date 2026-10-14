package cbsp.lab03;
/** Prepared immutable assignment parameters. */
public record TaskParameters(String version, String studentId, int largeLength,
        int smallLength, int comparisonSmallLength, double edgeThreshold,
        double energyThreshold, long seed, double syntheticFrequency) { }
