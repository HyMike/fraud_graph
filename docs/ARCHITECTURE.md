# Architecture

## Overview

A graph-based fraud-ring detector — accounts, devices, phones, and addresses as nodes; transactions and shared attributes as edges. It answers "is this *group of accounts* a coordinated ring?", as a second lens alongside (but not integrated with) a per-transaction scorer.

**Goals:** demonstrate entity resolution, structural pattern detection (Cypher), community detection (Neo4j GDS Louvain + PageRank), and an investigator review workflow — with a measured false-positive rate against deliberately planted legitimate look-alike clusters (families, roommates, small businesses).

Standalone project: no shared code, database, or API with the sibling `fraud_pipeline` repo. See [Phase 5](./BUILD_ORDER.md) for the one place they conceptually connect (a documented contract only, not live code).

---

## Data Flow

```
[data-generator — Python, offline script]
  Writes: accounts.csv, devices.csv, account_device.csv, transactions.csv
  Plants: 3 known ring shapes (fan-in, A→B→C→A cycle, device-sharing cluster)
          2 look-alike shapes (family sharing a device, small business sharing an IP)
  Tags every row with ground truth (is_fraud_ring, ring_id) for measuring precision/FP rate.
        │
        ▼ (LOAD CSV)
[Neo4j]
  Nodes: Account, Device
  Edges: (:Account)-[:TRANSFER]->(:Account), (:Account)-[:USED]->(:Device)
        │
        ▼
[ring-detector — Java/Spring Boot]
  ├─ Structural queries (Cypher, near-real-time fast path — local subgraph only):
  │    fan-in / fan-out degree over a window
  │    bounded-length cycles (*2..4)
  ├─ Community detection (Neo4j GDS, batch/periodic — NOT per-transaction):
  │    Louvain  → which accounts form a suspicious cluster
  │    PageRank → within that cluster, who's the central account
  │    Triggered by: a @Scheduled job (periodic) AND a manual "recompute" REST
  │    endpoint (cheap at this graph size, used for on-demand/demo runs)
  ├─ Suspiciousness scoring: simple weighted formula (density, shared-device
  │    ratio, cluster size) — not a trained model
  └─ Case audit log → PostgreSQL
        │
        ▼
[graph-dashboard — Angular + Cytoscape.js]
  Ranked list of flagged clusters → cluster detail rendered as a network graph
  (nodes colored by community, sized by PageRank) → investigator Confirm/Dismiss
  → decision written to the PostgreSQL audit log
```

---

## Why no ML model in this build

Louvain and PageRank are deterministic graph algorithms, not trained models: they compute an answer directly from whatever graph is loaded right now, with no learned weights and no memory carried between runs. Re-running them on a bigger graph tomorrow doesn't build on today's result — it's a fresh computation every time. The only "training" concept in the wider vision (Phase 6, a GNN) is an explicit, deferred stretch goal — not part of this build.

---

## Components

### data-generator (`services/data-generator/`, Python)

| File | Responsibility |
|---|---|
| `generate.py` | Emits the 4 CSVs with 3 planted ring shapes + 2 look-alike shapes, tagged with ground truth |

Run once, offline — not a long-running service, no API.

### ring-detector (`services/ring-detector/`, Java/Spring Boot)

Java package: `com.fraudgraph.ringdetector`

| Class | Responsibility |
|---|---|
| `GraphLoadController` | Triggers/verifies CSV → Neo4j load (dev/demo convenience) |
| `StructuralDetectionService` | Cypher: fan-in, fan-out, bounded cycle queries |
| `CommunityDetectionService` | Calls Neo4j GDS Louvain + PageRank via `Neo4jClient` |
| `ClusterScoringService` | Weighted suspiciousness formula per community |
| `ClusterController` | REST: `GET /api/clusters` (ranked), `GET /api/clusters/{id}`, `POST /api/clusters/recompute` |
| `CaseController` | REST: `POST /api/clusters/{id}/decision` (confirm/dismiss) |
| `AuditLogService` | Writes investigator decisions to PostgreSQL |
| `ClusterRecomputeScheduler` | `@Scheduled` periodic re-run of Louvain/PageRank |

Uses `Neo4jClient`/raw Cypher for detection queries — not Spring Data Neo4j entity mapping, which adds friction for graph-algorithm-style access.

### graph-dashboard (`dashboard/graph-dashboard/`, Angular)

| Component | Purpose |
|---|---|
| `ClusterListComponent` | Ranked list of flagged clusters |
| `ClusterDetailComponent` | Cytoscape.js network view — nodes colored by community, sized by PageRank |
| `DecisionComponent` | Confirm/Dismiss + reason, calls Case API |
| `RecomputeButtonComponent` | Calls `POST /api/clusters/recompute` on demand |

---

## Database Schema

### Neo4j (graph store)

```
(:Account {id, name, opened_at, is_fraud_ring, ring_id})
(:Device {id, fingerprint})
(:Account)-[:TRANSFER {amount, ts}]->(:Account)
(:Account)-[:USED]->(:Device)
```

### PostgreSQL (audit log only — added at Phase 4, not before)

```sql
CREATE TABLE cluster_decisions (
  id            UUID PRIMARY KEY,
  community_id  TEXT NOT NULL,
  decision      TEXT CHECK (decision IN ('CONFIRM', 'DISMISS')),
  investigator  TEXT,
  notes         TEXT,
  decided_at    TIMESTAMPTZ DEFAULT now()
);
```

---

## Technology Choices

| Technology | Why |
|---|---|
| Neo4j + GDS | Cypher for pattern queries; Louvain + PageRank come built-in (Community Edition, no license needed) |
| `Neo4jClient` over Spring Data Neo4j OGM | GDS/Cypher-style graph-algorithm queries don't map cleanly to entity-graph repositories |
| Cytoscape.js | Purpose-built for network rendering; clusters render clearly |
| Bounded-length cycle queries (`*2..6`) | Unbounded path matches degrade badly as the graph grows |
| Scheduled + manual recompute (not per-transaction) | Full-graph Louvain/PageRank on every transaction isn't feasible at scale; local Cypher checks (fan-in, cycles) are the actual near-real-time fast path |

---

## Infrastructure

`docker-compose.yml` services: `neo4j` (with GDS plugin), `ring-detector`, `graph-dashboard`. `postgres` added starting at Phase 4 (audit log), not before.

---

## Build Order

See [docs/BUILD_ORDER.md](./BUILD_ORDER.md).
