# fraud-graph

A graph-based fraud-ring detector: Spring Boot + Neo4j + Angular/Cytoscape.js. It finds coordinated fraud (mule networks, laundering cycles, synthetic-identity clusters) that a per-transaction scorer can't see, because the fraud lives in the *relationships* between accounts, not in any single transaction.

This is a standalone project — no shared code, database, or runtime with the sibling `fraud_pipeline` repo. The two are conceptually complementary ("two lenses on fraud": one scores individual transactions, this one investigates account networks), but nothing here calls into or depends on `fraud_pipeline`.

## What It Does

Accounts, devices, phones, and addresses become nodes; "sent money to" and "shares a device with" become edges. Once the data is a graph:
- **Structural Cypher queries** find specific shapes fast: fan-in/fan-out, A→B→C→A laundering cycles.
- **Neo4j Graph Data Science (Louvain + PageRank)** finds dense communities and ranks the central account within each one.

The system doesn't convict — it surfaces flagged clusters to an investigator dashboard for a human decision (confirm/dismiss), logged to an audit trail.

## Architecture

```
[data-generator — Python]  (offline, one-shot)
  → writes accounts/devices/transactions CSVs with known planted rings + innocent look-alikes
  → LOAD CSV into Neo4j

[ring-detector — Java/Spring Boot]  ←── REST API
  ├─► Neo4j            (Cypher pattern queries: fan-in, fan-out, cycles)
  ├─► Neo4j GDS         (Louvain community detection + PageRank centrality)
  ├─► PostgreSQL        (investigator decision audit log)
  └─ scheduled job + manual "recompute" endpoint re-run Louvain/PageRank

[graph-dashboard — Angular + Cytoscape.js]
  ← ranked flagged clusters, rendered as an interactive network graph
  → investigator Confirm / Dismiss
```

See [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) for full component breakdown and [docs/BUILD_ORDER.md](./docs/BUILD_ORDER.md) for the phased build guide.

## Tech Stack

| Layer | Technology |
|---|---|
| Detection service | Java 17, Spring Boot 3, Gradle |
| Graph store + algorithms | Neo4j 5 + Graph Data Science (Community Edition) |
| Graph access | `Neo4jClient`/raw Cypher (not Spring Data Neo4j entity mapping — GDS-style queries don't fit an OGM well) |
| Relational store | PostgreSQL (audit log only) |
| Synthetic data | Python (offline script, not a running service) |
| Dashboard | Angular (standalone components) + Cytoscape.js |
| Containerization | Docker Compose |

**No machine learning model is trained anywhere in this build.** Louvain and PageRank are deterministic graph algorithms — they compute directly from whatever graph is loaded, with no training data, no saved weights, and no memory between runs. A trained model (GNN) is an explicit future stretch (see Build Order), not part of the current build.

## Project Structure

```
fraud-graph/
├── services/
│   ├── ring-detector/      # Java/Spring Boot — Cypher + GDS detection, REST API
│   └── data-generator/     # Python — synthetic graph + ground truth (offline script)
├── dashboard/
│   └── graph-dashboard/    # Angular — investigator UI, Cytoscape.js graph view
├── infra/
│   ├── docker-compose.yml
│   └── k8s/                # stretch goal, not part of MVP
├── docs/
│   ├── ARCHITECTURE.md
│   └── BUILD_ORDER.md
├── CLAUDE.md
└── README.md
```

## Getting Started

### Prerequisites

- Docker Desktop (running)

### Start the system

```bash
docker compose -f infra/docker-compose.yml up --build
```

### Access

| Service | URL |
|---|---|
| Graph Dashboard (UI) | http://localhost:4200 |
| Ring Detector API | http://localhost:8081 |
| Neo4j Browser | http://localhost:7474 |

## Build Order

Built in phases, MVP-first (a thin working slice through every phase before any polish pass). See [docs/BUILD_ORDER.md](./docs/BUILD_ORDER.md) for the full guide.

| Phase | What | Status |
|---|---|---|
| 0 | Synthetic data generator (fixed ring shapes + look-alikes) | Not started |
| 1 | Graph construction (exact-match entity resolution → Neo4j) | Not started |
| 2 | Structural pattern detection (Cypher: fan-in/out, cycles) | Not started |
| 3 | Community detection (Neo4j GDS: Louvain + PageRank) | Not started |
| 4 | Investigator dashboard (Angular + Cytoscape.js) | Not started |
| 5 | Bridge-to-fraud_pipeline contract (documentation only) | Not started |
| 6 | GNN (Python side-service) — stretch, out of scope for now | Deferred |
