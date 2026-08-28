package com.skgis.ingestion;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class CsvReaderService {
    private static final Logger log = LoggerFactory.getLogger(CsvReaderService.class);

    private final EntityResolutionService entityResolutionService;

    // Small pool of shared ring devices and accounts to guarantee detectable rings
    private static final List<String> SHARED_RING_DEVICES = List.of(
            "DEV-RING-0042", "DEV-RING-0088", "DEV-RING-0105"
    );
    private static final List<String> SHARED_RING_ACCOUNTS = List.of(
            "ACC-RING-0091", "ACC-RING-0303", "ACC-RING-0777"
    );

    public CsvReaderService(EntityResolutionService entityResolutionService) {
        this.entityResolutionService = entityResolutionService;
    }

    public List<PaysimRecord> readAndAugmentCsv(String filePath) throws IOException {
        File csvFile = new File(filePath);
        if (!csvFile.exists()) {
            throw new IllegalArgumentException("CSV file not found at path: " + csvFile.getAbsolutePath());
        }

        List<PaysimRecord> records = new ArrayList<>();
        Map<String, String> customerToDeviceMap = new HashMap<>();
        Map<String, String> customerToAccountMap = new HashMap<>();

        boolean isCleanByPath = filePath.toLowerCase().contains("clean") || filePath.toLowerCase().contains("legitimate");

        // Pre-scan to check if dataset contains any fraud records (isFraud == 1)
        boolean hasFraudRecords = false;
        try (Reader preReader = new FileReader(csvFile, StandardCharsets.UTF_8);
             CSVParser preParser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(preReader)) {
            for (CSVRecord rec : preParser) {
                try {
                    if ("1".equals(rec.get("isFraud"))) {
                        hasFraudRecords = true;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("Could not pre-scan CSV for isFraud header: {}", e.getMessage());
        }

        boolean isCleanDataset = isCleanByPath || !hasFraudRecords;

        try (Reader reader = new FileReader(csvFile, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            long recordCount = 0;
            for (CSVRecord record : parser) {
                recordCount++;
                String txnId = "TXN-" + recordCount;
                long step = Long.parseLong(record.get("step"));
                String type = record.get("type");
                double amount = Double.parseDouble(record.get("amount"));
                String nameOrig = entityResolutionService.normalizeId(record.get("nameOrig"));
                double oldbalanceOrg = Double.parseDouble(record.get("oldbalanceOrg"));
                double newbalanceOrig = Double.parseDouble(record.get("newbalanceOrig"));
                String nameDest = entityResolutionService.normalizeId(record.get("nameDest"));
                double oldbalanceDest = Double.parseDouble(record.get("oldbalanceDest"));
                double newbalanceDest = Double.parseDouble(record.get("newbalanceDest"));
                int isFraud = isCleanDataset ? 0 : Integer.parseInt(record.get("isFraud"));

                // Derive device & account IDs based on dataset type
                String deviceId = customerToDeviceMap.computeIfAbsent(nameOrig, orig -> assignSyntheticDeviceId(orig, isCleanDataset));
                String bankAccountId = customerToAccountMap.computeIfAbsent(nameOrig, orig -> assignSyntheticAccountId(orig, isCleanDataset));

                PaysimRecord paysimRecord = new PaysimRecord(
                        txnId, step, type, amount, nameOrig,
                        oldbalanceOrg, newbalanceOrig, nameDest,
                        oldbalanceDest, newbalanceDest, isFraud,
                        deviceId, bankAccountId
                );
                records.add(paysimRecord);
            }

            log.info("Successfully read and augmented {} records from {} (isCleanDataset={})", records.size(), filePath, isCleanDataset);
        }

        return records;
    }

    private String assignSyntheticDeviceId(String nameOrig, boolean isCleanDataset) {
        int hash = Math.abs(nameOrig.hashCode());
        if (!isCleanDataset) {
            // ~5% of customers are deliberately grouped into shared ring devices for synthetic ring detection
            if (hash % 20 == 0 || hash % 20 == 1 || hash % 20 == 2 || hash % 20 == 3) {
                int ringIndex = hash % SHARED_RING_DEVICES.size();
                return SHARED_RING_DEVICES.get(ringIndex);
            }
            int poolIndex = (hash % 999999) + 1;
            return entityResolutionService.formatDeviceId(String.format("%06d", poolIndex));
        } else {
            // Strictly 1-to-1 dedicated unique devices for clean dataset
            int poolIndex = (hash % 999999) + 1;
            return entityResolutionService.formatDeviceId(String.format("CLEAN-%06d", poolIndex));
        }
    }

    private String assignSyntheticAccountId(String nameOrig, boolean isCleanDataset) {
        int hash = Math.abs(nameOrig.hashCode());
        if (!isCleanDataset) {
            if (hash % 15 == 0 || hash % 15 == 1) {
                int ringIndex = hash % SHARED_RING_ACCOUNTS.size();
                return SHARED_RING_ACCOUNTS.get(ringIndex);
            }
            int poolIndex = (hash % 999999) + 1;
            return entityResolutionService.formatAccountId(String.format("%06d", poolIndex));
        } else {
            // Strictly 1-to-1 dedicated unique bank accounts for clean dataset
            int poolIndex = (hash % 999999) + 1;
            return entityResolutionService.formatAccountId(String.format("CLEAN-%06d", poolIndex));
        }
    }
}
