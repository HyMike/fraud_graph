// Phase 1 (MVP): bulk-load the data-generator's CSV output into Neo4j.
// No Spring Data Neo4j repositories involved here — this is a raw, one-shot
// LOAD CSV script, run directly against Neo4j (cypher-shell or Neo4j Browser).
//
// Prerequisites:
//   1. python3 services/data-generator/generate.py   (writes the CSVs)
//   2. docker compose -f infra/docker-compose.yml up -d neo4j
//      (the compose file mounts services/data-generator/output to Neo4j's
//      import directory, so file:///accounts.csv etc. resolve correctly)
//
// Run with:
//   cypher-shell -a bolt://localhost:7687 -u neo4j -p fraudgraph -f scripts/load-graph.cypher
//
// This script is additive (MERGE, not CREATE) — safe to re-run after regenerating data.
// To start from a clean graph first, run scripts/reset-graph.cypher.

CREATE INDEX account_id_index IF NOT EXISTS FOR (a:Account) ON (a.id);
CREATE INDEX device_id_index IF NOT EXISTS FOR (d:Device) ON (d.id);

LOAD CSV WITH HEADERS FROM 'file:///accounts.csv' AS row
MERGE (a:Account {id: row.id})
SET a.name = row.name,
    a.openedAt = datetime(row.opened_at),
    a.label = row.label,
    a.ringId = row.ring_id;

LOAD CSV WITH HEADERS FROM 'file:///devices.csv' AS row
MERGE (d:Device {id: row.id})
SET d.fingerprint = row.fingerprint;

LOAD CSV WITH HEADERS FROM 'file:///account_device.csv' AS row
MATCH (a:Account {id: row.account_id})
MATCH (d:Device {id: row.device_id})
MERGE (a)-[:USED]->(d);

LOAD CSV WITH HEADERS FROM 'file:///transactions.csv' AS row
MATCH (from:Account {id: row.from_account})
MATCH (to:Account {id: row.to_account})
MERGE (from)-[t:TRANSFER {id: row.id}]->(to)
SET t.amount = toFloat(row.amount),
    t.ts = datetime(row.ts);
