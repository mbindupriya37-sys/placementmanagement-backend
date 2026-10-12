package com.example.backend.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorsConfigTests {

    @Test
    void parsesPlainCommaSeparatedOrigins() {
        assertEquals(List.of(
                "https://placementos-frontend-tl1q.onrender.com",
                "http://localhost:5173"),
                CorsConfig.parseAllowedOrigins(
                        "https://placementos-frontend-tl1q.onrender.com,http://localhost:5173"));
    }

    @Test
    void toleratesJsonLikeBracketsAndQuotesWithoutChangingTheOrigin() {
        assertEquals(List.of("https://placementos-frontend-tl1q.onrender.com"),
                CorsConfig.parseAllowedOrigins(
                        "[\"https://placementos-frontend-tl1q.onrender.com\"]"));
    }
}
