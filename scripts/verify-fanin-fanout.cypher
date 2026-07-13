// Week-1 slice verification query (see docs/BUILD_ORDER.md Phase 0-2 "Done when").
// Precision-of-one check: against the fixed synthetic graph, this should return
// EXACTLY the planted fan-in cash-out account (label FRAUD_RING, ringId fanin-1),
// and nothing else. Fan-out should return nothing (no fan-out ring is planted).
//
// Run with:
//   cypher-shell -a bolt://localhost:7687 -u neo4j -p fraudgraph -f scripts/verify-fanin-fanout.cypher

// Fan-in: accounts receiving from an abnormally high number of distinct senders
MATCH (sender:Account)-[:TRANSFER]->(receiver:Account)
WITH receiver, count(DISTINCT sender) AS distinctSenders
WHERE distinctSenders >= 10
RETURN receiver.id AS accountId, receiver.label AS label, receiver.ringId AS ringId, distinctSenders
ORDER BY distinctSenders DESC;

// Fan-out: accounts sending to an abnormally high number of distinct receivers
MATCH (sender:Account)-[:TRANSFER]->(receiver:Account)
WITH sender, count(DISTINCT receiver) AS distinctReceivers
WHERE distinctReceivers >= 10
RETURN sender.id AS accountId, sender.label AS label, sender.ringId AS ringId, distinctReceivers
ORDER BY distinctReceivers DESC;
