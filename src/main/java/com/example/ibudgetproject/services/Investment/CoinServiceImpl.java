package com.example.ibudgetproject.services.Investment;

import com.example.ibudgetproject.entities.Investment.Coin;
import com.example.ibudgetproject.repositories.Investment.CoinRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CoinServiceImpl implements CoinService {
    @Autowired
    private CoinRepository coinRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private static final Map<String, String> SYMBOL_TO_COINGECKO_ID = new HashMap<>();

    static {
        SYMBOL_TO_COINGECKO_ID.put("btc", "1/large/bitcoin");
        SYMBOL_TO_COINGECKO_ID.put("eth", "279/large/ethereum");
        SYMBOL_TO_COINGECKO_ID.put("usdt", "325/large/Tether");
        SYMBOL_TO_COINGECKO_ID.put("bnb", "825/large/bnb-icon2_2x");
        SYMBOL_TO_COINGECKO_ID.put("sol", "4128/large/solana");
        SYMBOL_TO_COINGECKO_ID.put("xrp", "44/large/xrp-symbol-white-128");
        SYMBOL_TO_COINGECKO_ID.put("usdc", "6319/large/usdc");
        SYMBOL_TO_COINGECKO_ID.put("ada", "975/large/cardano");
        SYMBOL_TO_COINGECKO_ID.put("doge", "5/large/dogecoin");
        SYMBOL_TO_COINGECKO_ID.put("trx", "1094/large/tron-logo");
        SYMBOL_TO_COINGECKO_ID.put("link", "1597/large/chainlink");
        SYMBOL_TO_COINGECKO_ID.put("dot", "6636/large/polkadot-new-dot-logo");
        SYMBOL_TO_COINGECKO_ID.put("matic", "4713/large/polygon");
        SYMBOL_TO_COINGECKO_ID.put("ltc", "2/large/litecoin");
        SYMBOL_TO_COINGECKO_ID.put("avax", "12559/large/avalanche-logo");
        SYMBOL_TO_COINGECKO_ID.put("dai", "9956/large/multi-collateral-dai");
        SYMBOL_TO_COINGECKO_ID.put("uni", "12504/large/uni");
        SYMBOL_TO_COINGECKO_ID.put("atom", "3794/large/cosmos_hub");
        SYMBOL_TO_COINGECKO_ID.put("etc", "1321/large/ethereum-classic-logo");
        SYMBOL_TO_COINGECKO_ID.put("xlm", "512/large/stellar");
        SYMBOL_TO_COINGECKO_ID.put("bch", "3/large/bitcoin-cash");
        SYMBOL_TO_COINGECKO_ID.put("icp", "8916/large/icp_logo");
        SYMBOL_TO_COINGECKO_ID.put("fil", "5784/large/filecoin");
        SYMBOL_TO_COINGECKO_ID.put("apt", "26455/large/aptos");
        SYMBOL_TO_COINGECKO_ID.put("arb", "11841/large/arbitrum");
        SYMBOL_TO_COINGECKO_ID.put("near", "6535/large/near_icon");
        SYMBOL_TO_COINGECKO_ID.put("aave", "7278/large/aave-token");
        SYMBOL_TO_COINGECKO_ID.put("grt", "6719/large/the-graph");
        SYMBOL_TO_COINGECKO_ID.put("pepe", "29850/large/pepe");
        SYMBOL_TO_COINGECKO_ID.put("sui", "28328/large/sui");
        SYMBOL_TO_COINGECKO_ID.put("hbar", "4642/large/hbar");
        SYMBOL_TO_COINGECKO_ID.put("op", "11840/large/optimism");
        SYMBOL_TO_COINGECKO_ID.put("imx", "17233/large/immutable-x");
        SYMBOL_TO_COINGECKO_ID.put("inj", "7226/large/injective-logo");
        SYMBOL_TO_COINGECKO_ID.put("mkr", "1518/large/maker");
        SYMBOL_TO_COINGECKO_ID.put("wld", "31069/large/worldcoin");
        SYMBOL_TO_COINGECKO_ID.put("rune", "6892/large/thorchain");
        SYMBOL_TO_COINGECKO_ID.put("ftm", "3513/large/fantom");
        SYMBOL_TO_COINGECKO_ID.put("cro", "3635/large/crypto-com-coin");
        SYMBOL_TO_COINGECKO_ID.put("algo", "4030/large/algorand");
        SYMBOL_TO_COINGECKO_ID.put("qnt", "3155/large/0_xs8UIxm_400x400");
        SYMBOL_TO_COINGECKO_ID.put("ldo", "13573/large/lido-dao");
        SYMBOL_TO_COINGECKO_ID.put("vet", "3077/large/vechain");
        SYMBOL_TO_COINGECKO_ID.put("sand", "12129/large/sand");
        SYMBOL_TO_COINGECKO_ID.put("mana", "1966/large/decentraland");
        SYMBOL_TO_COINGECKO_ID.put("axs", "6783/large/axie-infinity");
        SYMBOL_TO_COINGECKO_ID.put("stx", "4847/large/stacks");
        SYMBOL_TO_COINGECKO_ID.put("xmr", "69/large/monero");
        SYMBOL_TO_COINGECKO_ID.put("eos", "1765/large/eos");
        SYMBOL_TO_COINGECKO_ID.put("xtz", "2375/large/tezos");
        SYMBOL_TO_COINGECKO_ID.put("bsv", "3602/large/bitcoin-cash-sv");
        SYMBOL_TO_COINGECKO_ID.put("neo", "480/large/neo");
        SYMBOL_TO_COINGECKO_ID.put("waves", "307/large/waves");
        SYMBOL_TO_COINGECKO_ID.put("dash", "19/large/dash");
        SYMBOL_TO_COINGECKO_ID.put("zec", "486/large/zcash");
        SYMBOL_TO_COINGECKO_ID.put("ksm", "5034/large/kusama");
        SYMBOL_TO_COINGECKO_ID.put("theta", "2416/large/theta-logo");
        SYMBOL_TO_COINGECKO_ID.put("fdusd", "33695/large/fdusd");
        SYMBOL_TO_COINGECKO_ID.put("usde", "33650/large/ethena-usde");
        SYMBOL_TO_COINGECKO_ID.put("wif", "33566/large/dogwifcoin");
        SYMBOL_TO_COINGECKO_ID.put("fet", "5681/large/fetch");
        SYMBOL_TO_COINGECKO_ID.put("tao", "32942/large/bittensor");
        SYMBOL_TO_COINGECKO_ID.put("ena", "33651/large/ethena");
        SYMBOL_TO_COINGECKO_ID.put("pengu", "35321/large/pudgy-penguins");
        SYMBOL_TO_COINGECKO_ID.put("trump", "35326/large/official-trump");
        SYMBOL_TO_COINGECKO_ID.put("render", "5690/large/render-token");
        SYMBOL_TO_COINGECKO_ID.put("jasmy", "8425/large/jasmy");
        SYMBOL_TO_COINGECKO_ID.put("bonk", "28782/large/bonk");
    }

    private String getCoinLogoUrl(String symbol) {
        String sym = symbol.toLowerCase();
        String imageId = SYMBOL_TO_COINGECKO_ID.getOrDefault(sym, "1/large/bitcoin");
        return "https://assets.coingecko.com/coins/images/" + imageId + ".png";
    }

    // Cache coin list for 60 seconds
    @Cacheable(value = "coinList", key = "#page")
    @Override
    public List<Coin> getCoinList(int page) throws Exception {
        String url = "https://api.binance.com/api/v3/ticker/24hr";

        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode dataArray = objectMapper.readTree(response.getBody());
            List<Coin> allCoins = new ArrayList<>();

            for (JsonNode node : dataArray) {
                String symbol = node.get("symbol").asText();

                if (symbol.endsWith("USDT") && !symbol.startsWith("USDT")) {
                    Coin coin = new Coin();
                    String baseSymbol = symbol.replace("USDT", "").toLowerCase();

                    coin.setId(baseSymbol);
                    coin.setSymbol(baseSymbol);
                    coin.setName(baseSymbol.toUpperCase());
                    coin.setImage(getCoinLogoUrl(baseSymbol));
                    coin.setCurrentPrice(node.get("lastPrice").asDouble());
                    coin.setTotalVolume((long) node.get("quoteVolume").asDouble());
                    coin.setPriceChangePercentage24h(node.get("priceChangePercent").asDouble());
                    coin.setPriceChange24h(node.get("priceChange").asDouble());
                    coin.setHigh24h(node.get("highPrice").asDouble());
                    coin.setLow24h(node.get("lowPrice").asDouble());
                    coin.setMarketCap(coin.getTotalVolume() * 100);
                    coin.setMarketCapChange24h(0L);
                    coin.setMarketCapChangePercentage24h(0L);
                    coin.setTotalSupply(0L);

                    allCoins.add(coin);
                }
            }

            allCoins.sort((a, b) -> Long.compare(b.getTotalVolume(), a.getTotalVolume()));

            for (int i = 0; i < allCoins.size(); i++) {
                allCoins.get(i).setMarketCapRank(i + 1);
            }

            int start = (page - 1) * 10;
            int end = Math.min(start + 10, allCoins.size());

            List<Coin> coinList = new ArrayList<>();
            if (start < allCoins.size()) {
                coinList = allCoins.subList(start, end);
            }

            return coinList;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new Exception(e.getMessage());
        }
    }

    // Cache market chart for 5 minutes (longer because charts don't change as often)
    @Cacheable(value = "marketChart", key = "#coinId + '_' + #days")
    @Override
    public String getMarketChart(String coinId, int days) throws Exception {
        String symbol = coinId.toUpperCase() + "USDT";
        String interval = days <= 1 ? "1h" : "1d";
        String url = "https://api.binance.com/api/v3/klines?symbol=" + symbol + "&interval=" + interval + "&limit=" + (days * 24);

        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode klines = objectMapper.readTree(response.getBody());
            Map<String, List<List<Double>>> result = new HashMap<>();
            List<List<Double>> prices = new ArrayList<>();

            for (JsonNode kline : klines) {
                List<Double> point = new ArrayList<>();
                point.add(kline.get(0).asDouble());
                point.add(kline.get(4).asDouble());
                prices.add(point);
            }

            result.put("prices", prices);
            return objectMapper.writeValueAsString(result);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new Exception(e.getMessage());
        }
    }

    // Cache coin details for 30 seconds
    @Cacheable(value = "coinDetails", key = "#coinId")
    @Override
    public String getCoinDetails(String coinId) throws Exception {
        String symbol = coinId.toUpperCase() + "USDT";
        String url = "https://api.binance.com/api/v3/ticker/24hr?symbol=" + symbol;

        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode node = objectMapper.readTree(response.getBody());

            Coin coin = new Coin();
            String baseSymbol = coinId.toLowerCase();

            coin.setId(baseSymbol);
            coin.setName(coinId.toUpperCase());
            coin.setSymbol(baseSymbol);
            coin.setImage(getCoinLogoUrl(baseSymbol));
            coin.setCurrentPrice(node.get("lastPrice").asDouble());
            coin.setTotalVolume((long) node.get("quoteVolume").asDouble());
            coin.setPriceChangePercentage24h(node.get("priceChangePercent").asDouble());
            coin.setPriceChange24h(node.get("priceChange").asDouble());
            coin.setHigh24h(node.get("highPrice").asDouble());
            coin.setLow24h(node.get("lowPrice").asDouble());
            coin.setMarketCap(coin.getTotalVolume() * 100);
            coin.setMarketCapRank(0);
            coin.setMarketCapChange24h(0L);
            coin.setMarketCapChangePercentage24h(0L);
            coin.setTotalSupply(0L);

            coinRepository.save(coin);
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public Coin findById(String coinId) throws Exception {
        // First, try to find in database
        Optional<Coin> optionalCoin = coinRepository.findById(coinId);

        if (optionalCoin.isPresent()) {
            System.out.println("✓ Coin found in database: " + coinId);
            return optionalCoin.get();
        }

        // If not found in DB, fetch from Binance API and save it
        System.out.println("⚠ Coin not found in DB, fetching from Binance API: " + coinId);

        String symbol = coinId.toUpperCase() + "USDT";
        String url = "https://api.binance.com/api/v3/ticker/24hr?symbol=" + symbol;

        RestTemplate restTemplate = new RestTemplate();

        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode node = objectMapper.readTree(response.getBody());

            Coin coin = new Coin();
            String baseSymbol = coinId.toLowerCase();

            coin.setId(baseSymbol);
            coin.setName(coinId.toUpperCase());
            coin.setSymbol(baseSymbol);
            coin.setImage(getCoinLogoUrl(baseSymbol));
            coin.setCurrentPrice(node.get("lastPrice").asDouble());
            coin.setTotalVolume((long) node.get("quoteVolume").asDouble());
            coin.setPriceChangePercentage24h(node.get("priceChangePercent").asDouble());
            coin.setPriceChange24h(node.get("priceChange").asDouble());
            coin.setHigh24h(node.get("highPrice").asDouble());
            coin.setLow24h(node.get("lowPrice").asDouble());
            coin.setMarketCap(coin.getTotalVolume() * 100);
            coin.setMarketCapRank(0);
            coin.setMarketCapChange24h(0L);
            coin.setMarketCapChangePercentage24h(0L);
            coin.setTotalSupply(0L);

            // Save to database for future use
            Coin savedCoin = coinRepository.save(coin);
            System.out.println("✓ Coin saved to database: " + savedCoin.getId());

            return savedCoin;

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            System.err.println("✗ Error fetching coin from Binance API: " + e.getMessage());
            throw new Exception("Coin not found with id: " + coinId + ". The coin symbol may not exist on Binance.");
        } catch (Exception e) {
            System.err.println("✗ Unexpected error: " + e.getMessage());
            throw new Exception("Error processing coin: " + coinId);
        }
    }


    @Override
    public String searchCoin(String keyword) throws Exception {
        String url = "https://api.binance.com/api/v3/ticker/24hr";
        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode dataArray = objectMapper.readTree(response.getBody());
            List<Map<String, String>> results = new ArrayList<>();

            for (JsonNode node : dataArray) {
                String symbol = node.get("symbol").asText();
                if (symbol.toLowerCase().contains(keyword.toLowerCase()) && symbol.endsWith("USDT")) {
                    Map<String, String> coin = new HashMap<>();
                    String baseSymbol = symbol.replace("USDT", "");
                    coin.put("id", baseSymbol.toLowerCase());
                    coin.put("symbol", baseSymbol);
                    coin.put("name", baseSymbol);
                    results.add(coin);
                }
            }

            Map<String, Object> searchResult = new HashMap<>();
            searchResult.put("coins", results);
            return objectMapper.writeValueAsString(searchResult);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new Exception(e.getMessage());
        }
    }

    // Cache top 50 for 60 seconds
    @Cacheable(value = "top50Coins")
    @Override
    public String getTop50CoinsByMarketCapRank() throws Exception {
        List<Coin> coins = getCoinList(1);
        for (int i = 2; i <= 5; i++) {
            coins.addAll(getCoinList(i));
        }
        List<Coin> top50 = coins.subList(0, Math.min(50, coins.size()));
        return objectMapper.writeValueAsString(top50);
    }

    // Cache trending coins for 60 seconds
    @Cacheable(value = "trendingCoins")
    @Override
    public String GetTreadingCoins() throws Exception {
        String url = "https://api.binance.com/api/v3/ticker/24hr";

        RestTemplate restTemplate = new RestTemplate();
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>("parameters", headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode dataArray = objectMapper.readTree(response.getBody());
            List<Coin> trendingCoins = new ArrayList<>();

            for (JsonNode node : dataArray) {
                String symbol = node.get("symbol").asText();
                if (symbol.endsWith("USDT") && !symbol.startsWith("USDT")) {
                    Coin coin = new Coin();
                    String baseSymbol = symbol.replace("USDT", "").toLowerCase();

                    coin.setId(baseSymbol);
                    coin.setSymbol(baseSymbol);
                    coin.setName(baseSymbol.toUpperCase());
                    coin.setImage(getCoinLogoUrl(baseSymbol));
                    coin.setCurrentPrice(node.get("lastPrice").asDouble());
                    coin.setTotalVolume((long) node.get("quoteVolume").asDouble());
                    coin.setPriceChangePercentage24h(node.get("priceChangePercent").asDouble());
                    coin.setPriceChange24h(node.get("priceChange").asDouble());
                    coin.setHigh24h(node.get("highPrice").asDouble());
                    coin.setLow24h(node.get("lowPrice").asDouble());
                    coin.setMarketCap(coin.getTotalVolume() * 100);

                    trendingCoins.add(coin);
                }
            }

            trendingCoins.sort((a, b) -> Double.compare(
                    Math.abs(b.getPriceChangePercentage24h()),
                    Math.abs(a.getPriceChangePercentage24h())
            ));

            List<Coin> top10 = trendingCoins.subList(0, Math.min(10, trendingCoins.size()));
            Map<String, List<Coin>> result = new HashMap<>();
            result.put("coins", top10);
            return objectMapper.writeValueAsString(result);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new Exception(e.getMessage());
        }
    }

    // Optional: Clear all caches every 2 minutes automatically
    @Scheduled(fixedRate = 120000)
    @CacheEvict(value = {"coinList", "coinDetails", "marketChart", "trendingCoins", "top50Coins"}, allEntries = true)
    public void clearCache() {
        System.out.println("Cache cleared - fresh data will be loaded on next request");
    }
}
