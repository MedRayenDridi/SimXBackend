package com.example.ibudgetproject.DTO.Investment;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AssetPrediction {
    private String coinId;
    private String coinName;
    private String coinSymbol;
    private double currentQuantity;
    private double currentValue;
    private double predictedValue;
    private double expectedReturn;
    private double volatility;
    private int contributionToRank;
    private String riskLevel;
    private String recommendation;
}
