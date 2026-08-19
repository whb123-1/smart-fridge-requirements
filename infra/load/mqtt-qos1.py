#!/usr/bin/env python3
"""Production MQTT QoS 1 acceptance test using backend-issued device credentials."""

from __future__ import annotations

import argparse
import json
import math
import re
import secrets
import ssl
import subprocess
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import paho.mqtt.client as mqtt


class ApiError(RuntimeError):
    def __init__(self, status: int, code: str, retry_after: str | None = None):
        super().__init__(f"API returned HTTP {status} ({code})")
        self.status = status
        self.code = code
        self.retry_after = retry_after


class ApiClient:
    def __init__(self, base_url: str, insecure_tls: bool):
        self.base_url = base_url.rstrip("/")
        self.context = ssl._create_unverified_context() if insecure_tls else ssl.create_default_context()

    def call(
        self,
        method: str,
        path: str,
        *,
        body: dict[str, Any] | None = None,
        token: str | None = None,
        idempotency_key: bool = False,
        expected: tuple[int, ...] = (200,),
    ) -> Any:
        encoded = json.dumps(body, separators=(",", ":")).encode() if body is not None else None
        headers = {"Accept": "application/json", "User-Agent": "xianzhi-mqtt-acceptance/1"}
        if encoded is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        if idempotency_key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
        request = Request(self.base_url + path, data=encoded, headers=headers, method=method)
        try:
            with urlopen(request, timeout=60, context=self.context) as response:
                status = response.status
                payload = response.read()
                response_headers = response.headers
        except HTTPError as error:
            status = error.code
            payload = error.read()
            response_headers = error.headers
        try:
            document = json.loads(payload) if payload else {}
        except json.JSONDecodeError:
            document = {}
        if status not in expected:
            raise ApiError(status, str(document.get("code", "UNKNOWN")), response_headers.get("Retry-After"))
        return document.get("data")


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        values[name.strip()] = value.strip().strip('"').strip("'")
    return values


def reason_value(reason: Any) -> int:
    value = getattr(reason, "value", reason)
    try:
        return int(value)
    except (TypeError, ValueError):
        return 255


def on_connect(_client: mqtt.Client, state: dict[str, Any], _flags: Any, reason: Any, _properties: Any) -> None:
    state["connect_reason"] = reason_value(reason)
    state["connect_event"].set()


def on_disconnect(
    _client: mqtt.Client,
    state: dict[str, Any],
    _disconnect_flags: Any,
    reason: Any,
    _properties: Any,
) -> None:
    state["disconnect_reason"] = reason_value(reason)
    state["disconnect_event"].set()


def on_publish(
    _client: mqtt.Client,
    state: dict[str, Any],
    message_id: int,
    reason: Any,
    _properties: Any,
) -> None:
    state["publish_reasons"][message_id] = reason_value(reason)


def new_mqtt_client(credential: dict[str, Any], host: str, port: int, path: str, insecure_tls: bool):
    state: dict[str, Any] = {
        "connect_event": threading.Event(),
        "disconnect_event": threading.Event(),
        "connect_reason": None,
        "disconnect_reason": None,
        "publish_reasons": {},
    }
    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=credential["clientId"],
        protocol=mqtt.MQTTv5,
        transport="websockets",
        userdata=state,
    )
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_publish = on_publish
    client.username_pw_set(credential["username"], credential["password"])
    client.ws_set_options(path=path)
    client.tls_set(cert_reqs=ssl.CERT_NONE if insecure_tls else ssl.CERT_REQUIRED)
    if insecure_tls:
        client.tls_insecure_set(True)
    return client, state


def connect_client(
    credential: dict[str, Any], host: str, port: int, path: str, insecure_tls: bool, timeout: float
):
    client, state = new_mqtt_client(credential, host, port, path, insecure_tls)
    client.connect(host, port, keepalive=60)
    client.loop_start()
    if not state["connect_event"].wait(timeout):
        client.loop_stop()
        client.disconnect()
        raise RuntimeError("MQTT CONNACK timed out")
    if state["connect_reason"] >= 128:
        client.loop_stop()
        client.disconnect()
        raise RuntimeError(f"MQTT connection was rejected with reason {state['connect_reason']}")
    return client, state


