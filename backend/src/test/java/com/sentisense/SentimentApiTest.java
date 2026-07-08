package com.sentisense;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sentisense-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SentimentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    void analyzeReturnsFullAnalysis() throws Exception {
        mockMvc.perform(post("/api/sentiment/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "I absolutely love this product, amazing quality!", "source": "test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("POSITIVE"))
                .andExpect(jsonPath("$.mindState").value("DELIGHTED"))
                .andExpect(jsonPath("$.score", notNullValue()))
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.purchaseIntent", greaterThanOrEqualTo(50)))
                .andExpect(jsonPath("$.keywords", notNullValue()));
    }

    @Test
    @Order(2)
    void analyzeNegativeText() throws Exception {
        mockMvc.perform(post("/api/sentiment/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Worst purchase ever, completely useless and broke immediately"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("NEGATIVE"));
    }

    @Test
    @Order(3)
    void blankTextIsRejected() throws Exception {
        mockMvc.perform(post("/api/sentiment/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    @Order(4)
    void batchReturnsSummary() throws Exception {
        mockMvc.perform(post("/api/sentiment/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texts": ["Great phone, love it", "Terrible battery life", "Arrived on Wednesday"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.positive").value(1))
                .andExpect(jsonPath("$.negative").value(1))
                .andExpect(jsonPath("$.neutral").value(1))
                .andExpect(jsonPath("$.results", hasSize(3)));
    }

    @Test
    @Order(5)
    void emptyBatchIsRejected() throws Exception {
        mockMvc.perform(post("/api/sentiment/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"texts": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void historyContainsAnalyzedTexts() throws Exception {
        mockMvc.perform(get("/api/sentiment/history?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.content[0].label", notNullValue()));
    }

    @Test
    @Order(7)
    void statsAggregateResults() throws Exception {
        mockMvc.perform(get("/api/sentiment/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAnalyses", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.byLabel.POSITIVE", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.byMindState", notNullValue()));
    }

    @Test
    @Order(8)
    void modelInfoExposesMetrics() throws Exception {
        mockMvc.perform(get("/api/sentiment/model-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accuracy", notNullValue()))
                .andExpect(jsonPath("$.vocabularySize", greaterThanOrEqualTo(1000)));
    }

    @Test
    @Order(9)
    void historyCanBeCleared() throws Exception {
        mockMvc.perform(delete("/api/sentiment/history"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sentiment/stats"))
                .andExpect(jsonPath("$.totalAnalyses").value(0));
    }
}
