#!/usr/bin/env python3
"""Read-only live MQTT telemetry observer for the production WSS endpoint."""

from __future__ import annotations

import argparse
import json
import ssl
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

try:
    import paho.mqtt.client as mqtt
except ImportError as exc:  # pragma: no cover - operator guidance
    raise SystemExit(
        "缺少 paho-mqtt。请先执行：python -m pip install --target .tmp/mqtt-watch -r infra/load/requirements.txt"
    ) from exc


TOPIC = "smart-fridge/v1/+/telemetry"


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


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="实时只读订阅鲜知生产 MQTT 遥测")
    parser.add_argument("--device-id", help="只显示指定设备 UUID；Broker 端仍使用受控通配订阅")
    parser.add_argument("--compact", action="store_true", help="每条消息单行输出")
    parser.add_argument("--url", help="覆盖 MQTT WSS URL，例如 wss://localhost/mqtt")
    parser.add_argument("--duration-seconds", type=int, default=0, help="指定秒数后自动退出；0 表示持续运行")
    parser.add_argument("--once", action="store_true", help="收到第一条符合筛选条件的消息后退出")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parents[2]
    env_path = root / ".env.prod"
    password_path = root / "secrets" / "mqtt_service_password"
    if not env_path.is_file():
        raise SystemExit(f"找不到生产环境文件：{env_path}")
    if not password_path.is_file():
        raise SystemExit(f"找不到 MQTT 服务密码文件：{password_path}")

    values = load_env(env_path)
    domain = values.get("APP_DOMAIN", "").strip()
    if not domain and not args.url:
        raise SystemExit(".env.prod 缺少 APP_DOMAIN")
    public_url = args.url or values.get("MQTT_PUBLIC_BROKER_URL") or f"wss://{domain}/mqtt"
    parsed = urlparse(public_url)
    if parsed.scheme not in {"wss", "ws"} or not parsed.hostname:
        raise SystemExit(f"无效的 MQTT WebSocket URL：{public_url}")

    internal_tls = values.get("CADDY_TLS_MODE", "").lower() == "internal"
    host = parsed.hostname
    port = parsed.port or (443 if parsed.scheme == "wss" else 80)
    path = parsed.path or "/mqtt"
    username = values.get("MQTT_SERVICE_USERNAME", "service")
    password = password_path.read_text(encoding="utf-8-sig").strip()
    if not password:
        raise SystemExit("MQTT 服务密码文件为空")

    client = mqtt.Client(
        callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"xianzhi-observer-{uuid.uuid4().hex[:12]}",
        protocol=mqtt.MQTTv5,
        transport="websockets",
    )
    connected = threading.Event()
    completed = threading.Event()
    connection_result = {"code": None}
    client.username_pw_set(username, password)
    client.ws_set_options(path=path)
    if parsed.scheme == "wss":
        client.tls_set(cert_reqs=ssl.CERT_NONE if internal_tls else ssl.CERT_REQUIRED)
        if internal_tls:
            client.tls_insecure_set(True)

    def on_connect(
        connected_client: mqtt.Client,
        _userdata: Any,
        _flags: Any,
        reason: Any,
        _properties: Any,
    ) -> None:
        code = reason_value(reason)
        connection_result["code"] = code
        connected.set()
        if code >= 128:
            print(f"MQTT 连接被拒绝，reason={code}", file=sys.stderr, flush=True)
            connected_client.disconnect()
            return
        result, _message_id = connected_client.subscribe(TOPIC, qos=1)
        if result != mqtt.MQTT_ERR_SUCCESS:
            print(f"订阅提交失败，result={result}", file=sys.stderr, flush=True)
            connected_client.disconnect()
            return
        print(
            f"已连接 {public_url}，只读订阅 {TOPIC}（Ctrl+C 停止；凭据不会输出）",
            flush=True,
        )

    def on_subscribe(
        _client: mqtt.Client,
        _userdata: Any,
        _message_id: int,
        reason_codes: Any,
        _properties: Any,
    ) -> None:
        codes = [reason_value(value) for value in reason_codes]
        if any(value >= 128 for value in codes):
            print(f"订阅被 Broker 拒绝，reason={codes}", file=sys.stderr, flush=True)

    def on_message(_client: mqtt.Client, _userdata: Any, message: mqtt.MQTTMessage) -> None:
        segments = message.topic.split("/")
        device_id = segments[2] if len(segments) == 4 else "unknown"
        if args.device_id and device_id.lower() != args.device_id.lower():
            return
        payload_text = message.payload.decode("utf-8", errors="replace")
        try:
            payload: Any = json.loads(payload_text)
            rendered = json.dumps(payload, ensure_ascii=False, separators=(",", ":") if args.compact else None, indent=None if args.compact else 2)
        except json.JSONDecodeError:
            rendered = payload_text
        received_at = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
        print(f"\n[{received_at}] topic={message.topic} qos={message.qos} retain={message.retain}")
        print(rendered, flush=True)
        if args.once:
            completed.set()

    client.on_connect = on_connect
    client.on_subscribe = on_subscribe
    client.on_message = on_message

    try:
        client.connect(host, port, keepalive=60)
        client.loop_start()
        if not connected.wait(15):
            raise RuntimeError("MQTT CONNACK 等待超时")
        if connection_result["code"] is None or connection_result["code"] >= 128:
            return 1
        if args.once:
            completed.wait()
        elif args.duration_seconds > 0:
            completed.wait(args.duration_seconds)
        else:
            while True:
                time.sleep(1)
    except KeyboardInterrupt:
        print("\n已停止 MQTT 实时观测。", flush=True)
    finally:
        try:
            client.disconnect()
        except Exception:
            pass
        client.loop_stop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
