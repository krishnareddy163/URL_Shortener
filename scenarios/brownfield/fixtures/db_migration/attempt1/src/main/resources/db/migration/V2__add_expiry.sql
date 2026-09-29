-- Nullable: existing links (NULL) never expire, so v1 behavior is unchanged.
ALTER TABLE link ADD COLUMN expires_at TIMESTAMP NULL;
