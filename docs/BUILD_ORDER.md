# Build Order

## The Principle

MVP-first: a thin working slice through every phase, verified against planted ground truth, before any polish pass (varied/adversarial data, fuzzy entity resolution, full case-management workflow). Each phase has a clear, checkable deliverable.

---

## Phase 0 — Synthetic Data Generator (Python)
**Start here. Nothing downstream exists without data.**

- `services/data-generator/generate.py` emits a fixed graph (~500–2,000 nodes) as CSV: `accounts.csv`, `devices.csv`, `account_device.csv`, `transactions.csv`.
- Plant exactly 3 ring shapes: fan-in mule cash-out, A→B→C→A laundering cycle, device-sharing synthetic-identity cluster.
- Plant exactly 2 look-alike shapes (structurally similar, but legitimate): family sharing a device, small business sharing an IP.
- Tag every row with ground truth (`is_fraud_ring`, `ring_id`) so precision and false-positive rate can be measured later.
- No randomization/parameterization yet — that's a later polish pass, not MVP.

**Verify:** CSVs exist, row counts match what the script intended to plant, ground-truth columns are present.

---

## Phase 1 — Graph Construction (exact-match only)
**Build before detection because detection needs a populated graph.**

- Stand up Neo4j (+ GDS plugin) in Docker Compose.
- Load the 4 CSVs into Neo4j via `LOAD CSV`/`UNWIND` — no Spring Data Neo4j repositories yet, this is a bulk-load script.
- Only exact-match edges: same `device_id` → `(:Account)-[:USED]->(:Device)`; transactions → `(:Account)-[:TRANSFER]->(:Account)`.
- No fuzzy matching, no over-linking guardrails yet — those only matter once fuzzy matching exists (deferred).

**Verify:**
```cypher
MATCH (n) RETURN count(n);
MATCH ()-[r]->() RETURN count(r);
```
Counts should roughly match what `generate.py` intended to write.

---

## Phase 2 — Structural Pattern Detection (Cypher)
**The near-real-time fast path — runs on local subgraphs, not the whole graph.**

Three queries only, in `services/ring-detector`:
1. Fan-in: accounts receiving from an abnormally high number of distinct senders in a window.
2. Fan-out: symmetric version.
3. Bounded-length cycles: `MATCH p=(a)-[:TRANSFER*2..4]->(a) ... LIMIT n` — never unbounded.

Bridge-node detection deferred — it falls out of Phase 3's community output.

**Verify:** precision-of-one against seeded ground truth — the fan-in query returns *exactly* the planted mule cash-out account (and no look-alike), the cycle query returns *exactly* the planted A→B→C→A triple.

---

## Phase 3 — Community Detection (Neo4j GDS)
**Batch/periodic, not per-transaction — this is the honest framing to keep throughout.**

- Run Louvain (GDS Community Edition, free) → community ID per node.
- Run PageRank within a flagged community → centrality score per node.
- Suspiciousness score per community: a small weighted formula (density, shared-device ratio, cluster size) — not a trained model.

**Verify:** each of the 3 planted rings lands in its own community (or a shared one clearly distinct from normal traffic); PageRank within a flagged community ranks the "hub" account (fan-in target, or device-cluster's most-connected node) highest.

---

## Phase 4 — Investigator Dashboard (Angular + Cytoscape.js)
**The visual payoff.**

- `dashboard/graph-dashboard`: one page — Cytoscape view (nodes colored by community, sized by PageRank, flagged edges highlighted) + a flat ranked list of flagged clusters.
- Confirm/Dismiss buttons per cluster, with a reason, written to a PostgreSQL audit log (`cluster_decisions` table — this is where Postgres enters the stack, not before).
- Manual "Recompute clusters now" button → calls a `ring-detector` endpoint to re-run Louvain/PageRank on demand (cheap at this graph size, better for live demos).
- `@Scheduled` background job in `ring-detector` re-runs the same computation periodically — the "how this actually runs in production at scale" story, since per-transaction full-graph recompute isn't feasible at bank scale.
- No case-queue workflow, no escalate action, no auth in the MVP — polish, added later.

**Verify:** open the dashboard, see flagged clusters rendered via Cytoscape; Confirm/Dismiss one, confirm the Postgres audit row is written; click Recompute, confirm the view refreshes with fresh output within a couple seconds.

---

## Phase 5 — Bridge-to-fraud_pipeline Contract (documentation only)

- A short doc describing the graph-derived features (distance-to-fraud, centrality, community membership) and the shape `fraud_pipeline`'s payment-service would consume them in.
- **No code changes to `fraud_pipeline`.** This stays a design artifact, not a live integration, for this build.

---

## Deferred / Out of Scope (revisit only if time remains)

- Adversarial/varied ring generation (multiple templates, randomization, noise) — Phase 0 polish pass.
- Fuzzy entity resolution (edit-distance name matching, weighted corroboration, over-linking review queue) — Phase 1 polish pass, and the single biggest time sink in the original v2 doc if pulled forward.
- Full case-management workflow (queue, escalate, auth) — Phase 4 polish pass.
- Kafka streaming ingestion.
- Phase 6 GNN (Python/PyTorch Geometric side-service) — genuine stretch goal.
- Live Phase 5 integration into `fraud_pipeline`.

---

## Summary

| Phase | What | Why This Order |
|---|---|---|
| 0 | Synthetic data generator | Nothing downstream exists without data |
| 1 | Graph construction (exact-match) | Detection needs a populated graph |
| 2 | Structural Cypher detection | Fast path, cheapest to verify against ground truth |
| 3 | Louvain + PageRank (GDS) | Needs Phase 1's graph; cheap to demo well at this scale |
| 4 | Angular + Cytoscape dashboard | Built against real detection output, not mocks |
| 5 | fraud_pipeline bridge (doc only) | Design artifact, no live coupling |
