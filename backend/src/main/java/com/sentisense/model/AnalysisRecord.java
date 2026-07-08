package com.sentisense.model;

import java.time.Instant;

import com.sentisense.engine.SentimentEngine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "analysis_records")
public class AnalysisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SentimentEngine.Label label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SentimentEngine.MindState mindState;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private int purchaseIntent;

    @Column(length = 100)
    private String source;

    @Column(nullable = false)
    private Instant analyzedAt;

    protected AnalysisRecord() {
    }

    public AnalysisRecord(String text, SentimentEngine.Analysis analysis, String source) {
        this.text = text.length() > 4000 ? text.substring(0, 4000) : text;
        this.label = analysis.label();
        this.mindState = analysis.mindState();
        this.score = analysis.score();
        this.confidence = analysis.confidence();
        this.purchaseIntent = analysis.purchaseIntent();
        this.source = source;
        this.analyzedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public SentimentEngine.Label getLabel() {
        return label;
    }

    public SentimentEngine.MindState getMindState() {
        return mindState;
    }

    public double getScore() {
        return score;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getPurchaseIntent() {
        return purchaseIntent;
    }

    public String getSource() {
        return source;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
