#!/usr/bin/env bash
set -euo pipefail

STANZA="${PGBACKREST_STANZA:-marketmono}"
TEXTFILE_DIR="${NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/node_exporter/textfile_collector}"
RESTORE_STAMP_FILE="${RESTORE_REHEARSAL_STAMP_FILE:-/var/lib/marketmono/restore-rehearsal-last-success}"
OUTPUT_FILE="${TEXTFILE_DIR}/pgbackrest.prom"
TMP_FILE="${OUTPUT_FILE}.$$"

mkdir -p "${TEXTFILE_DIR}"

if ! INFO_JSON="$(pgbackrest --stanza="${STANZA}" info --output=json 2>/dev/null)"; then
  cat > "${TMP_FILE}" <<METRICS
# HELP marketmono_pgbackrest_info_success 1 when pgBackRest info was collected successfully, otherwise 0.
# TYPE marketmono_pgbackrest_info_success gauge
marketmono_pgbackrest_info_success{stanza="${STANZA}"} 0
METRICS
  mv "${TMP_FILE}" "${OUTPUT_FILE}"
  exit 0
fi

INFO_JSON="${INFO_JSON}" python3 - "${STANZA}" "${RESTORE_STAMP_FILE}" > "${TMP_FILE}" <<'PY'
import datetime as dt
import json
import os
import pathlib
import sys

stanza = sys.argv[1]
restore_stamp_file = pathlib.Path(sys.argv[2])
payload = json.loads(os.environ.get("INFO_JSON", "[]"))
stanza_info = payload[0] if payload else {}
backups = stanza_info.get("backup", []) or []
latest = backups[-1] if backups else {}

def parse_epoch(value):
    if value is None:
        return 0
    if isinstance(value, (int, float)):
        return int(value)
    value = str(value).replace("Z", "+00:00")
    try:
        return int(dt.datetime.fromisoformat(value).timestamp())
    except ValueError:
        return 0

timestamp = latest.get("timestamp", {}) or {}
info = latest.get("info", {}) or {}
size = info.get("repository", {}).get("size", 0) if isinstance(info.get("repository"), dict) else 0
backup_label = latest.get("label", "none")
backup_type = latest.get("type", "none")
backup_stop = parse_epoch(timestamp.get("stop"))
backup_start = parse_epoch(timestamp.get("start"))
backup_duration = max(backup_stop - backup_start, 0) if backup_stop and backup_start else 0
restore_epoch = 0

if restore_stamp_file.exists():
    restore_epoch = parse_epoch(restore_stamp_file.read_text(encoding="utf-8").strip())

print("# HELP marketmono_pgbackrest_info_success 1 when pgBackRest info was collected successfully, otherwise 0.")
print("# TYPE marketmono_pgbackrest_info_success gauge")
print(f'marketmono_pgbackrest_info_success{{stanza="{stanza}"}} 1')
print("# HELP marketmono_pgbackrest_latest_backup_timestamp_seconds Unix timestamp of the latest pgBackRest backup stop time.")
print("# TYPE marketmono_pgbackrest_latest_backup_timestamp_seconds gauge")
print(f'marketmono_pgbackrest_latest_backup_timestamp_seconds{{stanza="{stanza}",label="{backup_label}",type="{backup_type}"}} {backup_stop}')
print("# HELP marketmono_pgbackrest_latest_backup_duration_seconds Duration of the latest pgBackRest backup in seconds.")
print("# TYPE marketmono_pgbackrest_latest_backup_duration_seconds gauge")
print(f'marketmono_pgbackrest_latest_backup_duration_seconds{{stanza="{stanza}",label="{backup_label}",type="{backup_type}"}} {backup_duration}')
print("# HELP marketmono_pgbackrest_latest_backup_size_bytes Repository size of the latest pgBackRest backup in bytes.")
print("# TYPE marketmono_pgbackrest_latest_backup_size_bytes gauge")
print(f'marketmono_pgbackrest_latest_backup_size_bytes{{stanza="{stanza}",label="{backup_label}",type="{backup_type}"}} {int(size or 0)}')
print("# HELP marketmono_restore_rehearsal_last_success_timestamp_seconds Unix timestamp of the latest approved restore rehearsal proof.")
print("# TYPE marketmono_restore_rehearsal_last_success_timestamp_seconds gauge")
print(f'marketmono_restore_rehearsal_last_success_timestamp_seconds{{stanza="{stanza}"}} {restore_epoch}')
PY

mv "${TMP_FILE}" "${OUTPUT_FILE}"