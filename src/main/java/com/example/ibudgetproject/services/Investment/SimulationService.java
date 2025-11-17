package com.example.ibudgetproject.services.Investment;

import com.example.ibudgetproject.DTO.Investment.SimulationDTO;
import java.util.List;
import java.util.Map;

public interface SimulationService {
    List<SimulationDTO> simulateOneYearForAllUsers();
    SimulationDTO simulateOneYearForUser(Long userId);
    Map<String, Object> getSimulationSummary();
}
