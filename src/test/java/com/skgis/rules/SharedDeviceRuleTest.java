package com.skgis.rules;

import com.skgis.graph.GraphRepository;
import com.skgis.model.RiskReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SharedDeviceRuleTest {

    private SharedDeviceRule sharedDeviceRule;
    private GraphRepository graphRepository;

    @BeforeEach
    void setUp() {
        sharedDeviceRule = new SharedDeviceRule();
        graphRepository = Mockito.mock(GraphRepository.class);
    }

    @Test
    void testSharedDeviceRuleTrigger() {
        Map<String, Object> row = new HashMap<>();
        row.put("deviceId", "DEV-RING-0042");
        row.put("custCount", 4L);
        row.put("customers", List.of("C1001", "C1002", "C1003", "C1004"));

        when(graphRepository.executeCypher(anyString(), anyMap()))
                .thenReturn(List.of(row));

        Optional<RiskReason> reasonOpt = sharedDeviceRule.evaluate(
                List.of("C1001", "C1002", "C1003", "C1004", "DEV-RING-0042"),
                graphRepository
        );

        assertTrue(reasonOpt.isPresent());
        RiskReason reason = reasonOpt.get();
        assertEquals("SharedDeviceRule", reason.getRule());
        assertTrue(reason.getExplanation().contains("4 customers share Device DEV-RING-0042"));
        assertTrue(reason.getEvidenceEntityIds().contains("DEV-RING-0042"));
    }

    @Test
    void testSharedDeviceRuleNotTriggered() {
        when(graphRepository.executeCypher(anyString(), anyMap()))
                .thenReturn(Collections.emptyList());

        Optional<RiskReason> reasonOpt = sharedDeviceRule.evaluate(
                List.of("C1001", "DEV-0001"),
                graphRepository
        );

        assertFalse(reasonOpt.isPresent());
    }
}
