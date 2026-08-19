CREATE TABLE operational_heartbeat (
  component VARCHAR(64) NOT NULL,
  instance_id VARCHAR(128) NOT NULL,
  last_seen_at DATETIME(3) NOT NULL,
  metadata_json JSON NULL,
  PRIMARY KEY (component, instance_id),
  KEY ix_operational_heartbeat_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
