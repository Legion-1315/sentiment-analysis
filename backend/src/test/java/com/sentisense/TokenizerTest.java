package com.sentisense;

import java.util.List;

import com.sentisense.engine.Tokenizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizerTest {

    @Test
    void preservesCaseAndContractions() {
        List<String> tokens = Tokenizer.tokenize("I DON'T like it");
        assertThat(tokens).containsExactly("I", "DON'T", "like", "it");
    }

    @Test
    void capturesEmoticonsAndPunctuationRuns() {
        List<String> tokens = Tokenizer.tokenize("great product :) love it!!!");
        assertThat(tokens).contains(":)", "!!!");
    }

    @Test
    void handlesEmptyAndNullInput() {
        assertThat(Tokenizer.tokenize("")).isEmpty();
        assertThat(Tokenizer.tokenize(null)).isEmpty();
        assertThat(Tokenizer.tokenize("   ")).isEmpty();
    }

    @Test
    void mlTokensMarkNegationScope() {
        List<String> tokens = Tokenizer.mlTokens("I do not like this phone");
        assertThat(tokens).containsExactly("i", "do", "not", "like_NEG", "this_NEG", "phone_NEG");
    }

    @Test
    void negationScopeEndsAtClausePunctuation() {
        List<String> tokens = Tokenizer.mlTokens("not good! but works");
        assertThat(tokens).containsExactly("not", "good_NEG", "but", "works");
    }
}
