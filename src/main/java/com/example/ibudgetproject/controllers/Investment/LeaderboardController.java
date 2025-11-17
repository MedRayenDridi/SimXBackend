package com.example.ibudgetproject.controllers.Investment;

import com.example.ibudgetproject.DTO.Investment.LeaderboardDTO;
import com.example.ibudgetproject.entities.User.User;
import com.example.ibudgetproject.services.Investment.LeaderboardService;
import com.example.ibudgetproject.services.User.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private UserService userService;

    @GetMapping("/global")
    public ResponseEntity<List<LeaderboardDTO>> getGlobalLeaderboard(
            @RequestParam(defaultValue = "0") int limit) {
        try {
            List<LeaderboardDTO> leaderboard = leaderboardService.getGlobalLeaderboard(limit);
            return ResponseEntity.ok(leaderboard);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user")
    public ResponseEntity<LeaderboardDTO> getCurrentUserStats(
            @RequestHeader("Authorization") String jwt) {
        try {
            User user = userService.findUserProfileByJwt(jwt);
            LeaderboardDTO stats = leaderboardService.getUserRankAndStats(user.getUserId());
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<LeaderboardDTO> getUserStats(@PathVariable Long userId) {
        try {
            LeaderboardDTO stats = leaderboardService.getUserRankAndStats(userId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<Map<String, List<LeaderboardDTO>>> getCategoryLeaderboards() {
        try {
            Map<String, List<LeaderboardDTO>> categories = leaderboardService.getCategoryLeaderboards();
            return ResponseEntity.ok(categories);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user/{userId}/detailed")
    public ResponseEntity<Map<String, Object>> getUserDetailedStats(@PathVariable Long userId) {
        try {
            Map<String, Object> detailedStats = leaderboardService.getUserDetailedStats(userId);
            return ResponseEntity.ok(detailedStats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
