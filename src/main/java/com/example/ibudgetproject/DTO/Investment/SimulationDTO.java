package com.example.ibudgetproject.DTO.Investment;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SimulationDTO {
    private Long userId;
    private String username;
    private String fullName;

    // Current Stats
    private int currentRank;
    private double currentPortfolioValue;
    private double currentReturnPercentage;

    // Predicted Stats (1 Year)
    private int predictedRank;
    private double predictedPortfolioValue;
    private double predictedReturnPercentage;
    private int rankChange;

    // Prediction Metrics
    private double confidenceLevel;
    private int riskScore;
    private double expectedAnnualReturn;
    private double worstCaseValue;
    private double bestCaseValue;

    // Portfolio Analysis
    private double diversificationScore;
    private double portfolioVolatility;
    private double sharpeRatio;

    // Detailed Explanation
    private PredictionExplanation explanation;

    // Asset Breakdown
    private List<AssetPrediction> assetPredictions;

    // Comparison with Others
    private List<CompetitorComparison> nearbyCompetitors;
}
