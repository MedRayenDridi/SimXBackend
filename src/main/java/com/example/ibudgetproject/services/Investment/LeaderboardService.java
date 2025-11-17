package com.example.ibudgetproject.services.Investment;

import com.example.ibudgetproject.DTO.Investment.LeaderboardDTO;
import java.util.List;
import java.util.Map;

public interface LeaderboardService {
    List<LeaderboardDTO> getGlobalLeaderboard(int limit);
    LeaderboardDTO getUserRankAndStats(Long userId);
    Map<String, List<LeaderboardDTO>> getCategoryLeaderboards();
    Map<String, Object> getUserDetailedStats(Long userId);
}
