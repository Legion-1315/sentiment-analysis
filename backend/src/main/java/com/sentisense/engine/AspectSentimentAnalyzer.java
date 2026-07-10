package com.sentisense.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aspect-based sentiment analysis (ABSA): instead of one score for the whole
 * text, produce a sentiment per product/service aspect mentioned in it
 * ("camera = positive, battery = negative").
 *
 * Pipeline:
 * <ol>
 *   <li><b>Clause segmentation</b> — sentences are split at hard boundaries
 *       (.;!?\n) and soft boundaries (commas and coordinating/contrastive
 *       conjunctions). Segments without an aspect mention are merged into the
 *       neighbouring aspect clause so their opinion words are not lost.</li>
 *   <li><b>Aspect detection</b> — clause tokens are matched against a curated
 *       taxonomy ({@code data/aspect_lexicon.txt}, term → canonical aspect)
 *       with naive plural stripping.</li>
 *   <li><b>Clause scoring</b> — each aspect clause is scored by the
 *       {@link LexiconAnalyzer} with the aspect terms themselves excluded from
 *       valence. This fixes a documented failure of whole-text scoring: in
 *       "support never replied", the lexicon's positive valence for the noun
 *       "support" no longer drowns out the negation.</li>
 *   <li><b>Unmatched-negation prior</b> — a clause that contains a negation
 *       but no valence-bearing word ("never replied to my emails") receives a
 *       mild negative score instead of a neutral one; in consumer reviews an
 *       unfulfilled action is a complaint.</li>
 * </ol>
 */
public final class AspectSentimentAnalyzer {

    /** Aggregated sentiment for one canonical aspect across the whole text. */
    public record AspectSentiment(String aspect, SentimentEngine.Label label, double score,
                                  List<String> mentions, List<String> evidence) {
    }

