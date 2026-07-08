package com.sentisense.engine;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hybrid sentiment engine: ensembles the rule-based lexicon analyzer with the
 * corpus-trained neural classifier. The lexicon excels at intensity, negation
 * and emoji; the trained model captures domain phrasing from real consumer
 * reviews. The ML vote is weighted by its feature coverage so that texts full
 * of unseen vocabulary lean on the lexicon instead.
 */
@Component
public class SentimentEngine {

    public enum Label { POSITIVE, NEGATIVE, NEUTRAL }

    /** Consumer state-of-mind bands derived from the ensemble score. */
    public enum MindState {
        DELIGHTED("Enthusiastic advocate — likely to buy and recommend"),
        SATISFIED("Content — receptive to offers and follow-ups"),
        UNDECIDED("Neutral or mixed — needs more convincing"),
        DISSATISFIED("Unhappy — at risk, consider outreach"),
        FRUSTRATED("Strongly negative — churn risk, prioritize recovery");

        private final String meaning;

        MindState(String meaning) {
            this.meaning = meaning;
        }

        public String meaning() {
            return meaning;
        }
    }

    public record Analysis(Label label, MindState mindState, String mindStateMeaning,
                           double score, double confidence, int purchaseIntent,
                           double lexiconScore, double mlProbability,
                           double positive, double negative, double neutral,
                           List<LexiconAnalyzer.TokenScore> keywords) {
    }

    private static final double POSITIVE_THRESHOLD = 0.10;
    private static final double NEGATIVE_THRESHOLD = -0.10;

    private static final Logger log = LoggerFactory.getLogger(SentimentEngine.class);

    private final LexiconAnalyzer lexicon;
    private final MlClassifier classifier;

    public SentimentEngine() {
        long start = System.currentTimeMillis();
        this.lexicon = new LexiconAnalyzer();
        this.classifier = MlClassifier.trainFromBundledData();
        MlClassifier.Metrics m = classifier.metrics();
        log.info("Sentiment engine ready in {} ms — ML held-out accuracy {}%, F1 {}, vocab {}",
                System.currentTimeMillis() - start, m.accuracy() * 100, m.f1(), m.vocabularySize());
    }

    public Analysis analyze(String text) {
        LexiconAnalyzer.Result lex = lexicon.analyze(text);
        double mlProb = classifier.predictPositiveProbability(text);
        double coverage = classifier.featureCoverage(text);

        // Map ML probability into [-1, 1] and weight its vote by feature coverage
        double mlScore = 2 * mlProb - 1;
        double mlWeight = 0.5 * coverage;
        double score = (1 - mlWeight) * lex.compound() + mlWeight * mlScore;

        Label label = score >= POSITIVE_THRESHOLD ? Label.POSITIVE
                : score <= NEGATIVE_THRESHOLD ? Label.NEGATIVE
                : Label.NEUTRAL;

        // Confidence: agreement between the two analyzers plus signal strength
        double agreement = 1 - Math.abs(lex.compound() - mlScore) / 2;
        double confidence = clamp(0.35 * agreement + 0.65 * Math.abs(score), 0.05, 0.99);
        if (label == Label.NEUTRAL) {
            confidence = clamp(1 - Math.abs(score) * 4, 0.05, 0.99);
        }

        MindState state = mindState(score);
        int purchaseIntent = (int) Math.round((score + 1) / 2 * 100);

        return new Analysis(label, state, state.meaning(), round(score), round(confidence),
                purchaseIntent, lex.compound(), round(mlProb),
                lex.positive(), lex.negative(), lex.neutral(), lex.contributions());
    }

    private static MindState mindState(double score) {
        if (score >= 0.55) return MindState.DELIGHTED;
        if (score >= POSITIVE_THRESHOLD) return MindState.SATISFIED;
        if (score > NEGATIVE_THRESHOLD) return MindState.UNDECIDED;
        if (score > -0.55) return MindState.DISSATISFIED;
        return MindState.FRUSTRATED;
    }

    public MlClassifier.Metrics modelMetrics() {
        return classifier.metrics();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
