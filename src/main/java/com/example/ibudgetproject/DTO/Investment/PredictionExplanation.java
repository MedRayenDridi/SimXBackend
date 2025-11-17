package com.example.ibudgetproject.DTO.Investment;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PredictionExplanation {
    private List<String> mainFactors;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendations;
    private String overallAssessment;
    private Map<String, Double> factorImpacts;
}
