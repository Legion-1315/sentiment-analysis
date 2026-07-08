package com.sentisense.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits raw text into tokens while preserving emoticons (":)", ":-(") and
 * contractions ("don't"), since both carry sentiment signal.
 */
public final class Tokenizer {

    // Emoticons | words with optional apostrophes | isolated punctuation runs
    private static final Pattern TOKEN = Pattern.compile(
            "[:;=8xX][-o^]?[)(\\]\\[dDpP/\\\\|*]+"   // emoticons
            + "|<3|</3"                                // hearts
            + "|[a-zA-Z][a-zA-Z']*"                    // words / contractions
            + "|[!?]+"                                 // emphasis punctuation
            + "|\\d+(?:\\.\\d+)?");                    // numbers

    private Tokenizer() {
    }

    /** Tokens with original casing preserved (needed for ALL-CAPS emphasis detection). */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    /** Lowercased word tokens with negation scope marking, used as ML features. */
    public static List<String> mlTokens(String text) {
        List<String> raw = tokenize(text);
        List<String> out = new ArrayList<>(raw.size());
        boolean negated = false;
        for (String t : raw) {
            String lower = t.toLowerCase();
            if (t.chars().allMatch(c -> c == '!' || c == '?')) {
                negated = false; // negation scope ends at clause punctuation
                continue;
            }
            if (LexiconAnalyzer.isNegation(lower)) {
                negated = true;
                out.add(lower);
                continue;
            }
            out.add(negated ? lower + "_NEG" : lower);
        }
        return out;
    }
}
