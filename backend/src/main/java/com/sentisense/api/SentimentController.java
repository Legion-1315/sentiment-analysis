package com.sentisense.api;

import java.util.List;

import com.sentisense.engine.MlClassifier;
import com.sentisense.engine.SentimentEngine;
import com.sentisense.model.AnalysisRecord;
import com.sentisense.service.SentimentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sentiment")
public class SentimentController {

    public record AnalyzeRequest(
            @NotBlank(message = "text must not be blank")
            @Size(max = 4000, message = "text must be at most 4000 characters")
            String text,
            String source) {
    }

    public record BatchRequest(
            @NotEmpty(message = "texts must not be empty")
            @Size(max = 200, message = "at most 200 texts per batch")
            List<@NotBlank @Size(max = 4000) String> texts,
            String source) {
    }

    public record BatchItem(String text, SentimentEngine.Analysis analysis) {
    }

    public record BatchSummary(int total, long positive, long negative, long neutral,
                               double averageScore, List<BatchItem> results) {
    }

    private final SentimentService service;

    public SentimentController(SentimentService service) {
        this.service = service;
    }

    @PostMapping("/analyze")
    public SentimentEngine.Analysis analyze(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyzeAndRecord(request.text(), request.source());
    }

    @PostMapping("/batch")
    public BatchSummary batch(@Valid @RequestBody BatchRequest request) {
        List<BatchItem> results = request.texts().stream()
                .map(text -> new BatchItem(text, service.analyzeAndRecord(text, request.source())))
                .toList();
        long positive = results.stream().filter(r -> r.analysis().label() == SentimentEngine.Label.POSITIVE).count();
        long negative = results.stream().filter(r -> r.analysis().label() == SentimentEngine.Label.NEGATIVE).count();
        long neutral = results.size() - positive - negative;
        double avg = results.stream().mapToDouble(r -> r.analysis().score()).average().orElse(0);
        return new BatchSummary(results.size(), positive, negative, neutral,
                Math.round(avg * 10_000.0) / 10_000.0, results);
    }

    @GetMapping("/history")
    public Page<AnalysisRecord> history(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.history(page, size);
    }

    @DeleteMapping("/history")
    public void clearHistory() {
        service.clearHistory();
    }

    @GetMapping("/stats")
    public SentimentService.Stats stats() {
        return service.stats();
    }

    @GetMapping("/model-info")
    public MlClassifier.Metrics modelInfo() {
        return service.modelMetrics();
    }
}
