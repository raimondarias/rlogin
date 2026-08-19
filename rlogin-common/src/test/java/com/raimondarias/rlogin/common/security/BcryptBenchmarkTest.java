package com.raimondarias.rlogin.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BcryptBenchmarkTest {

    @Test
    void veryFastMachineSuggestsHigherCost() {
        assertTrue(BcryptBenchmark.suggestedCost(10, 5) > 10, "5ms per hash is far too cheap for cost 10");
    }

    @Test
    void verySlowMachineSuggestsLowerCost() {
        assertTrue(BcryptBenchmark.suggestedCost(10, 2000) < 10, "2s per hash is far too expensive for cost 10");
    }

    @Test
    void machineAlreadyInTheBandKeepsTheCost() {
        // 175ms is the middle of the 50-300ms band: no suggestion.
        assertEquals(10, BcryptBenchmark.suggestedCost(10, 175));
    }

    @Test
    void neverLeavesTheBcryptBounds() {
        assertTrue(BcryptBenchmark.suggestedCost(4, 1) >= 4);
        assertTrue(BcryptBenchmark.suggestedCost(31, 999_999) <= 31);
    }
}
