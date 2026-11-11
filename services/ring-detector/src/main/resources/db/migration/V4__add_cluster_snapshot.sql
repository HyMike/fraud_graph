-- Full picture of what the investigator reviewed, frozen at decision time:
-- accounts, devices, which account used which device, and every TRANSFER
-- (id, from, to, amount, timestamp) inside the cluster. Neo4j data and Louvain
-- community IDs change after the fact; this column does not.
--
-- Nullable on purpose: rows recorded before V4 never captured this and it
-- cannot be reconstructed, so they stay NULL rather than holding invented data.

ALTER TABLE cluster_decisions
    ADD COLUMN cluster_snapshot JSONB;
