package com.example.ibudgetproject.services.Investment;

import com.example.ibudgetproject.DTO.Investment.*;
import com.example.ibudgetproject.entities.Investment.Asset;
import com.example.ibudgetproject.entities.Investment.Order;
import com.example.ibudgetproject.entities.Investment.Wallet;
import com.example.ibudgetproject.entities.Investment.domain.OrderStatus;
import com.example.ibudgetproject.entities.Investment.domain.OrderType;
import com.example.ibudgetproject.entities.User.User;
import com.example.ibudgetproject.repositories.Investment.AssetRepository;
import com.example.ibudgetproject.repositories.Investment.OrderRepository;
import com.example.ibudgetproject.repositories.Investment.WalletRepository;
import com.example.ibudgetproject.repositories.User.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SimulationServiceImpl implements SimulationService {

    private static final int MONTE_CARLO_SIMULATIONS = 10000;
    private static final int SIMULATION_DAYS = 365;
    private static final double RISK_FREE_RATE = 0.03;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<SimulationDTO> simulateOneYearForAllUsers() {
        System.out.println("Starting 1-year simulation for all users...");

        List<User> allUsers = userRepository.findAll();
        List<SimulationDTO> simulations = new ArrayList<>();

        for (User user : allUsers) {
            try {
                SimulationDTO simulation = simulateOneYearForUser(user.getUserId());
                simulations.add(simulation);
            } catch (Exception e) {
                System.err.println("Error simulating for user " + user.getUserId() + ": " + e.getMessage());
            }
        }

        // Sort by predicted portfolio value (descending)
        simulations.sort(Comparator.comparingDouble(SimulationDTO::getPredictedPortfolioValue).reversed());

        // Assign predicted ranks and calculate rank changes
        for (int i = 0; i < simulations.size(); i++) {
            SimulationDTO sim = simulations.get(i);
            sim.setPredictedRank(i + 1);
            // Rank change = current rank - predicted rank (positive means moving up)
            int rankChange = sim.getCurrentRank() - (i + 1);
            sim.setRankChange(rankChange);
        }

        // Add competitor comparisons
        for (int i = 0; i < simulations.size(); i++) {
            SimulationDTO current = simulations.get(i);
            List<CompetitorComparison> competitors = new ArrayList<>();

            int start = Math.max(0, i - 3);
            int end = Math.min(simulations.size(), i + 4);

            for (int j = start; j < end; j++) {
                if (i != j) {
                    SimulationDTO competitor = simulations.get(j);
                    String threat = calculateThreatLevel(current, competitor);
                    competitors.add(new CompetitorComparison(
                            competitor.getUsername(),
                            competitor.getCurrentRank(),
                            j + 1,
                            competitor.getPredictedPortfolioValue(),
                            threat
                    ));
                }
            }

            current.setNearbyCompetitors(competitors);
        }

        System.out.println("✅ Simulation complete for " + simulations.size() + " users");
        return simulations;
    }

    @Override
    public SimulationDTO simulateOneYearForUser(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        SimulationDTO dto = new SimulationDTO();

        // Current stats
        LeaderboardDTO currentStats = leaderboardService.getUserRankAndStats(userId);
        dto.setUserId(userId);
        dto.setUsername(user.getEmail().split("@")[0]);
        dto.setFullName(user.fullName());
        dto.setCurrentRank(currentStats.getRank());
        dto.setCurrentPortfolioValue(currentStats.getPortfolioValue());
        dto.setCurrentReturnPercentage(currentStats.getReturnPercentage());

        // Get user's assets and trading history
        List<Asset> assets = assetRepository.findByUser_UserId(userId);
        List<Order> orders = orderRepository.findByUser_UserId(userId).stream()
                .filter(o -> o.getStatus() == OrderStatus.SUCCESS)
                .collect(Collectors.toList());

        Wallet wallet = walletRepository.findByUser_UserId(userId);
        double walletBalance = wallet != null ? wallet.getBalance().doubleValue() : 0;

        if (assets.isEmpty()) {
            dto.setPredictedPortfolioValue(walletBalance);
            dto.setPredictedReturnPercentage(0);
            dto.setConfidenceLevel(0.95);
            dto.setRiskScore(0);
            dto.setExpectedAnnualReturn(0);
            dto.setWorstCaseValue(walletBalance);
            dto.setBestCaseValue(walletBalance);
            dto.setDiversificationScore(0);
            dto.setPortfolioVolatility(0);
            dto.setSharpeRatio(0);
            dto.setExplanation(createBeginnerExplanation());
            dto.setAssetPredictions(new ArrayList<>());
            return dto;
        }

        // Run Monte Carlo simulation
        MonteCarloResult mcResult = runMonteCarloSimulation(assets, walletBalance);

        // Calculate trading behavior score
        TradingBehavior behavior = analyzeTradingBehavior(orders);

        // Calculate portfolio metrics
        PortfolioMetrics metrics = calculatePortfolioMetrics(assets);

        // Predict future value
        double predictedValue = mcResult.getExpectedValue() * (1 + behavior.getExpectedAnnualGrowth());
        dto.setPredictedPortfolioValue(predictedValue);
        dto.setPredictedReturnPercentage(((predictedValue - currentStats.getPortfolioValue()) / currentStats.getPortfolioValue()) * 100);

        // Set prediction metrics
        dto.setConfidenceLevel(mcResult.getConfidenceLevel());
        dto.setRiskScore(metrics.getRiskScore());
        dto.setExpectedAnnualReturn(((predictedValue - currentStats.getPortfolioValue()) / currentStats.getPortfolioValue()) * 100);
        dto.setWorstCaseValue(mcResult.getWorstCase());
        dto.setBestCaseValue(mcResult.getBestCase());

        // Portfolio analysis
        dto.setDiversificationScore(metrics.getDiversificationScore());
        dto.setPortfolioVolatility(metrics.getVolatility());
        dto.setSharpeRatio(metrics.getSharpeRatio());

        // Generate detailed explanation
        dto.setExplanation(generateExplanation(assets, metrics, behavior, mcResult));

        // Asset predictions
        dto.setAssetPredictions(predictIndividualAssets(assets, mcResult));

        return dto;
    }

    @Override
    public Map<String, Object> getSimulationSummary() {
        List<SimulationDTO> allSimulations = simulateOneYearForAllUsers();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", allSimulations.size());
        summary.put("averagePredictedReturn", allSimulations.stream()
                .mapToDouble(SimulationDTO::getPredictedReturnPercentage)
                .average()
                .orElse(0));
        summary.put("topPerformers", allSimulations.stream().limit(10).collect(Collectors.toList()));

        // Fix: getRankChange is already set by simulateOneYearForAllUsers
        summary.put("biggestRankChanges", allSimulations.stream()
                .sorted(Comparator.comparingInt((SimulationDTO s) -> Math.abs(s.getRankChange()))
                        .reversed())
                .limit(10)
                .collect(Collectors.toList()));

        return summary;
    }


    // ========== MONTE CARLO SIMULATION ==========

    private MonteCarloResult runMonteCarloSimulation(List<Asset> assets, double walletBalance) {
        Random random = new Random();
        double[] simulatedValues = new double[MONTE_CARLO_SIMULATIONS];

        double currentTotalValue = walletBalance;
        Map<String, AssetVolatility> assetVolatilities = new HashMap<>();

        for (Asset asset : assets) {
            double currentValue = asset.getQuantity() * asset.getCoin().getCurrentPrice();
            currentTotalValue += currentValue;

            try {
                double[] historicalPrices = fetchHistoricalPricesWrapper(asset.getCoin().getId(), 365);
                double[] returns = calculateReturns(historicalPrices);
                double meanReturn = new Mean().evaluate(returns);
                double volatility = new StandardDeviation().evaluate(returns);

                assetVolatilities.put(asset.getCoin().getId(), new AssetVolatility(meanReturn, volatility, currentValue));
            } catch (Exception e) {
                assetVolatilities.put(asset.getCoin().getId(), new AssetVolatility(0.0005, 0.03, currentValue));
            }
        }

        for (int i = 0; i < MONTE_CARLO_SIMULATIONS; i++) {
            double simulatedPortfolio = walletBalance;

            for (Asset asset : assets) {
                AssetVolatility vol = assetVolatilities.get(asset.getCoin().getId());
                double assetValue = vol.getCurrentValue();

                for (int day = 0; day < SIMULATION_DAYS; day++) {
                    double dailyReturn = vol.getMeanReturn() + vol.getVolatility() * random.nextGaussian();
                    assetValue *= (1 + dailyReturn);
                }

                simulatedPortfolio += assetValue;
            }

            simulatedValues[i] = simulatedPortfolio;
        }

        Arrays.sort(simulatedValues);

        double expectedValue = Arrays.stream(simulatedValues).average().orElse(currentTotalValue);
        double worstCase = simulatedValues[(int) (MONTE_CARLO_SIMULATIONS * 0.05)];
        double bestCase = simulatedValues[(int) (MONTE_CARLO_SIMULATIONS * 0.95)];
        double confidenceLevel = 1.0 - (new StandardDeviation().evaluate(simulatedValues) / expectedValue);

        return new MonteCarloResult(expectedValue, worstCase, bestCase, Math.max(0, Math.min(1, confidenceLevel)));
    }

    // ========== WRAPPER FOR HISTORICAL PRICES (doesn't touch AssetServiceImpl) ==========

    private double[] fetchHistoricalPricesWrapper(String coinId, int days) throws Exception {
        String url = "https://api.coingecko.com/api/v3/coins/" + coinId + "/market_chart?vs_currency=usd&days=" + days;
        RestTemplate restTemplate = new RestTemplate();

        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            JsonNode pricesArray = jsonNode.get("prices");
            double[] historicalPrices = new double[pricesArray.size()];

            for (int i = 0; i < pricesArray.size(); i++) {
                historicalPrices[i] = pricesArray.get(i).get(1).asDouble();
            }

            return historicalPrices;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new Exception(e.getMessage());
        }
    }

    private TradingBehavior analyzeTradingBehavior(List<Order> orders) {
        if (orders.isEmpty()) {
            return new TradingBehavior(0, 0, 0, 0);
        }

        int totalTrades = orders.size();

        LocalDateTime firstTrade = orders.stream()
                .map(Order::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now().minusMonths(1));
        long monthsActive = Math.max(1, ChronoUnit.MONTHS.between(firstTrade, LocalDateTime.now()));
        double tradesPerMonth = (double) totalTrades / monthsActive;

        long sellOrders = orders.stream().filter(o -> o.getOrderType() == OrderType.SELL).count();
        long profitableSells = orders.stream()
                .filter(o -> o.getOrderType() == OrderType.SELL)
                .filter(o -> o.getOrderItem() != null &&
                        o.getOrderItem().getSellPrice() > o.getOrderItem().getBuyPrice())
                .count();
        double winRate = sellOrders > 0 ? (double) profitableSells / sellOrders : 0.5;

        double avgHoldingDays = 30.0;

        double behaviorMultiplier = 1.0;
        if (tradesPerMonth > 20) behaviorMultiplier += 0.15;
        if (winRate > 0.7) behaviorMultiplier += 0.20;
        if (winRate < 0.4) behaviorMultiplier -= 0.15;

        double expectedGrowth = (behaviorMultiplier - 1.0) * 0.5;

        return new TradingBehavior(tradesPerMonth, winRate, avgHoldingDays, expectedGrowth);
    }

    private PortfolioMetrics calculatePortfolioMetrics(List<Asset> assets) {
        double totalValue = assets.stream()
                .mapToDouble(a -> a.getQuantity() * a.getCoin().getCurrentPrice())
                .sum();

        int uniqueAssets = assets.size();
        double diversificationScore = Math.min(100, uniqueAssets * 12.5);

        double weightedVolatility = 0;
        double[] assetReturns = new double[assets.size()];

        for (int i = 0; i < assets.size(); i++) {
            Asset asset = assets.get(i);
            double weight = (asset.getQuantity() * asset.getCoin().getCurrentPrice()) / totalValue;

            try {
                double[] historicalPrices = fetchHistoricalPricesWrapper(asset.getCoin().getId(), 90);
                double[] returns = calculateReturns(historicalPrices);
                double volatility = new StandardDeviation().evaluate(returns);
                weightedVolatility += weight * volatility;
                assetReturns[i] = new Mean().evaluate(returns);
            } catch (Exception e) {
                weightedVolatility += weight * 0.03;
                assetReturns[i] = 0.0005;
            }
        }

        double portfolioReturn = new Mean().evaluate(assetReturns);
        double annualReturn = portfolioReturn * 252;
        double annualVolatility = weightedVolatility * Math.sqrt(252);
        double sharpeRatio = annualVolatility > 0 ? (annualReturn - RISK_FREE_RATE) / annualVolatility : 0;

        int riskScore = (int) Math.min(100, weightedVolatility * 3000);

        return new PortfolioMetrics(diversificationScore, weightedVolatility, sharpeRatio, riskScore);
    }

    private PredictionExplanation generateExplanation(List<Asset> assets, PortfolioMetrics metrics,
                                                      TradingBehavior behavior, MonteCarloResult mcResult) {
        List<String> mainFactors = new ArrayList<>();
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        Map<String, Double> factorImpacts = new HashMap<>();

        if (metrics.getDiversificationScore() > 75) {
            strengths.add("Excellent portfolio diversification across " + assets.size() + " different assets");
            mainFactors.add("Strong diversification reduces overall risk");
            factorImpacts.put("Diversification", 25.0);
        } else if (metrics.getDiversificationScore() < 40) {
            weaknesses.add("Low diversification - portfolio concentrated in few assets");
            recommendations.add("Consider diversifying into 6-8 different cryptocurrencies");
            factorImpacts.put("Diversification", -15.0);
        }

        if (metrics.getRiskScore() > 70) {
            weaknesses.add("High-risk portfolio with significant volatility");
            recommendations.add("Consider adding stable assets like BTC or stablecoins");
            factorImpacts.put("Risk Management", -10.0);
        } else if (metrics.getRiskScore() < 30) {
            strengths.add("Conservative, low-risk portfolio approach");
            factorImpacts.put("Risk Management", 15.0);
        }

        if (behavior.getWinRate() > 0.7) {
            strengths.add(String.format("Excellent trading performance with %.1f%% win rate", behavior.getWinRate() * 100));
            mainFactors.add("Consistent profitable trading decisions");
            factorImpacts.put("Trading Skill", 30.0);
        } else if (behavior.getWinRate() < 0.4) {
            weaknesses.add("Low win rate suggests room for improvement in trade timing");
            recommendations.add("Focus on technical analysis and market timing");
            factorImpacts.put("Trading Skill", -20.0);
        }

        if (behavior.getTradesPerMonth() > 30) {
            mainFactors.add("Very active day trading strategy");
            factorImpacts.put("Trading Activity", 20.0);
        } else if (behavior.getTradesPerMonth() < 2) {
            mainFactors.add("Long-term hold strategy");
            factorImpacts.put("Trading Activity", 5.0);
        }

        if (metrics.getSharpeRatio() > 1.5) {
            strengths.add("Excellent risk-adjusted returns (Sharpe Ratio: " + String.format("%.2f", metrics.getSharpeRatio()) + ")");
            factorImpacts.put("Risk-Adjusted Returns", 25.0);
        } else if (metrics.getSharpeRatio() < 0.5) {
            weaknesses.add("Low risk-adjusted returns - returns don't justify the risk taken");
            factorImpacts.put("Risk-Adjusted Returns", -10.0);
        }

        Map<String, Double> assetAllocations = calculateAssetAllocations(assets);
        String topAsset = assetAllocations.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");

        double topAllocation = assetAllocations.getOrDefault(topAsset, 0.0);
        if (topAllocation > 50) {
            weaknesses.add(String.format("Over-concentrated in %s (%.1f%% of portfolio)", topAsset.toUpperCase(), topAllocation));
            recommendations.add("Reduce " + topAsset.toUpperCase() + " allocation to below 40%");
        }

        String overallAssessment = generateOverallAssessment(metrics, behavior, mcResult);

        return new PredictionExplanation(mainFactors, strengths, weaknesses, recommendations, overallAssessment, factorImpacts);
    }

    private String generateOverallAssessment(PortfolioMetrics metrics, TradingBehavior behavior, MonteCarloResult mcResult) {
        StringBuilder assessment = new StringBuilder();

        double expectedReturn = ((mcResult.getExpectedValue() - mcResult.getWorstCase()) / mcResult.getWorstCase()) * 100;

        if (expectedReturn > 50) {
            assessment.append("Excellent growth potential! ");
        } else if (expectedReturn > 20) {
            assessment.append("Strong growth trajectory. ");
        } else {
            assessment.append("Moderate growth expected. ");
        }

        if (metrics.getRiskScore() > 70) {
            assessment.append("However, high volatility increases risk. ");
        } else if (metrics.getRiskScore() < 30) {
            assessment.append("Low risk profile provides stability. ");
        }

        if (behavior.getWinRate() > 0.6) {
            assessment.append("Your trading decisions have been consistently profitable, which should continue driving performance.");
        } else {
            assessment.append("Improving trade timing could significantly boost returns.");
        }

        return assessment.toString();
    }

    private List<AssetPrediction> predictIndividualAssets(List<Asset> assets, MonteCarloResult mcResult) {
        List<AssetPrediction> predictions = new ArrayList<>();
        double totalCurrentValue = assets.stream()
                .mapToDouble(a -> a.getQuantity() * a.getCoin().getCurrentPrice())
                .sum();

        for (Asset asset : assets) {
            double currentValue = asset.getQuantity() * asset.getCoin().getCurrentPrice();
            double weight = currentValue / totalCurrentValue;

            try {
                double[] historicalPrices = fetchHistoricalPricesWrapper(asset.getCoin().getId(), 365);
                double[] returns = calculateReturns(historicalPrices);
                double meanReturn = new Mean().evaluate(returns) * 252;
                double volatility = new StandardDeviation().evaluate(returns) * Math.sqrt(252);

                double predictedValue = currentValue * (1 + meanReturn);
                double expectedReturn = ((predictedValue - currentValue) / currentValue) * 100;

                String riskLevel = getRiskLevel(volatility);
                String recommendation = getRecommendation(expectedReturn, volatility, weight);
                int contribution = (int) (weight * 100);

                predictions.add(new AssetPrediction(
                        asset.getCoin().getId(),
                        asset.getCoin().getName(),
                        asset.getCoin().getSymbol(),
                        asset.getQuantity(),
                        currentValue,
                        predictedValue,
                        expectedReturn,
                        volatility,
                        contribution,
                        riskLevel,
                        recommendation
                ));
            } catch (Exception e) {
                predictions.add(new AssetPrediction(
                        asset.getCoin().getId(),
                        asset.getCoin().getName(),
                        asset.getCoin().getSymbol(),
                        asset.getQuantity(),
                        currentValue,
                        currentValue * 1.15,
                        15.0,
                        0.3,
                        (int) (weight * 100),
                        "Medium",
                        "Hold"
                ));
            }
        }

        predictions.sort(Comparator.comparingInt(AssetPrediction::getContributionToRank).reversed());
        return predictions;
    }

    private double[] calculateReturns(double[] prices) {
        double[] returns = new double[prices.length - 1];
        for (int i = 1; i < prices.length; i++) {
            returns[i - 1] = Math.log(prices[i] / prices[i - 1]);
        }
        return returns;
    }

    private Map<String, Double> calculateAssetAllocations(List<Asset> assets) {
        double totalValue = assets.stream()
                .mapToDouble(a -> a.getQuantity() * a.getCoin().getCurrentPrice())
                .sum();

        Map<String, Double> allocations = new HashMap<>();
        for (Asset asset : assets) {
            double value = asset.getQuantity() * asset.getCoin().getCurrentPrice();
            allocations.put(asset.getCoin().getId(), (value / totalValue) * 100);
        }
        return allocations;
    }

    private String getRiskLevel(double volatility) {
        if (volatility > 0.8) return "Very High";
        if (volatility > 0.5) return "High";
        if (volatility > 0.3) return "Medium";
        return "Low";
    }

    private String getRecommendation(double expectedReturn, double volatility, double weight) {
        if (expectedReturn > 30 && volatility < 0.4) return "Increase";
        if (expectedReturn < 5 || volatility > 0.8) return "Reduce";
        if (weight > 0.4) return "Reduce";
        return "Hold";
    }

    private String calculateThreatLevel(SimulationDTO current, SimulationDTO competitor) {
        if (competitor.getPredictedRank() < current.getPredictedRank() - 2) return "High";
        if (competitor.getPredictedRank() < current.getPredictedRank()) return "Medium";
        return "Low";
    }

    private PredictionExplanation createBeginnerExplanation() {
        return new PredictionExplanation(
                Arrays.asList("No active trading portfolio yet"),
                new ArrayList<>(),
                Arrays.asList("No investments to generate returns"),
                Arrays.asList("Start investing in diversified cryptocurrencies", "Begin with stable assets like BTC and ETH"),
                "You're just getting started! Begin building a portfolio to compete in rankings.",
                new HashMap<>()
        );
    }

    // Inner classes
    @Data
    @AllArgsConstructor
    private static class MonteCarloResult {
        private double expectedValue;
        private double worstCase;
        private double bestCase;
        private double confidenceLevel;
    }

    @Data
    @AllArgsConstructor
    private static class AssetVolatility {
        private double meanReturn;
        private double volatility;
        private double currentValue;
    }

    @Data
    @AllArgsConstructor
    private static class TradingBehavior {
        private double tradesPerMonth;
        private double winRate;
        private double avgHoldingDays;
        private double expectedAnnualGrowth;
    }

    @Data
    @AllArgsConstructor
    private static class PortfolioMetrics {
        private double diversificationScore;
        private double volatility;
        private double sharpeRatio;
        private int riskScore;
    }
}
