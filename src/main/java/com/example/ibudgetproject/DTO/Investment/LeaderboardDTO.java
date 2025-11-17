package com.example.ibudgetproject.DTO.Investment;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardDTO {
    private Long userId;
    private String username;
    private String fullName;
    private double portfolioValue;
    private double totalReturn;
    private double returnPercentage;
    private int totalTrades;
    private double winRate;
    private int rank;
    private int percentile;
    private String badge;
    private double initialInvestment;
    private double totalProfit;
}
