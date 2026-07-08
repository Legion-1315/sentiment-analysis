package com.sentisense.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comparative evaluation of the three analyzers on the same held-out split the
 * ML classifier never saw during training (seed 42, every 10th sample per class).
 * Prints a report used in the project analysis; asserts the ensemble is at
 * least as good as the weakest individual analyzer.
 */
class EvaluationReportTest {

    @Test
    void ensembleOutperformsIndividualAnalyzers() {
        List<MlClassifier.Sample> all = new ArrayList<>();
        for (String file : List.of("amazon_cells_labelled.txt", "imdb_labelled.txt", "yelp_labelled.txt")) {
            all.addAll(MlClassifier.loadSamples("/data/" + file));
        }
        // Reproduce the classifier's stratified split exactly
        Collections.shuffle(all, new Random(42L));
        List<MlClassifier.Sample> heldOut = new ArrayList<>();
        int posSeen = 0, negSeen = 0;
        for (MlClassifier.Sample s : all) {
            if ((s.label() == 1 ? posSeen++ : negSeen++) % 10 == 0) {
                heldOut.add(s);
            }
        }

        SentimentEngine engine = new SentimentEngine();
        LexiconAnalyzer lexicon = new LexiconAnalyzer();
        MlClassifier ml = MlClassifier.trainFromBundledData();

        double lexiconAcc = accuracy(heldOut, s -> lexicon.analyze(s.text()).compound());
        double mlAcc = accuracy(heldOut, s -> ml.predictPositiveProbability(s.text()) - 0.5);
        double ensembleAcc = accuracy(heldOut, s -> engine.analyze(s.text()).score());

        System.out.printf("%n=== Held-out evaluation (%d consumer review sentences) ===%n", heldOut.size());
        System.out.printf("Lexicon only  : %.1f%%%n", lexiconAcc * 100);
        System.out.printf("ML only       : %.1f%%%n", mlAcc * 100);
        System.out.printf("Ensemble      : %.1f%%%n%n", ensembleAcc * 100);

        assertThat(ensembleAcc).isGreaterThanOrEqualTo(Math.min(lexiconAcc, mlAcc));
        assertThat(ensembleAcc).isGreaterThanOrEqualTo(0.78);
    }

    private static double accuracy(List<MlClassifier.Sample> samples,
                                   ToDoubleFunction<MlClassifier.Sample> scorer) {
        long correct = samples.stream()
                .filter(s -> (scorer.applyAsDouble(s) >= 0) == (s.label() == 1))
                .count();
        return (double) correct / samples.size();
    }
}
