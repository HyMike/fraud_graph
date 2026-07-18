package com.fraudgraph.ringdetector.detection;

/** An account flagged for an abnormal number of distinct counterparties (fan-in or fan-out) within a time window. */
public record CounterpartyFlag(String accountId, String label, String ringId, long distinctCounterparties) {
}
