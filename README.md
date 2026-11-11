# fraud-graph

A graph-based fraud-ring detector: Spring Boot + Neo4j + Angular/Cytoscape.js. It finds coordinated fraud (mule networks, laundering cycles, synthetic-identity clusters) that a per-transaction scorer can't see, because the fraud lives in the *relationships* between accounts, not in any single transaction.

This is a standalone project — no shared code, database, or runtime with the sibling `fraud_pipeline` repo. The two are conceptually complementary ("two lenses on fraud": one scores individual transactions, this one investigates account networks), but nothing here calls into or depends on `fraud_pipeline`.

## What It Does

Accounts and devices become nodes; "sent money to" (`TRANSFER`) and "logged in from" (`USED`) become edges. Once the data is a graph:
- **Structural Cypher queries** find specific shapes fast: fan-in/fan-out, A→B→C→A laundering cycles.
- **Neo4j Graph Data Science (Louvain + PageRank)** finds dense communities and ranks the central account within each one.

The system doesn't convict — it surfaces flagged clusters to an investigator dashboard for a human decision (confirm/dismiss). Each decision is logged to an audit trail with who made it and a frozen snapshot of the cluster they reviewed. Access is role-based (analyst, lead, auditor, admin) behind a JWT login.

## Architecture

```
[data-generator — Python]  (offline, one-shot)
  → writes accounts/devices/transactions CSVs with known planted rings + innocent look-alikes
  → LOAD CSV into Neo4j

[ring-detector — Java/Spring Boot]  ←── REST API (JWT, role-based access)
  ├─► Neo4j            (Cypher pattern queries: fan-in, fan-out, cycles)
  ├─► Neo4j GDS         (Louvain community detection + PageRank centrality)
  ├─► PostgreSQL        (investigator decision audit log + login accounts)
  └─ scheduled job + manual "recompute" endpoint re-run Louvain/PageRank

[graph-dashboard — Angular + Cytoscape.js]
  ← ranked flagged clusters, rendered as an interactive network graph
  → investigator Confirm / Dismiss
```

See [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) for full component breakdown and [docs/BUILD_ORDER.md](./docs/BUILD_ORDER.md) for the phased build guide.

## Tech Stack

| Layer | Technology |
|---|---|
| Detection service | Java 21, Spring Boot 3, Gradle |
| Auth | Spring Security, stateless JWT (HS256), role-based access |
| Graph store + algorithms | Neo4j 5 + Graph Data Science (Community Edition) |
| Graph access | `Neo4jClient`/raw Cypher (not Spring Data Neo4j entity mapping — GDS-style queries don't fit an OGM well) |
| Relational store | PostgreSQL (audit log + login accounts), Flyway migrations |
| Synthetic data | Python (offline script, not a running service) |
| Dashboard | Angular 21 (standalone components, signals) + Cytoscape.js, Vitest |
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
├── scripts/                # Cypher: load-graph, reset-graph, verification queries
├── docs/
│   ├── ARCHITECTURE.md
│   └── BUILD_ORDER.md
├── CLAUDE.md
└── README.md
```

Both applications are organised by layer:

```
ring-detector  com.fraudgraph.ringdetector/
  controller/  service/  repository/  scheduler/  entity/  dto/{request,response}/
  config/  constants/  enums/  exception/  util/

graph-dashboard  src/app/
  components/  services/  guards/  interceptors/  models/  constants/
  (+ src/environments/ for the API base URL)
```

Requests flow `controller → service → repository`; all Cypher lives in the repository layer. See [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md) for what lives in each folder.

## Getting Started

### Prerequisites

- Docker Desktop (running)

### Configure secrets

Passwords are read from `infra/.env`, which is gitignored. Create it from the template and set your own values:

```bash
cp infra/.env.example infra/.env
```

Docker Compose loads it automatically, and `./gradlew bootRun` / `./gradlew test` in `services/ring-detector` load it too. To use `cypher-shell` from your shell, export it first: `set -a; source infra/.env; set +a`.

### Secret scanning

[gitleaks](https://github.com/gitleaks/gitleaks) runs as a pre-commit hook and in CI ([.github/workflows/gitleaks.yml](.github/workflows/gitleaks.yml)). Rules are in [.gitleaks.toml](.gitleaks.toml). Enable the hook once per clone:

```bash
brew install pre-commit gitleaks
pre-commit install
```

### Start the system

```bash
docker compose -f infra/docker-compose.yml up --build
```

### Load the synthetic graph

The graph starts empty. Generate the CSVs and load them (Neo4j reads them from `services/data-generator/output/`):

```bash
python3 services/data-generator/generate.py
set -a; source infra/.env; set +a
cypher-shell -a bolt://localhost:7687 -u neo4j -p "$NEO4J_PASSWORD" -f scripts/load-graph.cypher
```

Then press **Recompute** in the dashboard (as `lead` or `admin`), or wait for the 5-minute scheduled run.

### Log in

On startup ring-detector seeds one user per role — `analyst`, `lead`, `auditor`, `admin` — all with the password set as `SEED_USER_PASSWORD` in `infra/.env`.

| Role | Can |
|---|---|
| analyst | view clusters, confirm/dismiss |
| lead | everything an analyst can, plus recompute and read the audit log |
| auditor | view clusters, read the audit log |
| admin | everything |

### Access

| Service | URL |
|---|---|
| Graph Dashboard (UI) | http://localhost:4200 |
| Ring Detector API | http://localhost:8081 |
| Neo4j Browser | http://localhost:7474 |

### Run the tests

```bash
cd services/ring-detector && ./gradlew test          # needs Neo4j + Postgres running
cd dashboard/graph-dashboard && npm test -- --watch=false
```

## Build Order

Built in phases, MVP-first (a thin working slice through every phase before any polish pass). See [docs/BUILD_ORDER.md](./docs/BUILD_ORDER.md) for the full guide.

| Phase | What | Status |
|---|---|---|
| 0 | Synthetic data generator (fixed ring shapes + look-alikes) | Done |
| 1 | Graph construction (exact-match entity resolution → Neo4j) | Done |
| 2 | Structural pattern detection (Cypher: fan-in/out, cycles) | Done |
| 3 | Community detection (Neo4j GDS: Louvain + PageRank) | Done |
| 4 | Investigator dashboard (Angular + Cytoscape.js) | Done |
| 5 | Bridge-to-fraud_pipeline contract (documentation only) | Not started |
| 6 | GNN (Python side-service) — stretch, out of scope for now | Deferred |
