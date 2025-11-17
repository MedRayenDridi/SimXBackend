package com.example.ibudgetproject.services.Investment;

import com.example.ibudgetproject.repositories.Investment.AssetRepository;
import com.example.ibudgetproject.repositories.Investment.CoinRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.CMAESOptimizer;
import org.apache.commons.math3.optim.univariate.*;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ibudgetproject.entities.Investment.*;
import com.example.ibudgetproject.entities.User.User;
import com.example.ibudgetproject.services.Investment.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AssetServiceImpl implements AssetService {

    private static final int SIMULATIONS = 10000;
    private static final double CONFIDENCE_LEVEL = 0.95;

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private CoinRepository coinRepository;
    @Autowired
    private ObjectMapper objectMapper;





    // ✅ FIXED: Now accepts buyPrice as parameter
    @Override
    public Asset createAsset(User user, Coin coin, double quantity) {
        Asset asset = new Asset();
        asset.setUser(user);
        asset.setCoin(coin);
        asset.setQuantity(quantity);
        // ✅ CRITICAL FIX: Use current price as buy price at time of purchase
        asset.setBuyPrice(coin.getCurrentPrice());

        System.out.println("✓ Asset created: " + coin.getSymbol() + " | Qty: " + quantity + " | Buy Price: " + coin.getCurrentPrice());
        return assetRepository.save(asset);
    }

    @Override
    public Asset getAssetById(Long assetId) throws Exception {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new Exception("Asset not found"));
    }

    @Override
    public Asset getAssetByUserIdAndId(Long userId, Long assetId) {
        return null;
    }

    @Override
    public List<Asset> getUsersAssets(Long userId) {
        return assetRepository.findByUser_UserId(userId);
    }

    @Override
    public Asset updateAsset(Long assetId, double quantity) throws Exception {
        Asset oldAsset = getAssetById(assetId);

        // ✅ CRITICAL FIX: Calculate weighted average buy price when adding more
        if (quantity > 0) {
            double oldQuantity = oldAsset.getQuantity();
            double oldBuyPrice = oldAsset.getBuyPrice();
            double newBuyPrice = oldAsset.getCoin().getCurrentPrice();

            // Weighted average: (oldQty * oldPrice + newQty * newPrice) / (oldQty + newQty)
            double weightedAvgPrice = ((oldQuantity * oldBuyPrice) + (quantity * newBuyPrice)) / (oldQuantity + quantity);

            oldAsset.setBuyPrice(weightedAvgPrice);
            oldAsset.setQuantity(oldQuantity + quantity);

            System.out.println("✓ Asset updated: " + oldAsset.getCoin().getSymbol() +
                    " | New Qty: " + oldAsset.getQuantity() +
                    " | Weighted Avg Buy Price: " + weightedAvgPrice);
        } else {
            // ✅ Selling: Keep the same buy price, just reduce quantity
            oldAsset.setQuantity(oldAsset.getQuantity() + quantity); // quantity is negative for sells
            System.out.println("✓ Asset sold: " + oldAsset.getCoin().getSymbol() +
                    " | Remaining Qty: " + oldAsset.getQuantity() +
                    " | Buy Price unchanged: " + oldAsset.getBuyPrice());
        }

        return assetRepository.save(oldAsset);
    }

    @Override
    public Asset findAssetByUserIdAndCoinId(Long user_Id, String coinId) {
        return assetRepository.findByUser_UserIdAndCoinId(user_Id, coinId);
    }

    @Override
    public void deleteAsset(Long assetId) {
        assetRepository.deleteById(assetId);
    }

    @Override
    public double calculateRiskMonteCarlo(Long userId, int days) {
        List<Asset> assets = assetRepository.findByUser_UserId(userId);
        if (assets.isEmpty()) {
            throw new RuntimeException("Aucun actif trouvé pour l'utilisateur.");
        }

        double portfolioValue = assets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getCoin().getCurrentPrice())
                .sum();

        double[] logReturns = assets.stream()
                .mapToDouble(asset -> {
                    try {
                        return computeLogReturn(fetchHistoricalPrices(asset.getCoin().getId()));
                    } catch (Exception e) {
                        throw new RuntimeException("Erreur lors de la récupération des prix historiques", e);
                    }
                })
                .toArray();

        if (logReturns.length == 0) {
            throw new RuntimeException("Pas assez de données pour effectuer la simulation Monte Carlo.");
        }

        double[] simulatedValues = new double[SIMULATIONS];
        Random random = new Random();

        for (int i = 0; i < SIMULATIONS; i++) {
            double simulatedPortfolioValue = portfolioValue;
            for (int j = 0; j < days; j++) {
                simulatedPortfolioValue *= Math.exp(logReturns[random.nextInt(logReturns.length)]);
            }
            simulatedValues[i] = simulatedPortfolioValue;
        }

        Arrays.sort(simulatedValues);
        return portfolioValue - simulatedValues[(int) (SIMULATIONS * (1 - CONFIDENCE_LEVEL))];
    }

    @Override
    public double calculateAssetValue(Long assetId) throws Exception {
        Asset asset = getAssetById(assetId);
        double currentPrice = asset.getCoin().getCurrentPrice();
        return asset.getQuantity() * currentPrice;
    }

    @Override
    public double computeLogReturn(double[] historicalPrices) {
        if (historicalPrices.length < 2) {
            throw new RuntimeException("Pas assez de données historiques.");
        }
        double[] returns = new double[historicalPrices.length - 1];
        for (int i = 1; i < historicalPrices.length; i++) {
            returns[i - 1] = Math.log(historicalPrices[i] / historicalPrices[i - 1]);
        }
        return new Mean().evaluate(returns) + new StandardDeviation().evaluate(returns) * new Random().nextGaussian();
    }

    private double[] fetchHistoricalPrices(String coinId) throws Exception {
        String url = "https://api.coingecko.com/api/v3/coins/" + coinId + "/market_chart?vs_currency=usd&days=30";
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

    @Override
    public ResponseEntity<Map<String, Object>> optimizePortfolioMarkowitz(Long userId) {
        try {
            System.out.println("Optimisation du portefeuille pour userId: " + userId);

            List<Asset> assets = assetRepository.findByUser_UserId(userId);
            if (assets.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aucun actif trouvé pour cet utilisateur"));
            }

            int n = assets.size();
            if (n == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Pas d'actifs à optimiser"));
            }

            double[][] returns = new double[n][];
            for (int i = 0; i < n; i++) {
                returns[i] = fetchHistoricalReturns(assets.get(i).getCoin().getId());
                if (returns[i].length == 0) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("message", "Pas assez de données pour " + assets.get(i).getCoin().getId()));
                }
            }

            int minLength = Arrays.stream(returns).mapToInt(r -> r.length).min().orElse(0);
            if (minLength == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Données insuffisantes pour calculer les rendements"));
            }

            for (int i = 0; i < n; i++) {
                returns[i] = Arrays.copyOf(returns[i], minLength);
            }

            double[] meanReturns = Arrays.stream(returns)
                    .mapToDouble(r -> new Mean().evaluate(r)).toArray();
            RealMatrix covarianceMatrix = new Covariance(returns).getCovarianceMatrix();

            for (int i = 0; i < covarianceMatrix.getRowDimension(); i++) {
                covarianceMatrix.addToEntry(i, i, 1e-10);
            }

            CMAESOptimizer optimizer = new CMAESOptimizer(
                    10000, 1e-9, true, 10, 0, new MersenneTwister(), false, null
            );

            ObjectiveFunction objectiveFunction = new ObjectiveFunction(weights ->
                    -computeSharpeRatio(weights, meanReturns, covarianceMatrix)
            );

            double[] lowerBound = new double[n];
            double[] upperBound = new double[n];
            double[] initialGuessArray = new double[n];

            Arrays.fill(lowerBound, 0.01);
            Arrays.fill(upperBound, 1.0);
            Arrays.fill(initialGuessArray, 1.0 / n);

            SimpleBounds bounds = new SimpleBounds(lowerBound, upperBound);
            InitialGuess initialGuess = new InitialGuess(initialGuessArray);

            PointValuePair result = optimizer.optimize(
                    new MaxEval(10000),
                    objectiveFunction,
                    GoalType.MAXIMIZE,
                    bounds,
                    initialGuess
            );

            double[] optimizedWeights = result.getPoint();
            double sum = Arrays.stream(optimizedWeights).sum();
            for (int i = 0; i < optimizedWeights.length; i++) {
                optimizedWeights[i] /= sum;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("optimizedWeights", optimizedWeights);
            response.put("sharpeRatio", -result.getValue());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private double[] fetchHistoricalReturns(String coinId) {
        try {
            double[] prices = fetchHistoricalPrices(coinId);
            if (prices.length < 2) throw new RuntimeException("Pas assez de données pour calculer les rendements");

            double[] returns = new double[prices.length - 1];
            for (int i = 1; i < prices.length; i++) {
                returns[i - 1] = Math.log(prices[i] / prices[i - 1]);
            }
            return returns;
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la récupération des rendements historiques", e);
        }
    }

    private double computeSharpeRatio(double[] weights, double[] returns, RealMatrix covMatrix) {
        double portfolioReturn = 0;
        for (int i = 0; i < weights.length; i++) {
            portfolioReturn += weights[i] * returns[i];
        }

        RealVector w = new ArrayRealVector(weights);
        double variance = w.dotProduct(covMatrix.operate(w));
        if (variance < 0) throw new RuntimeException("Variance négative détectée, problème dans la matrice de covariance.");

        double portfolioRisk = Math.sqrt(variance);
        return portfolioReturn / portfolioRisk;
    }

    @Override
    public Map<String, Double> calculatePortfolioPerformance(Long userId, int days) {
        List<Asset> assets = assetRepository.findByUser_UserId(userId);

        if (assets.isEmpty()) {
            throw new RuntimeException("Aucun actif trouvé pour l'utilisateur.");
        }

        double[] portfolioReturns = assets.stream()
                .mapToDouble(asset -> {
                    try {
                        double[] historicalPrices = fetchHistoricalPrices(asset.getCoin().getId(), days);
                        return computeReturn(historicalPrices);
                    } catch (Exception e) {
                        throw new RuntimeException("Erreur lors de la récupération des prix historiques", e);
                    }
                })
                .toArray();

        double meanReturn = new Mean().evaluate(portfolioReturns);
        double volatility = new StandardDeviation().evaluate(portfolioReturns);

        double monthlyMeanReturn = meanReturn * (30.0 / days);
        double monthlyVolatility = volatility * Math.sqrt(30.0 / days);

        Map<String, Double> performance = new HashMap<>();
        performance.put("monthlyMeanReturn", monthlyMeanReturn);
        performance.put("monthlyVolatility", monthlyVolatility);

        return performance;
    }

    private double computeReturn(double[] historicalPrices) {
        if (historicalPrices.length < 2) {
            return 0;
        }
        double[] returns = new double[historicalPrices.length - 1];
        for (int i = 1; i < historicalPrices.length; i++) {
            returns[i - 1] = Math.log(historicalPrices[i] / historicalPrices[i - 1]);
        }
        return new Mean().evaluate(returns);
    }

    private double[] fetchHistoricalPrices(String coinId, int days) throws Exception {
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
}
