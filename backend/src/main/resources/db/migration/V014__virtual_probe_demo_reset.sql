-- Demo mode reset: retain identity and business data, rebuild all probe telemetry.
DELETE nd FROM notification_delivery nd
JOIN notification n ON n.id = nd.notification_id
WHERE n.notification_type = 'ENVIRONMENT_ALERT';
DELETE FROM notification WHERE notification_type = 'ENVIRONMENT_ALERT';
DELETE FROM batch_environment_exposure;
DELETE FROM environment_incident;
DELETE FROM zone_environment_state;
DELETE FROM debug_telemetry_scenario;
DROP TABLE debug_telemetry_scenario;
DELETE FROM sensor_reading_hourly;
DELETE FROM sensor_reading;
DELETE FROM telemetry_message;
DELETE FROM outbox_event WHERE aggregate_type = 'Telemetry';
UPDATE shelf_life_assessment
SET environment_impacts = NULL,
    cumulative_risk_minutes = 0;
UPDATE sensor
SET device_id = NULL,
    profile_id = NULL,
    name = NULL,
    external_key = NULL,
    binding_status = 'PENDING_BIND',
    source = 'LOGICAL_SLOT',
    last_valid_value = NULL,
    last_unit = NULL,
    last_quality = NULL,
    last_observed_at = NULL,
    last_received_at = NULL,
    consecutive_suspect_count = 0;
DELETE FROM device;
ALTER TABLE device DROP CHECK chk_device_type;
ALTER TABLE device
  DROP INDEX uk_device_mqtt_client,
  DROP INDEX uk_device_mqtt_username,
  DROP COLUMN mqtt_client_id,
  DROP COLUMN mqtt_username,
  DROP COLUMN credential_hash,
  ADD CONSTRAINT chk_device_type CHECK (device_type = 'VIRTUAL');
