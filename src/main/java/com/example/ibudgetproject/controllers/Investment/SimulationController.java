package com.example.ibudgetproject.controllers.Investment;

import com.example.ibudgetproject.DTO.Investment.SimulationDTO;
import com.example.ibudgetproject.entities.User.User;
import com.example.ibudgetproject.services.Investment.SimulationService;
import com.example.ibudgetproject.services.User.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationController {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private UserService userService;

    @PostMapping("/predict-year")
    public ResponseEntity<List<SimulationDTO>> predictOneYear() {
        try {
            System.out.println("🔮 Starting 1-year prediction simulation...");
            List<SimulationDTO> predictions = simulationService.simulateOneYearForAllUsers();
            System.out.println("✅ Simulation complete!");
            return ResponseEntity.ok(predictions);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user")
    public ResponseEntity<SimulationDTO> getCurrentUserPrediction(
            @RequestHeader("Authorization") String jwt) {
        try {
            User user = userService.findUserProfileByJwt(jwt);
            SimulationDTO prediction = simulationService.simulateOneYearForUser(user.getUserId());
            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<SimulationDTO> getUserPrediction(@PathVariable Long userId) {
        try {
            SimulationDTO prediction = simulationService.simulateOneYearForUser(userId);
            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSimulationSummary() {
        try {
            Map<String, Object> summary = simulationService.getSimulationSummary();
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
