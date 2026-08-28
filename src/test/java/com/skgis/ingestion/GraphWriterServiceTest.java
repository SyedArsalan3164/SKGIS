package com.skgis.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphWriterServiceTest {

    private EntityResolutionService entityResolutionService;

    @BeforeEach
    void setUp() {
        entityResolutionService = new EntityResolutionService();
    }

    @Test
    void testEntityResolution() {
        assertTrue(entityResolutionService.isMerchant("M1234567"));
        assertFalse(entityResolutionService.isMerchant("C9876543"));

        assertEquals("DEV-0042", entityResolutionService.formatDeviceId("0042"));
        assertEquals("DEV-0042", entityResolutionService.formatDeviceId("DEV-0042"));

        assertEquals("ACC-0091", entityResolutionService.formatAccountId("0091"));
        assertEquals("ACC-0091", entityResolutionService.formatAccountId("ACC-0091"));
    }

    @Test
    void testPaysimRecordMapping() {
        PaysimRecord record = new PaysimRecord(
                "TXN-1", 1, "PAYMENT", 150.0, "C1001",
                500.0, 350.0, "M2001", 1000.0, 1150.0, 0,
                "DEV-RING-0042", "ACC-RING-0091"
        );

        assertEquals("TXN-1", record.getTxnId());
        assertEquals("C1001", record.getNameOrig());
        assertEquals("M2001", record.getNameDest());
        assertEquals("DEV-RING-0042", record.getDeviceId());
        assertEquals("ACC-RING-0091", record.getBankAccountId());
    }
}
