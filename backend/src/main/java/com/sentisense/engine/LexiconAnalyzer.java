package com.sentisense.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based sentiment analyzer in the style of VADER (Hutto &amp; Gilbert, 2014),
 * driven by the MIT-licensed VADER lexicon (~7.5k words/emoticons rated -4..+4).
 *
 * Rules implemented: negation flipping, booster/dampener intensifiers with
 * distance decay, ALL-CAPS emphasis, exclamation amplification, and
 * contrastive-"but" clause reweighting.
 */
public final class LexiconAnalyzer {

    public record TokenScore(String token, double score) {
    }

    public record Result(double compound, double positive, double negative, double neutral,
                         List<TokenScore> contributions) {
    }

    private static final Set<String> NEGATIONS = Set.of(
            "not", "no", "never", "none", "nobody", "nothing", "neither", "nowhere",
            "isn't", "aren't", "wasn't", "weren't", "don't", "doesn't", "didn't",
            "can't", "cannot", "couldn't", "won't", "wouldn't", "shouldn't",
            "hasn't", "haven't", "hadn't", "ain't", "without", "hardly", "barely", "scarcely");

    private static final Map<String, Double> BOOSTERS = Map.ofEntries(
            Map.entry("absolutely", 0.293), Map.entry("amazingly", 0.293),
            Map.entry("completely", 0.293), Map.entry("considerably", 0.293),
            Map.entry("decidedly", 0.293), Map.entry("deeply", 0.293),
            Map.entry("enormously", 0.293), Map.entry("entirely", 0.293),
            Map.entry("especially", 0.293), Map.entry("exceptionally", 0.293),
            Map.entry("extremely", 0.293), Map.entry("greatly", 0.293),
            Map.entry("highly", 0.293), Map.entry("hugely", 0.293),
            Map.entry("incredibly", 0.293), Map.entry("intensely", 0.293),
            Map.entry("majorly", 0.293), Map.entry("particularly", 0.293),
            Map.entry("purely", 0.293), Map.entry("quite", 0.293),
            Map.entry("really", 0.293), Map.entry("remarkably", 0.293),
            Map.entry("so", 0.293), Map.entry("substantially", 0.293),
            Map.entry("thoroughly", 0.293), Map.entry("totally", 0.293),
            Map.entry("tremendously", 0.293), Map.entry("unbelievably", 0.293),
            Map.entry("utterly", 0.293), Map.entry("very", 0.293),
            Map.entry("almost", -0.293), Map.entry("barely", -0.293),
            Map.entry("kind", -0.293), Map.entry("kinda", -0.293),
            Map.entry("less", -0.293), Map.entry("little", -0.293),
            Map.entry("marginally", -0.293), Map.entry("occasionally", -0.293),
            Map.entry("partly", -0.293), Map.entry("slightly", -0.293),
            Map.entry("somewhat", -0.293), Map.entry("sort", -0.293));

    private static final double NEGATION_FLIP = -0.74;
    private static final double CAPS_BOOST = 0.733;
    private static final double EXCLAMATION_BOOST = 0.292;
    private static final double NORMALIZATION_ALPHA = 15.0;

    private final Map<String, Double> lexicon;

    public LexiconAnalyzer() {
        this.lexicon = loadLexicon("/data/vader_lexicon.txt");
    }

    static boolean isNegation(String lowerToken) {
        return NEGATIONS.contains(lowerToken);
    }

    private static Map<String, Double> loadLexicon(String resource) {
        Map<String, Double> map = new HashMap<>(16_384);
        try (InputStream in = LexiconAnalyzer.class.getResourceAsStream(resource);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    map.put(parts[0], Double.parseDouble(parts[1]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load sentiment lexicon", e);
        }
        return map;
    }

    public Result analyze(String text) {
        return analyze(text, Set.of());
    }

    /**
     * Analyze while treating {@code excludedTokens} (lowercase) as valence-free.
     * Used by aspect-based analysis: when scoring a clause *about* an aspect,
     * the aspect term itself ("support", "value") must not contribute its own
     * lexicon valence — only the opinion words around it should.
     */
    public Result analyze(String text, Set<String> excludedTokens) {
        List<String> tokens = Tokenizer.tokenize(text);
        boolean mixedCase = hasMixedCase(tokens);
        int butIndex = indexOfBut(tokens);

        List<TokenScore> contributions = new ArrayList<>();
        List<Double> valences = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String lower = token.toLowerCase(Locale.ROOT);
            if (excludedTokens.contains(lower)) {
                continue;
            }
            Double base = lexicon.get(lower);
            if (base == null || base == 0.0) {
                continue;
            }
            double valence = base;

            // ALL-CAPS emphasis, only meaningful when the text isn't uniformly cased
            if (mixedCase && token.length() > 1 && token.equals(token.toUpperCase(Locale.ROOT))
                    && token.chars().anyMatch(Character::isLetter)) {
                valence += Math.signum(valence) * CAPS_BOOST;
            }

            // Look back up to 3 tokens for boosters and negations
            for (int back = 1; back <= 3 && i - back >= 0; back++) {
                String prev = tokens.get(i - back).toLowerCase(Locale.ROOT);
                if (lexicon.containsKey(prev)) {
                    continue; // sentiment words don't modify each other
                }
                Double boost = BOOSTERS.get(prev);
                if (boost != null) {
                    double decay = switch (back) {
                        case 1 -> 1.0;
                        case 2 -> 0.95;
                        default -> 0.9;
                    };
                    valence += Math.signum(valence) * boost * decay;
                }
                if (NEGATIONS.contains(prev)) {
                    valence *= NEGATION_FLIP;
                    break;
                }
            }

            // Clauses after a contrastive "but" dominate overall meaning
            if (butIndex >= 0) {
                valence *= (i < butIndex) ? 0.5 : 1.5;
            }

            valences.add(valence);
            contributions.add(new TokenScore(token, round(valence)));
        }

        double sum = valences.stream().mapToDouble(Double::doubleValue).sum();

        // Exclamation marks amplify whichever direction the text already leans
        long exclamations = Math.min(text.chars().filter(c -> c == '!').count(), 4);
        if (sum != 0 && exclamations > 0) {
            sum += Math.signum(sum) * exclamations * EXCLAMATION_BOOST;
        }

        double compound = sum / Math.sqrt(sum * sum + NORMALIZATION_ALPHA);
        compound = Math.max(-1.0, Math.min(1.0, compound));

        double pos = 0, neg = 0, neu = 0;
        for (String token : tokens) {
            Double v = lexicon.get(token.toLowerCase(Locale.ROOT));
            if (v == null || v == 0.0) {
                neu++;
            } else if (v > 0) {
                pos++;
            } else {
                neg++;
            }
        }
        double total = Math.max(pos + neg + neu, 1);

        return new Result(round(compound), round(pos / total), round(neg / total),
                round(neu / total), contributions);
    }

    private static boolean hasMixedCase(List<String> tokens) {
        boolean anyUpper = false, anyLower = false;
        for (String t : tokens) {
            if (t.chars().anyMatch(Character::isLetter)) {
                if (t.equals(t.toUpperCase(Locale.ROOT))) {
                    anyUpper = true;
                } else {
                    anyLower = true;
                }
            }
        }
        return anyUpper && anyLower;
    }

    private static int indexOfBut(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).equalsIgnoreCase("but")) {
                return i;
            }
        }
        return -1;
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
