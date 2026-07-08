package com.sentisense.repo;

import com.sentisense.engine.SentimentEngine;
import com.sentisense.model.AnalysisRecord;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<AnalysisRecord, Long> {

    long countByLabel(SentimentEngine.Label label);

    long countByMindState(SentimentEngine.MindState mindState);
}
