-- Automatically bind every existing logical slot to an internal virtual probe.
-- This intentionally uses only DML because the migration account has no CREATE TEMPORARY TABLE privilege.
INSERT INTO device (id, user_id, zone_id, name, device_type, status, firmware_version, created_at, updated_at, version)
SELECT UUID_TO_BIN(UUID()),
       f.user_id,
       z.id,
       CONCAT(z.name, CASE WHEN s.metric = 'TEMPERATURE' THEN '温度' ELSE '湿度' END, '模拟探头-', s.slot_index),
       'VIRTUAL',
       'ACTIVE',
       'virtual-simulator',
       UTC_TIMESTAMP(3),
       UTC_TIMESTAMP(3),
       0
FROM sensor s
JOIN fridge_zone z ON z.id = s.zone_id AND z.deleted_at IS NULL
JOIN fridge f ON f.id = z.fridge_id
JOIN sensor_profile p ON p.zone_kind = z.kind
  AND p.metric = s.metric
  AND p.profile_version = (
    SELECT MAX(p2.profile_version)
    FROM sensor_profile p2
    WHERE p2.zone_kind = p.zone_kind AND p2.metric = p.metric
  )
WHERE s.device_id IS NULL
  AND s.binding_status = 'PENDING_BIND'
  AND s.enabled = TRUE;

UPDATE sensor s
JOIN fridge_zone z ON z.id = s.zone_id AND z.deleted_at IS NULL
JOIN fridge f ON f.id = z.fridge_id
JOIN sensor_profile p ON p.zone_kind = z.kind
  AND p.metric = s.metric
  AND p.profile_version = (
    SELECT MAX(p2.profile_version)
    FROM sensor_profile p2
    WHERE p2.zone_kind = p.zone_kind AND p2.metric = p.metric
  )
JOIN device d ON d.user_id = f.user_id
  AND d.zone_id = z.id
  AND d.device_type = 'VIRTUAL'
  AND d.status = 'ACTIVE'
  AND d.name = CONCAT(z.name, CASE WHEN s.metric = 'TEMPERATURE' THEN '温度' ELSE '湿度' END, '模拟探头-', s.slot_index)
SET s.device_id = d.id,
    s.profile_id = p.id,
    s.name = d.name,
    s.external_key = CONCAT(LOWER(s.metric), '-', s.slot_index),
    s.binding_status = 'BOUND',
    s.source = 'VIRTUAL_SIMULATOR'
WHERE s.device_id IS NULL
  AND s.binding_status = 'PENDING_BIND'
  AND s.enabled = TRUE;
