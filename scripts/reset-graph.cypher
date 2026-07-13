// Wipes the entire graph. Destructive — use before a clean reload of load-graph.cypher.
//
// Run with:
//   cypher-shell -a bolt://localhost:7687 -u neo4j -p fraudgraph -f scripts/reset-graph.cypher

MATCH (n) DETACH DELETE n;
