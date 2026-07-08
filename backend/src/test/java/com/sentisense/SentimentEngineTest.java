package com.sentisense;

import com.sentisense.engine.SentimentEngine;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SentimentEngineTest {

    private static SentimentEngine engine;

    @BeforeAll
    static void setUp() {
        engine = new SentimentEngine();
    }

    @Test
    void labelsPositiveConsumerFeedback() {
        SentimentEngine.Analysis a = engine.analyze("Absolutely love this phone, best purchase this year!");
        assertThat(a.label()).isEqualTo(SentimentEngine.Label.POSITIVE);
        assertThat(a.mindState()).isIn(SentimentEngine.MindState.DELIGHTED, SentimentEngine.MindState.SATISFIED);
        assertThat(a.purchaseIntent()).isGreaterThan(50);
    }

    @Test
    void labelsNegativeConsumerFeedback() {
        SentimentEngine.Analysis a = engine.analyze("Horrible experience, the product broke and support ignored me");
        assertThat(a.label()).isEqualTo(SentimentEngine.Label.NEGATIVE);
        assertThat(a.mindState()).isIn(SentimentEngine.MindState.DISSATISFIED, SentimentEngine.MindState.FRUSTRATED);
        assertThat(a.purchaseIntent()).isLessThan(50);
    }

    @Test
    void labelsNeutralFactualText() {
        SentimentEngine.Analysis a = engine.analyze("The parcel was delivered to the front desk on Monday");
        assertThat(a.label()).isEqualTo(SentimentEngine.Label.NEUTRAL);
        assertThat(a.mindState()).isEqualTo(SentimentEngine.MindState.UNDECIDED);
    }

    @Test
    void confidenceAndScoresStayInRange() {
        for (String text : new String[]{
                "I love it", "I hate it", "it exists", "not bad at all",
                "AMAZING deal!!!", "worst purchase ever :("}) {
            SentimentEngine.Analysis a = engine.analyze(text);
            assertThat(a.score()).isBetween(-1.0, 1.0);
            assertThat(a.confidence()).isBetween(0.0, 1.0);
            assertThat(a.purchaseIntent()).isBetween(0, 100);
            assertThat(a.mlProbability()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void mindStateMeaningIsPopulated() {
        assertThat(engine.analyze("great").mindStateMeaning()).isNotBlank();
    }

    @Test
    void exposesModelMetrics() {
        assertThat(engine.modelMetrics().accuracy()).isGreaterThan(0.7);
    }
}
