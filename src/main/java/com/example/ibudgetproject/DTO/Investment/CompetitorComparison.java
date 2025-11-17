package com.example.ibudgetproject.DTO.Investment;

import lombok.*;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompetitorComparison {
    private String username;
    private int currentRank;
    private int predictedRank;
    private double predictedValue;
    private String threat;
}
