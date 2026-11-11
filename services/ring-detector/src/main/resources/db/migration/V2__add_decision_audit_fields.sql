-- Make the audit log answer "who confirmed what, exactly":
--   decided_by          who made the decision
--   member_account_ids  snapshot of the cluster's accounts at decision time
--                       (Louvain renumbers community_id on every recompute, so
--                       community_id alone stops meaning anything afterwards)
--   member_fingerprint  SHA-256 of the sorted member IDs, for lookups/joins

ALTER TABLE cluster_decisions
    ADD COLUMN decided_by         VARCHAR(255),
    ADD COLUMN member_account_ids TEXT[],
    ADD COLUMN member_fingerprint VARCHAR(64);

-- Rows written before this migration have no record of who decided or which
-- accounts the community contained, and that cannot be recovered — mark them
-- explicitly instead of inventing values.
UPDATE cluster_decisions
SET decided_by         = 'unknown (recorded before V2)',
    member_account_ids = '{}',
    member_fingerprint = ''
WHERE decided_by IS NULL;

ALTER TABLE cluster_decisions
    ALTER COLUMN decided_by         SET NOT NULL,
    ALTER COLUMN member_account_ids SET NOT NULL,
    ALTER COLUMN member_fingerprint SET NOT NULL;

CREATE INDEX cluster_decisions_member_fingerprint_idx ON cluster_decisions (member_fingerprint);
