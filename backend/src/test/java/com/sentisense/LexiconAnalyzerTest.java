package com.sentisense;

import com.sentisense.engine.LexiconAnalyzer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LexiconAnalyzerTest {

    private static final LexiconAnalyzer analyzer = new LexiconAnalyzer();

    @Test
    void positiveTextScoresPositive() {
        assertThat(analyzer.analyze("This product is great and I love it").compound()).isPositive();
    }

    @Test
    void negativeTextScoresNegative() {
        assertThat(analyzer.analyze("This is terrible, I hate it").compound()).isNegative();
    }

    @Test
    void neutralTextScoresNearZero() {
        assertThat(analyzer.analyze("The package arrived on Tuesday").compound())
                .isBetween(-0.1, 0.1);
    }

    @Test
    void negationFlipsPolarity() {
        double plain = analyzer.analyze("The camera is good").compound();
        double negated = analyzer.analyze("The camera is not good").compound();
        assertThat(plain).isPositive();
        assertThat(negated).isNegative();
    }

    @Test
    void boosterIntensifiesSentiment() {
        double plain = analyzer.analyze("The service was good").compound();
        double boosted = analyzer.analyze("The service was extremely good").compound();
        assertThat(boosted).isGreaterThan(plain);
    }

    @Test
    void exclamationAmplifiesSentiment() {
        double plain = analyzer.analyze("I love this phone").compound();
        double excited = analyzer.analyze("I love this phone!!!").compound();
        assertThat(excited).isGreaterThan(plain);
    }

    @Test
    void allCapsAddsEmphasis() {
        double plain = analyzer.analyze("this deal is great, thanks").compound();
        double shouted = analyzer.analyze("this deal is GREAT, thanks").compound();
        assertThat(shouted).isGreaterThan(plain);
    }

    @Test
    void clauseAfterButDominates() {
        double result = analyzer.analyze("The design is nice but the battery is awful").compound();
        assertThat(result).isNegative();
    }

    @Test
    void emoticonsCarrySentiment() {
        assertThat(analyzer.analyze("just got my order :)").compound()).isPositive();
        assertThat(analyzer.analyze("just got my order :(").compound()).isNegative();
    }

    @Test
    void reportsContributingKeywords() {
        LexiconAnalyzer.Result result = analyzer.analyze("Amazing quality but horrible delivery");
        assertThat(result.contributions())
                .extracting(LexiconAnalyzer.TokenScore::token)
                .contains("Amazing", "horrible");
    }

    @Test
    void compoundStaysWithinBounds() {
        String extreme = "amazing awesome fantastic wonderful perfect love love love!!!!";
        assertThat(analyzer.analyze(extreme).compound()).isBetween(-1.0, 1.0);
    }
}
