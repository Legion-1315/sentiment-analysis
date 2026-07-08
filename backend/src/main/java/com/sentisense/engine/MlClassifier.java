package com.sentisense.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A single-layer neural text classifier (logistic regression trained with SGD)
 * over unigram + bigram features with negation-scope marking.
 *
 * Trained at startup on the UCI "Sentiment Labelled Sentences" corpus:
 * 3,000 real consumer review sentences from Amazon, IMDb and Yelp, labelled
 * positive (1) or negative (0). A stratified 10% split is held out to report
 * honest evaluation metrics via {@link #metrics()}.
 */
public final class MlClassifier {

    public record Sample(String text, int label) {
    }

    public record Metrics(int trainSize, int testSize, double accuracy, double precision,
                          double recall, double f1, int vocabularySize, int epochs) {
    }

    private static final int EPOCHS = 40;
    private static final double LEARNING_RATE = 0.08;
    private static final double L2 = 1e-5;
    private static final long SEED = 42L;
    private static final int MIN_FEATURE_COUNT = 2;

    private final Map<String, Integer> vocabulary = new HashMap<>();
    private double[] weights;
    private double bias;
    private Metrics metrics;

    /** Trains from the bundled UCI dataset resources. */
    public static MlClassifier trainFromBundledData() {
        List<Sample> samples = new ArrayList<>();
        for (String file : List.of("amazon_cells_labelled.txt", "imdb_labelled.txt", "yelp_labelled.txt")) {
            samples.addAll(loadSamples("/data/" + file));
        }
        MlClassifier classifier = new MlClassifier();
        classifier.train(samples);
        return classifier;
    }

    static List<Sample> loadSamples(String resource) {
        List<Sample> samples = new ArrayList<>();
        try (InputStream in = MlClassifier.class.getResourceAsStream(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int tab = line.lastIndexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String text = line.substring(0, tab).trim();
                String label = line.substring(tab + 1).trim();
                if (!text.isEmpty() && (label.equals("0") || label.equals("1"))) {
                    samples.add(new Sample(text, Integer.parseInt(label)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load training data " + resource, e);
        }
        return samples;
    }

    public void train(List<Sample> allSamples) {
        List<Sample> shuffled = new ArrayList<>(allSamples);
        Collections.shuffle(shuffled, new Random(SEED));

        // Stratified 90/10 train/test split
        List<Sample> train = new ArrayList<>(), test = new ArrayList<>();
        int posSeen = 0, negSeen = 0;
        for (Sample s : shuffled) {
            boolean toTest = (s.label() == 1 ? posSeen++ : negSeen++) % 10 == 0;
            (toTest ? test : train).add(s);
        }

        buildVocabulary(train);
        weights = new double[vocabulary.size()];
        bias = 0;

        Random rng = new Random(SEED);
        for (int epoch = 0; epoch < EPOCHS; epoch++) {
            Collections.shuffle(train, rng);
            double lr = LEARNING_RATE / (1 + 0.05 * epoch);
            for (Sample s : train) {
                int[] features = featurize(s.text());
                double p = predictFromFeatures(features);
                double gradient = p - s.label();
                for (int f : features) {
                    weights[f] -= lr * (gradient + L2 * weights[f]);
                }
                bias -= lr * gradient;
            }
        }

        metrics = evaluate(train.size(), test);
    }

    private void buildVocabulary(List<Sample> train) {
        Map<String, Integer> counts = new HashMap<>();
        for (Sample s : train) {
            for (String f : extractFeatures(s.text())) {
                counts.merge(f, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= MIN_FEATURE_COUNT) {
                vocabulary.put(e.getKey(), vocabulary.size());
            }
        }
    }

    private static List<String> extractFeatures(String text) {
        List<String> tokens = Tokenizer.mlTokens(text);
        List<String> features = new ArrayList<>(tokens);
        for (int i = 0; i + 1 < tokens.size(); i++) {
            features.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return features;
    }

    private int[] featurize(String text) {
        return extractFeatures(text).stream()
                .map(vocabulary::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .mapToInt(Integer::intValue)
                .toArray();
    }

    private double predictFromFeatures(int[] features) {
        double z = bias;
        for (int f : features) {
            z += weights[f];
        }
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /** Probability that the text is positive, in [0, 1]. */
    public double predictPositiveProbability(String text) {
        return predictFromFeatures(featurize(text));
    }

    /** Fraction of the text's features seen during training — a coverage/confidence signal. */
    public double featureCoverage(String text) {
        List<String> features = extractFeatures(text);
        if (features.isEmpty()) {
            return 0;
        }
        long known = features.stream().filter(vocabulary::containsKey).count();
        return (double) known / features.size();
    }

    private Metrics evaluate(int trainSize, List<Sample> test) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (Sample s : test) {
            boolean predictedPositive = predictPositiveProbability(s.text()) >= 0.5;
            if (predictedPositive && s.label() == 1) tp++;
            else if (predictedPositive) fp++;
            else if (s.label() == 0) tn++;
            else fn++;
        }
        double accuracy = (double) (tp + tn) / Math.max(test.size(), 1);
        double precision = tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        return new Metrics(trainSize, test.size(), round(accuracy), round(precision),
                round(recall), round(f1), vocabulary.size(), EPOCHS);
    }

    public Metrics metrics() {
        return metrics;
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
