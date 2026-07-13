# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**fraud-graph** — a graph-based fraud-ring detector: Java/Spring Boot + Neo4j (Graph Data Science) + Angular/Cytoscape.js. Standalone project — no shared code, database, or runtime with the sibling `fraud_pipeline` repo (a separate, already-complete per-transaction fraud platform under `~/Desktop/fintech_projects/fraud_pipeline`). The two are conceptually complementary but not integrated; see `docs/BUILD_ORDER.md` Phase 5 for the one (documentation-only) place they connect.

**No ML model is trained anywhere in this build.** Louvain and PageRank (Neo4j GDS) are deterministic graph algorithms — they compute directly from whatever graph is currently loaded, with no training data, no saved weights, no memory between runs. A trained model (GNN) is Phase 6, an explicit deferred stretch goal.

Building MVP-first: a thin working slice through Phases 0–4, verified against planted synthetic ground truth, before any polish pass. See `docs/BUILD_ORDER.md` for the full phase breakdown.

## Repository Structure

```
fraud-graph/
├── services/
│   ├── ring-detector/       # Java 17, Spring Boot 3, Gradle
│   └── data-generator/      # Python — offline synthetic-data script, not a running service
├── dashboard/
│   └── graph-dashboard/     # Angular
├── infra/
│   ├── docker-compose.yml
│   └── k8s/                 # stretch, not MVP
└── docs/
    ├── ARCHITECTURE.md
    └── BUILD_ORDER.md
```

## data-generator (services/data-generator/)

```bash
cd services/data-generator
python generate.py          # writes accounts.csv, devices.csv, account_device.csv, transactions.csv
```

Plants exactly 3 ring shapes (fan-in mule cash-out, A→B→C→A cycle, device-sharing cluster) and 2 look-alikes (family sharing a device, small business sharing an IP), tagged with ground truth (`is_fraud_ring`, `ring_id`). This is intentionally a fixed, hand-authored small graph for the MVP — do not generalize it into a randomized/adversarial generator without an explicit ask; that's a deferred polish pass (see BUILD_ORDER.md).

## Ring Detector (services/ring-detector/)

Java package: `com.fraudgraph.ringdetector`

### Build & Run

```bash
cd services/ring-detector
./gradlew build
./gradlew bootRun        # requires Neo4j running (docker compose up neo4j)
./gradlew test
```

### Key conventions

- Use `Neo4jClient`/raw Cypher for detection queries (structural pattern matching, GDS calls). Do **not** model rings as Spring Data Neo4j entity graphs — SDN's OGM adds friction for graph-algorithm-style access.
- Cycle detection queries must bound path length (e.g. `*2..4`) and include a `LIMIT` — unbounded `[:TRANSFER*]` matches degrade badly as the graph grows.
- Community detection (Louvain/PageRank) is batch/periodic — triggered by a `@Scheduled` job and a manual `POST /api/clusters/recompute` endpoint. Never trigger a full-graph GDS recompute per-transaction; that's what the Phase 2 local Cypher checks are for.
- PostgreSQL is only used for the `cluster_decisions` audit log (Phase 4+) — do not introduce it earlier in the stack.

## Dashboard (dashboard/graph-dashboard/)

Angular SPA with Cytoscape.js for graph rendering. Built in Phase 4 against real `ring-detector` API endpoints, not mocks.

```bash
cd dashboard/graph-dashboard
npm test -- --watch=false      # Vitest
```

Cytoscape renders to a `<canvas>`, which jsdom doesn't implement — any spec file that mounts a component using Cytoscape must mock it (see `app.spec.ts`), the same way `fraud_pipeline`'s case-dashboard mocks Chart.js:

```ts
vi.mock('cytoscape', () => ({ default: vi.fn(() => ({})) }));
```

## Infrastructure

```bash
# Start all services
docker compose -f infra/docker-compose.yml up

# Start only Neo4j
docker compose -f infra/docker-compose.yml up -d neo4j
```

## Tech Stack

### Ring Detector (Java/Spring Boot)
- **Spring Web** — REST controllers
- **`Neo4jClient`** — raw Cypher / GDS procedure calls (not Spring Data Neo4j entity repositories)
- **Spring Scheduling** — periodic Louvain/PageRank recompute (`@Scheduled`)
- **Spring Data JPA** — PostgreSQL audit log (Phase 4+)

### Data Generator (Python)
- Plain Python, CSV output — no ML libraries, no training

### Infrastructure
- **Neo4j 5 + Graph Data Science (Community Edition)** — graph store, Louvain, PageRank. Community Edition is sufficient; no Enterprise license needed for these algorithms.
- **PostgreSQL** — audit log only (Phase 4+)
- **Docker Compose** — local orchestration

### Dashboard (Angular)
- **Angular** — SPA framework (standalone components)
- **Cytoscape.js** — network graph rendering

## Testing

Run the relevant test suite whenever you add or change behaviour in any service, mirroring the discipline in `fraud_pipeline`'s CLAUDE.md — keep suites green before considering a phase complete.

```bash
cd services/ring-detector
./gradlew test
```

(Test suite structure to be filled in as Phase 1+ introduces testable services — service-layer tests for structural detection, community detection, and cluster scoring.)
