package com.sentisense;

import com.sentisense.engine.MlClassifier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MlClassifierTest {

    private static MlClassifier classifier;

    @BeforeAll
    static void train() {
        classifier = MlClassifier.trainFromBundledData();
    }

    @Test
    void heldOutAccuracyIsAcceptable() {
        MlClassifier.Metrics m = classifier.metrics();
        assertThat(m.testSize()).isGreaterThanOrEqualTo(250);
        assertThat(m.accuracy())
                .as("held-out accuracy (got %s)", m.accuracy())
                .isGreaterThanOrEqualTo(0.75);
        assertThat(m.f1()).isGreaterThanOrEqualTo(0.75);
    }

    @Test
    void predictsObviousPositive() {
        assertThat(classifier.predictPositiveProbability("Excellent product, works great and I love it"))
                .isGreaterThan(0.5);
    }

    @Test
    void predictsObviousNegative() {
        assertThat(classifier.predictPositiveProbability("Terrible quality, broke after one day, waste of money"))
                .isLessThan(0.5);
    }

    @Test
    void handlesNegatedPhrases() {
        assertThat(classifier.predictPositiveProbability("does not work"))
                .isLessThan(0.5);
    }

    @Test
    void coverageIsLowForUnseenVocabulary() {
        double coverage = classifier.featureCoverage("zxqwv blorptastic frimble");
        assertThat(coverage).isLessThan(0.5);
    }

    @Test
    void probabilityStaysInRange() {
        for (String text : new String[]{"", "ok", "great great great", "awful awful awful"}) {
            assertThat(classifier.predictPositiveProbability(text)).isBetween(0.0, 1.0);
        }
    }
}