def telemetry_payload(sensor: dict[str, Any], firmware: str) -> bytes:
    temperature = sensor["metric"] == "TEMPERATURE"
    document = {
        "messageId": str(uuid.uuid4()),
        "observedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "firmwareVersion": firmware,
        "readings": [
            {
                "sensorId": sensor["id"],
                "metric": sensor["metric"],
                "value": 4 if temperature else 50,
                "unit": "C" if temperature else "PERCENT",
                "quality": "GOOD",
            }
        ],
    }
    return json.dumps(document, separators=(",", ":")).encode()


def publish_all(clients: list[tuple[mqtt.Client, dict[str, Any]]], devices: list[dict[str, Any]], firmware: str) -> int:
    pending: list[tuple[Any, dict[str, Any]]] = []
    for (client, state), device in zip(clients, devices, strict=True):
        result = client.publish(
            device["credential"]["topic"], telemetry_payload(device["sensor"], firmware), qos=1, retain=False
        )
        if result.rc != mqtt.MQTT_ERR_SUCCESS:
            raise RuntimeError(f"MQTT publish was not queued (client result {result.rc})")
        pending.append((result, state))

    deadline = time.monotonic() + 60
    for info, state in pending:
        remaining = max(0.1, deadline - time.monotonic())
        info.wait_for_publish(timeout=remaining)
        while info.mid not in state["publish_reasons"] and time.monotonic() < deadline:
            time.sleep(0.01)
    return sum(
        1
        for info, state in pending
        if info.is_published() and state["publish_reasons"].get(info.mid, 255) < 128
    )


def query_counts(root: Path, env_file: Path, compose_file: Path, firmware: str) -> tuple[int, int, int]:
    if not re.fullmatch(r"mqtt-load-[a-z0-9-]+", firmware):
        raise ValueError("Unsafe firmware marker")
    sql = (
        "SELECT COUNT(*),COALESCE(SUM(tm.status='ACCEPTED'),0),"
        "(SELECT COUNT(*) FROM sensor_reading sr JOIN telemetry_message tm2 "
        "ON tm2.id=sr.telemetry_message_id WHERE tm2.firmware_version='" + firmware + "') "
        "FROM telemetry_message tm WHERE tm.firmware_version='" + firmware + "';"
    )
    command = [
        "docker",
        "compose",
        "--env-file",
        str(env_file),
        "-f",
        str(compose_file),
        "exec",
        "-T",
        "mysql",
        "sh",
        "-ec",
        'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)"; export MYSQL_PWD; exec mysql -N -B -uroot xianzhi -e "$1"',
        "sh",
        sql,
    ]
    result = subprocess.run(command, cwd=root, capture_output=True, text=True, timeout=30, check=False)
    if result.returncode != 0:
        raise RuntimeError("Database verification command failed")
    fields = result.stdout.strip().split("\t")
    if len(fields) != 3:
        raise RuntimeError("Database verification returned an unexpected result")
    return tuple(int(field) for field in fields)  # type: ignore[return-value]


def wait_for_counts(
    root: Path, env_file: Path, compose_file: Path, firmware: str, expected: int, timeout: float
) -> tuple[int, int, int]:
    deadline = time.monotonic() + timeout
    counts = (0, 0, 0)
    while time.monotonic() < deadline:
        counts = query_counts(root, env_file, compose_file, firmware)
        if counts == (expected, expected, expected):
            return counts
        time.sleep(2)
    return counts