    private static final Pattern HARD_BOUNDARY = Pattern.compile("[.;!?\\n]+");
    private static final Pattern SOFT_BOUNDARY =
            Pattern.compile(",|\\b(?:but|and|or|however|though|although|yet|while|whereas)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final double POSITIVE_THRESHOLD = 0.10;
    private static final double NEGATIVE_THRESHOLD = -0.10;
    /** Score for a clause whose only signal is an unmatched negation. */
    private static final double UNMATCHED_NEGATION_SCORE = -0.35;
    private static final int MAX_EVIDENCE = 3;

    private final Map<String, String> termToAspect;
    private final LexiconAnalyzer lexicon;

    public AspectSentimentAnalyzer(LexiconAnalyzer lexicon) {
        this.lexicon = lexicon;
        this.termToAspect = loadTaxonomy("/data/aspect_lexicon.txt");
    }

    private static Map<String, String> loadTaxonomy(String resource) {
        Map<String, String> map = new HashMap<>(256);
        try (InputStream in = AspectSentimentAnalyzer.class.getResourceAsStream(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    map.put(parts[0].toLowerCase(Locale.ROOT), parts[1].toLowerCase(Locale.ROOT));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load aspect taxonomy", e);
        }
        return map;
    }

    public List<AspectSentiment> analyze(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // aspect -> accumulated clause scores / surface mentions / evidence clauses
        Map<String, List<Double>> scores = new LinkedHashMap<>();
        Map<String, Set<String>> mentions = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new LinkedHashMap<>();

        for (String sentence : HARD_BOUNDARY.split(text)) {
            if (sentence.isBlank()) {
                continue;
            }
            for (Clause clause : segmentIntoAspectClauses(sentence)) {
                double score = scoreClause(clause);
                for (String aspect : clause.aspects) {
                    scores.computeIfAbsent(aspect, k -> new ArrayList<>()).add(score);
                    mentions.computeIfAbsent(aspect, k -> new LinkedHashSet<>()).addAll(clause.terms);
                    List<String> ev = evidence.computeIfAbsent(aspect, k -> new ArrayList<>());
                    if (ev.size() < MAX_EVIDENCE) {
                        ev.add(clause.text.trim());
                    }
                }
            }
        }

        List<AspectSentiment> result = new ArrayList<>(scores.size());
        for (Map.Entry<String, List<Double>> e : scores.entrySet()) {
            double mean = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0);
            SentimentEngine.Label label = mean >= POSITIVE_THRESHOLD ? SentimentEngine.Label.POSITIVE
                    : mean <= NEGATIVE_THRESHOLD ? SentimentEngine.Label.NEGATIVE
                    : SentimentEngine.Label.NEUTRAL;
            result.add(new AspectSentiment(e.getKey(), label, round(mean),
                    List.copyOf(mentions.get(e.getKey())), List.copyOf(evidence.get(e.getKey()))));
        }
        // Most-mentioned first, strongest signal breaking ties
        result.sort(Comparator
                .comparingInt((AspectSentiment a) -> scores.get(a.aspect()).size()).reversed()
                .thenComparing(a -> -Math.abs(a.score())));
        return result;
    }

    /** A clause with the canonical aspects and surface terms mentioned in it. */
    private record Clause(String text, Set<String> aspects, Set<String> terms) {
    }

    /**
     * Split a sentence at soft boundaries, then merge aspect-less segments into
     * the neighbouring aspect clause (leading segments attach forward, trailing
     * ones attach backward) so opinion words stay with the aspect they modify.
     */
    private List<Clause> segmentIntoAspectClauses(String sentence) {
        List<String> segments = new ArrayList<>();
        List<String> delimiters = new ArrayList<>(); // delimiter following segment i
        Matcher m = SOFT_BOUNDARY.matcher(sentence);
        int last = 0;
        while (m.find()) {
            segments.add(sentence.substring(last, m.start()));
            delimiters.add(m.group());
            last = m.end();
        }
        segments.add(sentence.substring(last));

        List<Clause> clauses = new ArrayList<>();
        StringBuilder pending = new StringBuilder(); // aspect-less prefix, attaches forward
        for (int i = 0; i < segments.size(); i++) {
            String segment = segments.get(i);
            Set<String> aspects = new LinkedHashSet<>();
            Set<String> terms = new LinkedHashSet<>();
            detectAspects(segment, aspects, terms);

            String delimiter = i < delimiters.size() ? delimiters.get(i) : "";
            if (aspects.isEmpty()) {
                if (!clauses.isEmpty()) {
                    // attach backward: extend the previous aspect clause
                    Clause prev = clauses.remove(clauses.size() - 1);
                    String joined = prev.text + " " + priorDelimiter(delimiters, i) + " " + segment;
                    clauses.add(new Clause(joined, prev.aspects, prev.terms));
                } else {
                    pending.append(segment).append(' ').append(delimiter).append(' ');
                }
            } else {
                String clauseText = pending.isEmpty() ? segment : pending + segment;
                pending.setLength(0);
                clauses.add(new Clause(clauseText, aspects, terms));
            }
        }
        return clauses;
    }

    private static String priorDelimiter(List<String> delimiters, int segmentIndex) {
        return segmentIndex - 1 < delimiters.size() && segmentIndex - 1 >= 0
                ? delimiters.get(segmentIndex - 1) : "";
    }

    private void detectAspects(String segment, Set<String> aspects, Set<String> terms) {
        for (String token : Tokenizer.tokenize(segment)) {
            String lower = token.toLowerCase(Locale.ROOT);
            String aspect = termToAspect.get(lower);
            if (aspect == null && lower.endsWith("s") && lower.length() > 3) {
                aspect = termToAspect.get(lower.substring(0, lower.length() - 1));
            }
            if (aspect != null) {
                aspects.add(aspect);
                terms.add(lower);
            }
        }
    }

    private double scoreClause(Clause clause) {
        LexiconAnalyzer.Result result = lexicon.analyze(clause.text, clause.terms);
        if (result.compound() != 0.0) {
            return result.compound();
        }
        // Unmatched negation: "support never replied to my emails" has no
        // valence-bearing word once "support" is excluded, but the negated
        // action is a complaint, not a neutral statement.
        for (String token : Tokenizer.tokenize(clause.text)) {
            if (LexiconAnalyzer.isNegation(token.toLowerCase(Locale.ROOT))) {
                return UNMATCHED_NEGATION_SCORE;
            }
        }
        return 0.0;
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
