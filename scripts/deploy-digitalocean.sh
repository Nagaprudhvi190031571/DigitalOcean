#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC="${ROOT}/.do/app.yaml"
APP_NAME="llm-proxy"

if ! command -v doctl >/dev/null 2>&1; then
  echo "Install doctl: https://docs.digitalocean.com/reference/doctl/how-to/install/"
  exit 1
fi

if ! doctl account get >/dev/null 2>&1; then
  echo "Authenticate first: doctl auth init"
  exit 1
fi

APP_ID="$(doctl apps list --format ID,Spec.Name --no-header | awk -v name="${APP_NAME}" '$2 == name { print $1; exit }')"

if [[ -n "${APP_ID}" ]]; then
  echo "Updating existing app ${APP_ID}..."
  doctl apps update "${APP_ID}" --spec "${SPEC}" --wait
  doctl apps get "${APP_ID}" --format LiveURL --no-header
else
  echo "Creating new app from ${SPEC}..."
  doctl apps create --spec "${SPEC}" --wait
fi
