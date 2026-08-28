package com.skgis.ingestion;

import org.springframework.stereotype.Service;

@Service
public class EntityResolutionService {

    public boolean isMerchant(String entityId) {
        if (entityId == null) return false;
        return entityId.trim().startsWith("M");
    }

    public String normalizeId(String rawId) {
        if (rawId == null) return "";
        return rawId.trim();
    }

    public String formatDeviceId(String rawDevice) {
        if (rawDevice == null || rawDevice.isBlank()) return "DEV-UNKNOWN";
        if (rawDevice.startsWith("DEV-")) return rawDevice;
        return "DEV-" + rawDevice;
    }

    public String formatAccountId(String rawAccount) {
        if (rawAccount == null || rawAccount.isBlank()) return "ACC-UNKNOWN";
        if (rawAccount.startsWith("ACC-")) return rawAccount;
        return "ACC-" + rawAccount;
    }
}
