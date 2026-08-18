CREATE TABLE device (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  zone_id BINARY(16) NOT NULL,
  name VARCHAR(96) NOT NULL,
  device_type VARCHAR(24) NOT NULL,
  mqtt_client_id VARCHAR(128) NOT NULL,
  mqtt_username VARCHAR(128) NOT NULL,
  credential_hash VARCHAR(255) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  firmware_version VARCHAR(64) NULL,
  last_seen_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  deleted_at DATETIME(3) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_mqtt_client (mqtt_client_id),
  UNIQUE KEY uk_device_mqtt_username (mqtt_username),
  KEY ix_device_user_zone (user_id, zone_id, status, deleted_at),
  CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_device_zone FOREIGN KEY (zone_id) REFERENCES fridge_zone(id),
  CONSTRAINT chk_device_type CHECK (device_type IN ('PHYSICAL', 'VIRTUAL')),
  CONSTRAINT chk_device_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sensor_profile (
  id BINARY(16) NOT NULL,
  code VARCHAR(64) NOT NULL,
  profile_version INT NOT NULL DEFAULT 1,
  zone_kind VARCHAR(24) NOT NULL,
  metric VARCHAR(16) NOT NULL,
  physical_min DECIMAL(10,3) NOT NULL,
  physical_max DECIMAL(10,3) NOT NULL,
  normal_min DECIMAL(10,3) NOT NULL,
  normal_max DECIMAL(10,3) NOT NULL,
  max_change_per_minute DECIMAL(10,3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sensor_profile_code_version (code, profile_version),
  KEY ix_sensor_profile_kind_metric (zone_kind, metric, profile_version),
  CONSTRAINT chk_sensor_profile_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE sensor
  ADD COLUMN device_id BINARY(16) NULL AFTER zone_id,
  ADD COLUMN profile_id BINARY(16) NULL AFTER device_id,
  ADD COLUMN name VARCHAR(96) NULL AFTER metric,
  ADD COLUMN external_key VARCHAR(96) NULL AFTER name,
  ADD COLUMN last_valid_value DECIMAL(10,3) NULL AFTER enabled,
  ADD COLUMN last_unit VARCHAR(16) NULL AFTER last_valid_value,
  ADD COLUMN last_quality VARCHAR(16) NULL AFTER last_unit,
  ADD COLUMN last_observed_at DATETIME(3) NULL AFTER last_quality,
  ADD COLUMN last_received_at DATETIME(3) NULL AFTER last_observed_at,
  ADD COLUMN consecutive_suspect_count INT NOT NULL DEFAULT 0 AFTER last_received_at,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER updated_at,
  ADD UNIQUE KEY uk_sensor_device_external (device_id, external_key),
  ADD KEY ix_sensor_device_binding (device_id, binding_status, enabled),
  ADD KEY ix_sensor_zone_metric_current (zone_id, metric, binding_status, enabled, last_observed_at),
  ADD CONSTRAINT fk_sensor_device FOREIGN KEY (device_id) REFERENCES device(id),
  ADD CONSTRAINT fk_sensor_profile FOREIGN KEY (profile_id) REFERENCES sensor_profile(id);

CREATE TABLE telemetry_message (
  id BINARY(16) NOT NULL,
  device_id BINARY(16) NOT NULL,
  message_id CHAR(36) NOT NULL,
  observed_at DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  firmware_version VARCHAR(64) NULL,
  source VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL,
  rejection_code VARCHAR(64) NULL,
  payload_hash CHAR(64) NOT NULL,
  payload_json JSON NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_telemetry_device_message (device_id, message_id),
  KEY ix_telemetry_received (received_at),
  KEY ix_telemetry_status_received (status, received_at),
  CONSTRAINT fk_telemetry_device FOREIGN KEY (device_id) REFERENCES device(id),
  CONSTRAINT chk_telemetry_status CHECK (status IN ('ACCEPTED', 'REJECTED', 'DUPLICATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sensor_reading (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  zone_id BINARY(16) NOT NULL,
  device_id BINARY(16) NOT NULL,
  sensor_id BINARY(16) NOT NULL,
  telemetry_message_id BINARY(16) NOT NULL,
  metric VARCHAR(16) NOT NULL,
  value DECIMAL(10,3) NOT NULL,
  unit VARCHAR(16) NOT NULL,
  quality VARCHAR(16) NOT NULL,
  source VARCHAR(24) NOT NULL,
  observed_at DATETIME(3) NOT NULL,
  received_at DATETIME(3) NOT NULL,
  PRIMARY KEY (observed_at, id),
  KEY ix_reading_sensor_time (sensor_id, observed_at DESC),
  KEY ix_reading_zone_metric_time (zone_id, metric, observed_at DESC),
  KEY ix_reading_user_time (user_id, observed_at DESC),
  CONSTRAINT chk_reading_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY')),
  CONSTRAINT chk_reading_quality CHECK (quality IN ('GOOD', 'SUSPECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
PARTITION BY RANGE COLUMNS(observed_at) (
  PARTITION p_before_202608 VALUES LESS THAN ('2026-08-01'),
  PARTITION p202608 VALUES LESS THAN ('2026-09-01'),
  PARTITION p202609 VALUES LESS THAN ('2026-10-01'),
  PARTITION p202610 VALUES LESS THAN ('2026-11-01'),
  PARTITION p202611 VALUES LESS THAN ('2026-12-01'),
  PARTITION pmax VALUES LESS THAN (MAXVALUE)
);

CREATE TABLE sensor_reading_hourly (
  sensor_id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  zone_id BINARY(16) NOT NULL,
  metric VARCHAR(16) NOT NULL,
  hour_start DATETIME(3) NOT NULL,
  min_value DECIMAL(10,3) NOT NULL,
  max_value DECIMAL(10,3) NOT NULL,
  avg_value DECIMAL(10,3) NOT NULL,
  sample_count INT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (sensor_id, hour_start),
  KEY ix_hourly_zone_metric_time (zone_id, metric, hour_start DESC),
  KEY ix_hourly_user_time (user_id, hour_start DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE zone_environment_state (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  zone_id BINARY(16) NOT NULL,
  metric VARCHAR(16) NOT NULL,
  current_value DECIMAL(10,3) NULL,
  current_unit VARCHAR(16) NOT NULL,
  current_quality VARCHAR(16) NOT NULL,
  last_observed_at DATETIME(3) NULL,
  last_received_at DATETIME(3) NULL,
  outside_since DATETIME(3) NULL,
  normal_since DATETIME(3) NULL,
  stale_since DATETIME(3) NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_zone_environment_metric (zone_id, metric),
  KEY ix_zone_environment_fridge (user_id, fridge_id, updated_at),
  CONSTRAINT fk_zone_environment_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_zone_environment_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id),
  CONSTRAINT fk_zone_environment_zone FOREIGN KEY (zone_id) REFERENCES fridge_zone(id),
  CONSTRAINT chk_zone_environment_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE environment_incident (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  fridge_id BINARY(16) NOT NULL,
  zone_id BINARY(16) NOT NULL,
  metric VARCHAR(16) NOT NULL,
  reason VARCHAR(32) NOT NULL,
  direction VARCHAR(16) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  started_at DATETIME(3) NOT NULL,
  last_observed_at DATETIME(3) NOT NULL,
  ended_at DATETIME(3) NULL,
  max_deviation DECIMAL(10,3) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  open_zone_id BINARY(16) GENERATED ALWAYS AS (CASE WHEN status = 'OPEN' THEN zone_id ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_environment_open (open_zone_id, metric, reason),
  KEY ix_environment_user_status (user_id, status, started_at DESC),
  KEY ix_environment_fridge_status (fridge_id, status, started_at DESC),
  KEY ix_environment_zone_time (zone_id, started_at DESC),
  CONSTRAINT fk_environment_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_environment_fridge FOREIGN KEY (fridge_id) REFERENCES fridge(id),
  CONSTRAINT fk_environment_zone FOREIGN KEY (zone_id) REFERENCES fridge_zone(id),
  CONSTRAINT chk_environment_metric CHECK (metric IN ('TEMPERATURE', 'HUMIDITY')),
  CONSTRAINT chk_environment_reason CHECK (reason IN ('OUT_OF_RANGE', 'STALE_DATA', 'SENSOR_SUSPECT')),
  CONSTRAINT chk_environment_status CHECK (status IN ('OPEN', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE batch_environment_exposure (
  id BINARY(16) NOT NULL,
  batch_id BINARY(16) NOT NULL,
  incident_id BINARY(16) NOT NULL,
  processed_until DATETIME(3) NOT NULL,
  exposure_minutes DECIMAL(14,3) NOT NULL DEFAULT 0,
  risk_minutes DECIMAL(14,3) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_incident_exposure (batch_id, incident_id),
  KEY ix_exposure_incident (incident_id, processed_until),
  CONSTRAINT fk_exposure_batch FOREIGN KEY (batch_id) REFERENCES inventory_batch(id),
  CONSTRAINT fk_exposure_incident FOREIGN KEY (incident_id) REFERENCES environment_incident(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE debug_telemetry_scenario (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  device_id BINARY(16) NOT NULL,
  sensor_id BINARY(16) NOT NULL,
  mode VARCHAR(16) NOT NULL,
  target_value DECIMAL(10,3) NULL,
  duration_minutes INT NOT NULL,
  jitter DECIMAL(10,3) NOT NULL DEFAULT 0,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  started_at DATETIME(3) NOT NULL,
  ends_at DATETIME(3) NOT NULL,
  next_emit_at DATETIME(3) NOT NULL,
  last_emit_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_debug_scenario_due (status, next_emit_at),
  KEY ix_debug_scenario_user (user_id, created_at DESC),
  CONSTRAINT fk_debug_scenario_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT fk_debug_scenario_device FOREIGN KEY (device_id) REFERENCES device(id),
  CONSTRAINT fk_debug_scenario_sensor FOREIGN KEY (sensor_id) REFERENCES sensor(id),
  CONSTRAINT chk_debug_scenario_mode CHECK (mode IN ('NORMAL', 'TARGET', 'STALE')),
  CONSTRAINT chk_debug_scenario_status CHECK (status IN ('ACTIVE', 'STOPPED', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification (
  id BINARY(16) NOT NULL,
  user_id BINARY(16) NOT NULL,
  notification_type VARCHAR(32) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id BINARY(16) NOT NULL,
  dedup_key VARCHAR(160) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  title VARCHAR(160) NOT NULL,
  body VARCHAR(1000) NOT NULL,
  deep_link VARCHAR(255) NULL,
  resolved_at DATETIME(3) NULL,
  read_at DATETIME(3) NULL,
  dismissed_at DATETIME(3) NULL,
  expires_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_user_dedup (user_id, dedup_key),
  KEY ix_notification_user_read (user_id, read_at, created_at DESC),
  KEY ix_notification_subject (subject_type, subject_id),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shedlock (
  name VARCHAR(64) NOT NULL,
  lock_until DATETIME(3) NOT NULL,
  locked_at DATETIME(3) NOT NULL,
  locked_by VARCHAR(255) NOT NULL,
  PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE food_storage_profile
  ADD COLUMN temperature_moderate_deviation_c DECIMAL(8,3) NOT NULL DEFAULT 2.000 AFTER risk_coefficient,
  ADD COLUMN temperature_severe_deviation_c DECIMAL(8,3) NOT NULL DEFAULT 5.000 AFTER temperature_moderate_deviation_c,
  ADD COLUMN humidity_moderate_deviation_pct DECIMAL(8,3) NOT NULL DEFAULT 10.000 AFTER temperature_severe_deviation_c,
  ADD COLUMN humidity_severe_deviation_pct DECIMAL(8,3) NOT NULL DEFAULT 20.000 AFTER humidity_moderate_deviation_pct,
  ADD COLUMN mild_risk_multiplier DECIMAL(8,3) NOT NULL DEFAULT 1.000 AFTER humidity_severe_deviation_pct,
  ADD COLUMN moderate_risk_multiplier DECIMAL(8,3) NOT NULL DEFAULT 1.500 AFTER mild_risk_multiplier,
  ADD COLUMN severe_risk_multiplier DECIMAL(8,3) NOT NULL DEFAULT 2.500 AFTER moderate_risk_multiplier,
  ADD COLUMN high_risk_minutes DECIMAL(14,3) NOT NULL DEFAULT 720.000 AFTER severe_risk_multiplier;

ALTER TABLE shelf_life_assessment
  ADD COLUMN environment_impacts JSON NULL AFTER explanation;

ALTER TABLE outbox_event
  ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER status,
  ADD COLUMN locked_at DATETIME(3) NULL AFTER available_at,
  ADD COLUMN processed_at DATETIME(3) NULL AFTER locked_at,
  ADD COLUMN last_error VARCHAR(1000) NULL AFTER processed_at;

INSERT INTO sensor_profile
  (id, code, profile_version, zone_kind, metric, physical_min, physical_max, normal_min, normal_max, max_change_per_minute, created_at, updated_at)
VALUES
  (UUID_TO_BIN(UUID()), 'CHILL_TEMPERATURE', 1, 'CHILL', 'TEMPERATURE', -40, 60, 0, 8, 3, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'CHILL_HUMIDITY', 1, 'CHILL', 'HUMIDITY', 0, 100, 40, 90, 15, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'FRESH_TEMPERATURE', 1, 'FRESH', 'TEMPERATURE', -40, 60, 0, 6, 2, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'FRESH_HUMIDITY', 1, 'FRESH', 'HUMIDITY', 0, 100, 60, 95, 12, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'FREEZE_TEMPERATURE', 1, 'FREEZE', 'TEMPERATURE', -40, 60, -28, -12, 3, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'FREEZE_HUMIDITY', 1, 'FREEZE', 'HUMIDITY', 0, 100, 20, 70, 12, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'VARIABLE_TEMPERATURE', 1, 'VARIABLE', 'TEMPERATURE', -40, 60, -4, 4, 3, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (UUID_TO_BIN(UUID()), 'VARIABLE_HUMIDITY', 1, 'VARIABLE', 'HUMIDITY', 0, 100, 30, 90, 15, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
