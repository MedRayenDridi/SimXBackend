package com.example.ibudgetproject.services.Investment;

import com.example.ibudgetproject.DTO.Investment.LeaderboardDTO;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Override
    public List<LeaderboardDTO> getGlobalLeaderboard(int limit) {
        List<User> allUsers = userRepository.findAll();

        System.out.println("📊 Total users in app: " + allUsers.size());

        List<LeaderboardDTO> leaderboard = allUsers.stream()
                .map(this::calculateUserStats)
                .collect(Collectors.toList());

        leaderboard.sort(Comparator
                .comparingDouble(LeaderboardDTO::getReturnPercentage)
                .reversed()
                .thenComparingDouble(LeaderboardDTO::getPortfolioValue)
                .reversed()
                .thenComparingInt(LeaderboardDTO::getTotalTrades)
                .reversed());

        for (int i = 0; i < leaderboard.size(); i++) {
            LeaderboardDTO dto = leaderboard.get(i);
            dto.setRank(i + 1);
            dto.setPercentile(calculatePercentile(i + 1, leaderboard.size()));
        }

        if (limit > 0 && limit < leaderboard.size()) {
            return leaderboard.stream().limit(limit).collect(Collectors.toList());
        }

        return leaderboard;
    }

    @Override
    public LeaderboardDTO getUserRankAndStats(Long userId) {
        List<LeaderboardDTO> globalLeaderboard = getGlobalLeaderboard(0);

        return globalLeaderboard.stream()
                .filter(dto -> dto.getUserId().equals(userId))
                .findFirst()
                .orElseGet(() -> {
                    User user = userRepository.findById(userId).orElseThrow();
                    LeaderboardDTO dto = calculateUserStats(user);
                    dto.setRank(globalLeaderboard.size() + 1);
                    dto.setPercentile(0);
                    return dto;
                });
    }

    @Override
    public Map<String, List<LeaderboardDTO>> getCategoryLeaderboards() {
        List<LeaderboardDTO> allTraders = getGlobalLeaderboard(0);

        Map<String, List<LeaderboardDTO>> categories = new HashMap<>();

        categories.put("dayTraders", allTraders.stream()
                .filter(dto -> dto.getTotalTrades() > 0)
                .sorted(Comparator.comparingInt(LeaderboardDTO::getTotalTrades).reversed())
                .limit(20)
                .collect(Collectors.toList()));

        categories.put("swingTraders", allTraders.stream()
                .filter(dto -> dto.getTotalTrades() >= 5 && dto.getTotalTrades() <= 100)
                .sorted(Comparator.comparingDouble(LeaderboardDTO::getReturnPercentage).reversed())
                .limit(20)
                .collect(Collectors.toList()));

        categories.put("consistent", allTraders.stream()
                .filter(dto -> dto.getTotalTrades() >= 10)
                .sorted(Comparator.comparingDouble(LeaderboardDTO::getWinRate).reversed())
                .limit(20)
                .collect(Collectors.toList()));

        categories.put("cryptoExperts", allTraders.stream()
                .filter(dto -> dto.getTotalTrades() > 0)
                .sorted(Comparator.comparingDouble(LeaderboardDTO::getReturnPercentage).reversed())
                .limit(20)
                .collect(Collectors.toList()));

        categories.put("biggestPortfolios", allTraders.stream()
                .filter(dto -> dto.getPortfolioValue() > 0)
                .sorted(Comparator.comparingDouble(LeaderboardDTO::getPortfolioValue).reversed())
                .limit(20)
                .collect(Collectors.toList()));

        categories.put("risingStars", allTraders.stream()
                .filter(dto -> dto.getTotalTrades() > 0 && dto.getTotalTrades() < 50 && dto.getReturnPercentage() > 0)
                .sorted(Comparator.comparingDouble(LeaderboardDTO::getReturnPercentage).reversed())
                .limit(20)
                .collect(Collectors.toList()));

        return categories;
    }

    @Override
    public Map<String, Object> getUserDetailedStats(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        LeaderboardDTO stats = getUserRankAndStats(userId);

        List<Order> recentOrders = orderRepository.findByUser_UserId(userId).stream()
                .filter(o -> o.getStatus() == OrderStatus.SUCCESS)
                .sorted(Comparator.comparing(Order::getTimestamp).reversed())
                .limit(10)
                .collect(Collectors.toList());

        List<LeaderboardDTO> allTraders = getGlobalLeaderboard(0);
        int userRank = stats.getRank();

        List<LeaderboardDTO> nearbyTraders = allTraders.stream()
                .filter(dto -> Math.abs(dto.getRank() - userRank) <= 5)
                .collect(Collectors.toList());

        Map<String, Object> detailedStats = new HashMap<>();
        detailedStats.put("userStats", stats);
        detailedStats.put("recentTrades", recentOrders);
        detailedStats.put("nearbyTraders", nearbyTraders);
        detailedStats.put("totalCompetitors", allTraders.size());
        detailedStats.put("monthlyPerformance", calculateMonthlyPerformance(userId));

        return detailedStats;
    }

    private LeaderboardDTO calculateUserStats(User user) {
        LeaderboardDTO dto = new LeaderboardDTO();

        dto.setUserId(user.getUserId());
        dto.setUsername(user.getEmail().split("@")[0]);
        dto.setFullName(user.fullName() != null && !user.fullName().trim().isEmpty()
                ? user.fullName()
                : user.getEmail().split("@")[0]);

        List<Asset> assets = assetRepository.findByUser_UserId(user.getUserId());
        double portfolioValue = assets.stream()
                .mapToDouble(asset -> asset.getQuantity() * asset.getCoin().getCurrentPrice())
                .sum();

        Wallet wallet = walletRepository.findByUser_UserId(user.getUserId());
        double walletBalance = wallet != null ? wallet.getBalance().doubleValue() : 0;

        double totalValue = portfolioValue + walletBalance;
        dto.setPortfolioValue(totalValue);

        List<Order> orders = orderRepository.findByUser_UserId(user.getUserId());

        double totalBuyAmount = orders.stream()
                .filter(o -> o.getOrderType() == OrderType.BUY && o.getStatus() == OrderStatus.SUCCESS)
                .mapToDouble(o -> o.getPrice().doubleValue())
                .sum();

        double totalSellAmount = orders.stream()
                .filter(o -> o.getOrderType() == OrderType.SELL && o.getStatus() == OrderStatus.SUCCESS)
                .mapToDouble(o -> o.getPrice().doubleValue())
                .sum();

        double netInvestment = totalBuyAmount - totalSellAmount;
        dto.setInitialInvestment(netInvestment);

        double totalProfit = totalValue - netInvestment;
        dto.setTotalProfit(totalProfit);
        dto.setTotalReturn(totalProfit);

        if (netInvestment > 0) {
            dto.setReturnPercentage((totalProfit / netInvestment) * 100);
        } else if (totalSellAmount > 0 && totalBuyAmount > 0) {
            double realizedGains = totalSellAmount - totalBuyAmount;
            dto.setReturnPercentage(totalBuyAmount > 0 ? (realizedGains / totalBuyAmount) * 100 : 0);
        } else {
            dto.setReturnPercentage(0);
        }

        List<Order> successfulOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.SUCCESS)
                .collect(Collectors.toList());

        dto.setTotalTrades(successfulOrders.size());

        List<Order> sellOrders = successfulOrders.stream()
                .filter(o -> o.getOrderType() == OrderType.SELL)
                .collect(Collectors.toList());

        long profitableSells = sellOrders.stream()
                .filter(this::isOrderProfitable)
                .count();

        double winRate = sellOrders.size() > 0
                ? (profitableSells * 100.0 / sellOrders.size())
                : (dto.getTotalTrades() > 0 && totalProfit > 0 ? 50.0 : 0.0);

        dto.setWinRate(winRate);
        dto.setBadge(determineBadge(dto));

        return dto;
    }

    private boolean isOrderProfitable(Order sellOrder) {
        if (sellOrder.getOrderType() != OrderType.SELL || sellOrder.getOrderItem() == null) {
            return false;
        }

        double sellPrice = sellOrder.getOrderItem().getSellPrice();
        double buyPrice = sellOrder.getOrderItem().getBuyPrice();

        return sellPrice > buyPrice;
    }

    private String determineBadge(LeaderboardDTO dto) {
        if (dto.getTotalTrades() == 0) {
            return "Beginner";
        }

        if (dto.getTotalTrades() > 200 && dto.getReturnPercentage() > 5) {
            return "Elite Day Trader";
        } else if (dto.getTotalTrades() > 200) {
            return "Day Trader";
        }

        if (dto.getWinRate() > 80 && dto.getTotalTrades() > 30) {
            return "Master Trader";
        } else if (dto.getWinRate() > 70 && dto.getTotalTrades() > 20) {
            return "Consistent Trader";
        }

        if (dto.getReturnPercentage() > 50) {
            return "Crypto Legend";
        } else if (dto.getReturnPercentage() > 20) {
            return "Crypto Expert";
        }

        if (dto.getTotalTrades() >= 50 && dto.getTotalTrades() <= 150) {
            return "Swing Trader";
        }

        if (dto.getTotalTrades() > 10 && dto.getReturnPercentage() > 0) {
            return "Active Trader";
        }

        if (dto.getTotalTrades() > 0 && dto.getReturnPercentage() > 0) {
            return "Rising Star";
        }

        return "Beginner";
    }

    private int calculatePercentile(int rank, int totalUsers) {
        if (totalUsers == 0) return 0;
        return Math.max(1, (int) Math.round((1 - (rank - 1.0) / totalUsers) * 100));
    }

    private Map<String, Double> calculateMonthlyPerformance(Long userId) {
        LeaderboardDTO stats = calculateUserStats(userRepository.findById(userId).orElseThrow());

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Order> recentOrders = orderRepository.findByUser_UserId(userId).stream()
                .filter(o -> o.getTimestamp().isAfter(thirtyDaysAgo))
                .filter(o -> o.getStatus() == OrderStatus.SUCCESS)
                .collect(Collectors.toList());

        Map<String, Double> monthlyPerf = new HashMap<>();
        monthlyPerf.put("return", stats.getReturnPercentage());
        monthlyPerf.put("profit", stats.getTotalProfit());
        monthlyPerf.put("trades", (double) recentOrders.size());
        monthlyPerf.put("averageTradeSize", recentOrders.isEmpty() ? 0.0 :
                recentOrders.stream().mapToDouble(o -> o.getPrice().doubleValue()).average().orElse(0));

        return monthlyPerf;
    }
}
