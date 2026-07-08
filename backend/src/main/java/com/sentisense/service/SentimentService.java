package com.sentisense.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sentisense.engine.SentimentEngine;
import com.sentisense.model.AnalysisRecord;
import com.sentisense.repo.AnalysisRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SentimentService {

    public record Stats(long totalAnalyses, Map<String, Long> byLabel, Map<String, Long> byMindState,
                        double averageScore, double averagePurchaseIntent) {
    }

    private final SentimentEngine engine;
    private final AnalysisRepository repository;

    public SentimentService(SentimentEngine engine, AnalysisRepository repository) {
        this.engine = engine;
        this.repository = repository;
    }

    @Transactional
    public SentimentEngine.Analysis analyzeAndRecord(String text, String source) {
        SentimentEngine.Analysis analysis = engine.analyze(text);
        repository.save(new AnalysisRecord(text, analysis, source));
        return analysis;
    }

    /** Analysis without persistence, for previews/tests. */
    public SentimentEngine.Analysis analyzeOnly(String text) {
        return engine.analyze(text);
    }

    public Page<AnalysisRecord> history(int page, int size) {
        return repository.findAll(PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "analyzedAt")));
    }

    @Transactional
    public void clearHistory() {
        repository.deleteAll();
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        List<AnalysisRecord> all = repository.findAll();
        Map<String, Long> byLabel = new LinkedHashMap<>();
        for (SentimentEngine.Label label : SentimentEngine.Label.values()) {
            byLabel.put(label.name(), repository.countByLabel(label));
        }
        Map<String, Long> byState = new LinkedHashMap<>();
        for (SentimentEngine.MindState state : SentimentEngine.MindState.values()) {
            byState.put(state.name(), repository.countByMindState(state));
        }
        double avgScore = all.stream().mapToDouble(AnalysisRecord::getScore).average().orElse(0);
        double avgIntent = all.stream().mapToInt(AnalysisRecord::getPurchaseIntent).average().orElse(0);
        return new Stats(all.size(), byLabel, byState,
                Math.round(avgScore * 10_000.0) / 10_000.0,
                Math.round(avgIntent * 100.0) / 100.0);
    }

    public com.sentisense.engine.MlClassifier.Metrics modelMetrics() {
        return engine.modelMetrics();
    }
}
