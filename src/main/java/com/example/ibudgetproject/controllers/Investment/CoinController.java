package com.example.ibudgetproject.controllers.Investment;

import com.example.ibudgetproject.entities.Investment.Coin;
import com.example.ibudgetproject.services.Investment.CoinService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/coins")
public class CoinController {
    @Autowired
    private CoinService coinService;
    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping
    ResponseEntity<List<Coin>> getCoinList(@RequestParam("page") int page) throws Exception {
        List<Coin> coins = coinService.getCoinList(page);
        return new ResponseEntity<>(coins, HttpStatus.ACCEPTED);
    }

    @GetMapping("/{coinId}/chart")
    ResponseEntity<JsonNode> getMarketChart(
            @PathVariable String coinId,
            @RequestParam(value = "interval", defaultValue = "1h") String interval,
            @RequestParam(value = "limit", defaultValue = "500") int limit) throws Exception {
        String res = coinService.getMarketChart(coinId, interval, limit);
        JsonNode jsonNode = objectMapper.readTree(res);
        return new ResponseEntity<>(jsonNode, HttpStatus.ACCEPTED);
    }

    @GetMapping("/search")
    ResponseEntity<JsonNode> searchCoin(@RequestParam("q") String keyword) throws Exception {
        String coin = coinService.searchCoin(keyword);
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }

    @GetMapping("/top50")
    ResponseEntity<JsonNode> getTop50CoinsByMarketCapRank() throws Exception {
        String coin = coinService.getTop50CoinsByMarketCapRank();
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }

    @GetMapping("/trading")
    ResponseEntity<JsonNode> GetTreadingCoins() throws Exception {
        String coin = coinService.GetTreadingCoins();
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }

    @GetMapping("/details/{coinId}")
    ResponseEntity<JsonNode> getCoinDetails(@PathVariable String coinId) throws Exception {
        String coin = coinService.getCoinDetails(coinId);
        JsonNode jsonNode = objectMapper.readTree(coin);
        return ResponseEntity.ok(jsonNode);
    }
}
