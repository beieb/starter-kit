#!/usr/bin/env bash
# Build and start all the services (logs in logs/). Ctrl+C stops them.
set -e
cd "$(dirname "$0")"

mvn -q -DskipTests package
mkdir -p logs

pids=()
start() {
  java -jar "$1" > "logs/$2.log" 2>&1 &
  pids+=($!)
  echo "started $2 (pid $!)"
}
trap 'echo; echo "stopping..."; kill "${pids[@]}" 2>/dev/null' INT TERM EXIT

start gateway/target/gateway.jar gateway
start thing-lamp/target/thing-lamp.jar lamp
# TODO: thermostat, motion sensor

echo "dashboard: http://localhost:8080/  (token: operator-secret) - Ctrl+C to stop"
wait
