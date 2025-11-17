package com.example.ibudgetproject.configurations;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "coinList",          // Cache for coin listings
                "coinDetails",       // Cache for individual coin details
                "marketChart",       // Cache for price charts
                "trendingCoins",     // Cache for trending coins
                "top50Coins"         // Cache for top 50 coins
        );

        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .initialCapacity(100)           // Initial cache size
                .maximumSize(500)               // Maximum entries
                .expireAfterWrite(60, TimeUnit.SECONDS)  // Cache expires after 60 seconds
                .recordStats();                 // Enable statistics
    }
}
