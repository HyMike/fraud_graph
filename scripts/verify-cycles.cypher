// Week-1 slice verification query (see docs/BUILD_ORDER.md Phase 0-2 "Done when").
// Precision-of-one check: against the fixed synthetic graph, this should return
// the planted A->B->C->A cycle (label FRAUD_RING, ringId cycle-1), and nothing
// else. Bounded length (*2..4) — never leave a cycle query unbounded.
//
// Note: the same physical cycle may appear once per rotation (starting at A, at
// B, or at C) since the pattern anchors on whichever node the match engine binds
// first — that's expected here, not a bug to fix in this verification script.
//
// Run with:
//   cypher-shell -a bolt://localhost:7687 -u neo4j -p fraudgraph -f scripts/verify-cycles.cypher

MATCH p = (a:Account)-[:TRANSFER*2..4]->(a)
WITH [n IN nodes(p)[0..-1] | n.id] AS cycleAccountIds,
     [n IN nodes(p)[0..-1] | n.label] AS labels
RETURN DISTINCT cycleAccountIds, labels
LIMIT 25;