def login_admin(api: ApiClient, password_file: Path) -> str:
    password = password_file.read_text(encoding="utf-8-sig").strip()
    if not password:
        raise RuntimeError("Administrator password file is empty")
    for attempt in range(2):
        try:
            session = api.call(
                "POST", "/api/v1/auth/login", body={"identifier": "admin", "password": password}
            )
            return str(session["accessToken"])
        except ApiError as error:
            if error.status != 429 or attempt == 1:
                raise
            try:
                delay = int(error.retry_after or "0")
            except ValueError as exception:
                raise RuntimeError("Login rate limit returned an invalid Retry-After header") from exception
            if delay < 1 or delay > 65:
                raise RuntimeError("Login rate limit cannot be retried within the acceptance window")
            print(f"Login rate limit is active; retrying after {delay} seconds.", flush=True)
            time.sleep(delay + 1)
    raise RuntimeError("Administrator login failed")


def provision_devices(api: ApiClient, count: int, run_id: str, user_ids: list[str]) -> list[dict[str, Any]]:
    devices: list[dict[str, Any]] = []
    zone_kinds = ["CHILL", "FRESH", "VARIABLE", "FREEZE", "CHILL", "FRESH"]
    users_needed = math.ceil(count / 48)
    for user_index in range(users_needed):
        short_id = re.sub(r"[^a-z0-9]", "", run_id)[-10:]
        username = f"mqttload_{short_id}_{user_index + 1}"
        session = api.call(
            "POST",
            "/api/v1/auth/register",
            body={
                "username": username,
                "email": f"{username}@load.invalid",
                "password": secrets.token_urlsafe(24),
                "displayName": "MQTT production acceptance",
            },
            expected=(201,),
        )
        user_ids.append(str(session["user"]["id"]))
        token = str(session["accessToken"])
        zones = [
            {
                "kind": kind,
                "name": f"Load zone {zone_index + 1}",
                "temperatureSensorCount": 4,
                "humiditySensorCount": 4,
            }
            for zone_index, kind in enumerate(zone_kinds)
        ]
        fridge = api.call(
            "POST",
            "/api/v1/onboarding/initialize",
            body={"fridgeName": "MQTT load fridge", "zones": zones},
            token=token,
            idempotency_key=True,
        )
        available_sensors: list[dict[str, Any]] = []
        for zone in fridge["zones"]:
            sensors = api.call("GET", f"/api/v1/zones/{zone['id']}/sensors", token=token)
            available_sensors.extend(sensors)
        user_target = min(48, count - len(devices))
        for sensor in available_sensors[:user_target]:
            ordinal = len(devices) + 1
            device = api.call(
                "POST",
                f"/api/v1/zones/{sensor['zoneId']}/devices",
                body={"name": f"MQTT load device {ordinal}", "type": "PHYSICAL"},
                token=token,
                idempotency_key=True,
            )
            api.call(
                "POST",
                f"/api/v1/devices/{device['id']}/sensors",
                body={"slotId": sensor["id"], "name": f"Load sensor {ordinal}", "externalKey": f"load-{ordinal}"},
                token=token,
                idempotency_key=True,
            )
            devices.append({"credential": device["credential"], "sensor": sensor})
            if len(devices) % 10 == 0 or len(devices) == count:
                print(f"Provisioned {len(devices)}/{count} devices.", flush=True)
    return devices


def delete_users(api: ApiClient, admin_token: str, user_ids: list[str]) -> None:
    for user_id in user_ids:
        api.call(
            "DELETE",
            f"/api/v1/admin/users/{user_id}",
            token=admin_token,
            idempotency_key=True,
        )


