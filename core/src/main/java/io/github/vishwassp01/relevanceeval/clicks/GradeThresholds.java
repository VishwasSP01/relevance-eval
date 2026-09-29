package io.github.vishwassp01.relevanceeval.clicks;

/**
 * Encapsulates the threshold boundaries used to map corrected click-through rates into relevance grades 0 to 3.
 *
 * @param grade3 minimum corrected rate required for grade 3 (default: 0.50)
 * @param grade2 minimum corrected rate required for grade 2 (default: 0.25)
 * @param grade1 minimum corrected rate required for grade 1 (default: 0.10)
 */
public record GradeThresholds(double grade3, double grade2, double grade1) {

    public static final GradeThresholds DEFAULT = new GradeThresholds(0.50, 0.25, 0.10);

    public GradeThresholds {
        if (Double.isNaN(grade3) || Double.isNaN(grade2) || Double.isNaN(grade1)) {
            throw new IllegalArgumentException("Thresholds must not be NaN");
        }
        if (grade3 <= grade2 || grade2 <= grade1 || grade1 < 0.0 || grade3 > 1.0) {
            throw new IllegalArgumentException(
                    "Thresholds must satisfy 1.0 >= grade3 > grade2 > grade1 >= 0.0, but received: grade3="
                            + grade3 + ", grade2=" + grade2 + ", grade1=" + grade1
            );
        }
    }

    /**
     * Maps a corrected click rate to a relevance grade between 0 and 3.
     *
     * @param rate the estimated or corrected click-through rate
     * @return 3 if rate &gt;= grade3, 2 if rate &gt;= grade2, 1 if rate &gt;= grade1, otherwise 0
     */
    public int toGrade(double rate) {
        if (rate >= grade3) {
            return 3;
        } else if (rate >= grade2) {
            return 2;
        } else if (rate >= grade1) {
            return 1;
        } else {
            return 0;
        }
    }
}
