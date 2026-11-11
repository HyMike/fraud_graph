package com.fraudgraph.ringdetector.dto.response;

import java.util.List;

/**
 * Everything inside one community at a single moment: its accounts, the devices
 * they used (the same devices the cluster graph shows), and every TRANSFER
 * between two members. Stored as JSONB on each audit-log decision so the row
 * still says exactly what was reviewed after Neo4j changes.
 */
public record ClusterSnapshot(
    List<Account> accounts,
    List<Device> devices,
    List<DeviceUse> deviceUses,
    List<Transfer> transfers) {

    public record Account(String id, String label, String ringId, Double pagerank) {
    }

    public record Device(String id, String fingerprint) {
    }

    public record DeviceUse(String accountId, String deviceId) {
    }

    /** ts is the ISO-8601 timestamp stored on the TRANSFER relationship. */
    public record Transfer(String id, String fromAccountId, String toAccountId, Double amount, String ts) {
    }

    public List<String> accountIds() {
        return accounts.stream().map(Account::id).sorted().toList();
    }
}