def close_clients(clients: list[tuple[mqtt.Client, dict[str, Any]]]) -> None:
    for client, _state in clients:
        try:
            client.disconnect()
        except Exception:
            pass
        try:
            client.loop_stop()
        except Exception:
            pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", default=".env.prod")
    parser.add_argument("--compose-file", default="compose.prod.yaml")
    parser.add_argument("--admin-password-file", default="secrets/smoke_admin_password")
    parser.add_argument("--devices", type=int, default=100)
    parser.add_argument("--mqtt-path", default="/mqtt")
    parser.add_argument("--mqtt-port", type=int, default=443)
    parser.add_argument("--connect-timeout", type=float, default=20)
    parser.add_argument("--database-timeout", type=float, default=120)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parents[2]
    env_file = (root / args.env_file).resolve()
    compose_file = (root / args.compose_file).resolve()
    password_file = (root / args.admin_password_file).resolve()
    if args.devices != 100:
        raise RuntimeError("Production acceptance requires exactly 100 devices")
    values = load_env(env_file)
    domain = values.get("APP_DOMAIN", "").strip()
    if not domain:
        raise RuntimeError("APP_DOMAIN is missing")
    insecure_tls = values.get("SMOKE_INSECURE_TLS") == "true" or values.get("CADDY_TLS_MODE") == "internal"
    api = ApiClient(f"https://{domain}", insecure_tls)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + secrets.token_hex(3)
    firmware = "mqtt-load-" + run_id
    user_ids: list[str] = []
    devices: list[dict[str, Any]] = []
    clients: list[tuple[mqtt.Client, dict[str, Any]]] = []
    users_deleted = False
    admin_token = ""
    print(f"Starting MQTT QoS 1 acceptance run {run_id}.", flush=True)
    try:
        admin_token = login_admin(api, password_file)
        devices = provision_devices(api, args.devices, run_id, user_ids)
        for index, device in enumerate(devices, start=1):
            clients.append(
                connect_client(
                    device["credential"], domain, args.mqtt_port, args.mqtt_path, insecure_tls, args.connect_timeout
                )
            )
            if index % 10 == 0 or index == args.devices:
                print(f"Connected {index}/{args.devices} MQTT clients.", flush=True)

        acknowledgements = publish_all(clients, devices, firmware)
        counts = wait_for_counts(root, env_file, compose_file, firmware, args.devices, args.database_timeout)
        print(
            f"QoS1 acknowledgements={acknowledgements}; telemetry_message={counts[0]}; "
            f"accepted={counts[1]}; sensor_reading={counts[2]}.",
            flush=True,
        )
        if acknowledgements != args.devices or counts != (args.devices, args.devices, args.devices):
            raise RuntimeError("MQTT QoS 1 acceptance counts do not match")

        delete_users(api, admin_token, user_ids)
        users_deleted = True
        revoked_firmware = firmware + "-revoked"
        result = clients[0][0].publish(
            devices[0]["credential"]["topic"],
            telemetry_payload(devices[0]["sensor"], revoked_firmware),
            qos=1,
            retain=False,
        )
        try:
            result.wait_for_publish(timeout=5)
        except RuntimeError:
            pass
        time.sleep(2)
        revoked_counts = query_counts(root, env_file, compose_file, revoked_firmware)
        if revoked_counts != (0, 0, 0):
            raise RuntimeError("A soft-deleted user's connected device could still publish telemetry")

        close_clients(clients)
        clients.clear()
        rejected_client, rejected_state = new_mqtt_client(
            devices[0]["credential"], domain, args.mqtt_port, args.mqtt_path, insecure_tls
        )
        try:
            rejected_client.connect(domain, args.mqtt_port, keepalive=30)
            rejected_client.loop_start()
            if not rejected_state["connect_event"].wait(args.connect_timeout):
                raise RuntimeError("Revoked device authentication check timed out")
            if rejected_state["connect_reason"] < 128:
                raise RuntimeError("A soft-deleted user's device could still authenticate")
        finally:
            try:
                rejected_client.disconnect()
            except Exception:
                pass
            rejected_client.loop_stop()

        print(
            "MQTT production acceptance passed: 100 credentials, WSS, QoS 1, persistence, "
            "and user-revocation checks are all valid; test users were soft-deleted.",
            flush=True,
        )
        return 0
    finally:
        close_clients(clients)
        if user_ids and not users_deleted and admin_token:
            try:
                delete_users(api, admin_token, user_ids)
                print("Temporary MQTT test users were soft-deleted after the interrupted run.", flush=True)
            except Exception as cleanup_error:
                print(f"WARNING: temporary-user cleanup failed ({type(cleanup_error).__name__}).", file=sys.stderr)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("MQTT acceptance was interrupted.", file=sys.stderr)
        raise SystemExit(130)
    except Exception as error:
        print(f"MQTT acceptance failed: {error}", file=sys.stderr)
        raise SystemExit(1)
