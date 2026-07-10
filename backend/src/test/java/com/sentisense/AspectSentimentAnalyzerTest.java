package com.sentisense;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sentisense.engine.AspectSentimentAnalyzer;
import com.sentisense.engine.AspectSentimentAnalyzer.AspectSentiment;
import com.sentisense.engine.LexiconAnalyzer;
import com.sentisense.engine.SentimentEngine.Label;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AspectSentimentAnalyzerTest {

    private static AspectSentimentAnalyzer analyzer;

    @BeforeAll
    static void setUp() {
        analyzer = new AspectSentimentAnalyzer(new LexiconAnalyzer());
    }

    private static Map<String, AspectSentiment> byAspect(String text) {
        return analyzer.analyze(text).stream()
                .collect(Collectors.toMap(AspectSentiment::aspect, a -> a));
    }

    @Test
    @DisplayName("contrastive review yields opposite polarity per aspect")
    void contrastiveAspects() {
        Map<String, AspectSentiment> aspects =
                byAspect("The camera is amazing but the battery is terrible.");
        assertThat(aspects.get("camera").label()).isEqualTo(Label.POSITIVE);
        assertThat(aspects.get("battery").label()).isEqualTo(Label.NEGATIVE);
    }

    @Test
    @DisplayName("fixes the documented negated-neutral-verb failure at aspect level")
    void negatedNeutralVerb() {
        // Whole-text scoring misreads this as positive ("support" has positive
        // valence in the lexicon). Aspect scoring excludes the aspect term and
        // applies the unmatched-negation prior.
        Map<String, AspectSentiment> aspects =
                byAspect("Customer support never replied to my emails!!!");
        assertThat(aspects).containsKey("service");
        assertThat(aspects.get("service").label()).isEqualTo(Label.NEGATIVE);
    }

    @Test
    @DisplayName("comma-separated opinions attach to their own aspects")
    void commaSeparatedAspects() {
        Map<String, AspectSentiment> aspects =
                byAspect("Gorgeous screen, horrible battery life.");
        assertThat(aspects.get("display").label()).isEqualTo(Label.POSITIVE);
        assertThat(aspects.get("battery").label()).isEqualTo(Label.NEGATIVE);
    }

    @Test
    @DisplayName("aspect-less trailing clause is merged into the aspect clause")
    void trailingClauseMerged() {
        // "dies quickly" has no aspect term; its sentiment must still reach "battery"
        Map<String, AspectSentiment> aspects =
                byAspect("The battery is nice but dies within an hour, sadly.");
        assertThat(aspects).containsKey("battery");
        assertThat(aspects.get("battery").score()).isLessThan(0.4); // "but"-clause dampens the praise
    }

    @Test
    @DisplayName("text without known aspects yields an empty list")
    void noAspects() {
        assertThat(analyzer.analyze("I am absolutely thrilled about everything!")).isEmpty();
        assertThat(analyzer.analyze("")).isEmpty();
        assertThat(analyzer.analyze(null)).isEmpty();
    }

    @Test
    @DisplayName("multiple mentions of one aspect aggregate into a single entry")
    void mentionsAggregate() {
        List<AspectSentiment> aspects = analyzer.analyze(
                "The food was delicious. Great food, honestly the best meal in years.");
        assertThat(aspects).hasSize(1);
        AspectSentiment food = aspects.get(0);
        assertThat(food.aspect()).isEqualTo("food");
        assertThat(food.label()).isEqualTo(Label.POSITIVE);
        assertThat(food.mentions()).contains("food", "meal");
        assertThat(food.evidence()).isNotEmpty();
    }

    @Test
    @DisplayName("plural aspect terms resolve to the singular taxonomy entry")
    void pluralStripping() {
        Map<String, AspectSentiment> aspects = byAspect("The speakers sound fantastic.");
        assertThat(aspects).containsKey("audio");
        assertThat(aspects.get("audio").label()).isEqualTo(Label.POSITIVE);
    }

    @Test
    @DisplayName("restaurant domain: food vs service split")
    void restaurantDomains() {
        Map<String, AspectSentiment> aspects =
                byAspect("The food was delicious but the staff was rude.");
        assertThat(aspects.get("food").label()).isEqualTo(Label.POSITIVE);
        assertThat(aspects.get("service").label()).isEqualTo(Label.NEGATIVE);
    }

    @Test
    @DisplayName("scores are bounded and rounded")
    void bounds() {
        for (AspectSentiment a : analyzer.analyze(
                "AMAZING camera!!! Horrific battery. Decent price, I guess. Shipping was ok.")) {
            assertThat(a.score()).isBetween(-1.0, 1.0);
            assertThat(a.mentions()).isNotEmpty();
        }
    }
}
