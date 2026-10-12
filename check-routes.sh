#!/usr/bin/env bash
# Tests of the mandatory routes and of the API conventions (bash + curl).
# Usage: ./check-routes.sh
#   GATEWAY   gateway URL                            (default http://localhost:8080)
#   TOKEN     token with write access                (default operator-secret)
#   VIEWER    read-only token for the 403 tests      (default: tests skipped)
#   LAMP_URL  direct URL of the lamp                 (default http://localhost:8082)

GATEWAY=${GATEWAY:-http://localhost:8080}
TOKEN=${TOKEN:-operator-secret}
VIEWER=${VIEWER:-}
LAMP_URL=${LAMP_URL:-http://localhost:8082}

pass=0; fail=0; body=$(mktemp); headers=$(mktemp)
trap 'rm -f "$body" "$headers"' EXIT

# check <expected status> <description> <curl arguments...>
check() {
  local expected=$1 label=$2; shift 2
  local status
  status=$(curl -s -o "$body" -w '%{http_code}' --max-time 5 "$@")
  if [ "$status" = "$expected" ]; then
    pass=$((pass + 1)); printf '  PASS  %-58s %s\n' "$label" "$status"
  else
    fail=$((fail + 1)); printf '  FAIL  %-58s got %s, expected %s  %s\n' "$label" "$status" "$expected" "$(head -c 150 "$body")"
  fi
}

auth=(-H "Authorization: Bearer $TOKEN")
json=(-H "Content-Type: application/json")

echo "== Dashboard and security"
check 200 "GET / (dashboard, public)"                        "$GATEWAY/"
check 401 "GET /things without token"                        "$GATEWAY/things"
check 401 "GET /things with a wrong token"                   -H "Authorization: Bearer wrong" "$GATEWAY/things"
check 200 "GET /things with token"                           "${auth[@]}" "$GATEWAY/things"

echo "== Find: registration and discovery"
for id in thermostat lamp motion; do
  check 200 "GET /things/$id"                                "${auth[@]}" "$GATEWAY/things/$id"
done
check 404 "GET /things/unknown"                              "${auth[@]}" "$GATEWAY/things/unknown"
check 409 "POST /things with an existing id"                 "${auth[@]}" "${json[@]}" -X POST \
      -d "{\"id\":\"lamp\",\"name\":\"Lamp\",\"baseUrl\":\"$LAMP_URL\"}" "$GATEWAY/things"
check 400 "POST /things without id"                          "${auth[@]}" "${json[@]}" -X POST -d '{}' "$GATEWAY/things"
check 200 "GET $LAMP_URL/model (description of a thing)"     "$LAMP_URL/model"

echo "== Access: properties and actions through the gateway (proxy)"
for id in thermostat lamp motion; do
  check 200 "GET /things/$id/properties"                     "${auth[@]}" "$GATEWAY/things/$id/properties"
done
check 200 "GET /things/lamp/properties/on"                   "${auth[@]}" "$GATEWAY/things/lamp/properties/on"
check 404 "GET /things/lamp/properties/unknown"              "${auth[@]}" "$GATEWAY/things/lamp/properties/unknown"
check 200 "PUT /things/lamp/properties/on true"              "${auth[@]}" "${json[@]}" -X PUT -d '{"value":true}' "$GATEWAY/things/lamp/properties/on"
check 200 "PUT /things/lamp/properties/brightness 60"        "${auth[@]}" "${json[@]}" -X PUT -d '{"value":60}' "$GATEWAY/things/lamp/properties/brightness"
check 400 "PUT /things/lamp/properties/brightness 150"       "${auth[@]}" "${json[@]}" -X PUT -d '{"value":150}' "$GATEWAY/things/lamp/properties/brightness"
check 400 "PUT /things/thermostat/properties/temperature"    "${auth[@]}" "${json[@]}" -X PUT -d '{"value":30}' "$GATEWAY/things/thermostat/properties/temperature"
check 400 "PUT /things/thermostat/properties/mode \"turbo\"" "${auth[@]}" "${json[@]}" -X PUT -d '{"value":"turbo"}' "$GATEWAY/things/thermostat/properties/mode"
check 200 "POST /things/lamp/actions/toggle"                 "${auth[@]}" -X POST "$GATEWAY/things/lamp/actions/toggle"
check 200 "POST /things/lamp/actions/setBrightness 80"       "${auth[@]}" "${json[@]}" -X POST -d '{"value":80}' "$GATEWAY/things/lamp/actions/setBrightness"
check 200 "POST /things/thermostat/actions/setTarget 20"     "${auth[@]}" "${json[@]}" -X POST -d '{"value":20}' "$GATEWAY/things/thermostat/actions/setTarget"
check 200 "POST /things/motion/actions/simulateMotion"       "${auth[@]}" -X POST "$GATEWAY/things/motion/actions/simulateMotion"
check 404 "POST /things/lamp/actions/unknown"                "${auth[@]}" -X POST "$GATEWAY/things/lamp/actions/unknown"

echo "== API conventions: registration, unreachable thing, events"
# temporary thing on a port where nothing answers
curl -s -o /dev/null --max-time 5 "${auth[@]}" -X DELETE "$GATEWAY/things/check-tmp"
check 201 "POST /things (temporary thing)"                   -D "$headers" "${auth[@]}" "${json[@]}" -X POST \
      -d '{"id":"check-tmp","name":"Temporary","baseUrl":"http://localhost:9","model":{}}' "$GATEWAY/things"
if tr -d '\r' < "$headers" | grep -qi '^location: .*/things/check-tmp$'; then
  pass=$((pass + 1)); printf '  PASS  %-58s\n' "Location header of the registration"
else
  fail=$((fail + 1)); printf '  FAIL  %-58s missing or wrong\n' "Location header of the registration"
fi
check 502 "GET /things/check-tmp/properties (thing not answering)" "${auth[@]}" "$GATEWAY/things/check-tmp/properties"
check 204 "DELETE /things/check-tmp"                         "${auth[@]}" -X DELETE "$GATEWAY/things/check-tmp"
check 404 "DELETE /things/check-tmp again"                   "${auth[@]}" -X DELETE "$GATEWAY/things/check-tmp"
check 202 "POST /events from a registered thing"             "${auth[@]}" "${json[@]}" -X POST \
      -d '{"thingId":"lamp","type":"check","data":{}}' "$GATEWAY/events"
check 404 "POST /events from an unknown thing"               "${auth[@]}" "${json[@]}" -X POST \
      -d '{"thingId":"unknown","type":"check","data":{}}' "$GATEWAY/events"

echo "== Real time"
stream=$(curl -s -o /dev/null -w '%{http_code} %{content_type}' --max-time 2 "$GATEWAY/events/stream?token=$TOKEN")
case "$stream" in
  "200 text/event-stream"*) pass=$((pass + 1)); printf '  PASS  %-58s %s\n' "GET /events/stream?token=..." "$stream" ;;
  *) fail=$((fail + 1)); printf '  FAIL  %-58s got "%s"\n' "GET /events/stream?token=..." "$stream" ;;
esac
check 401 "GET /events/stream without token"                 --max-time 2 "$GATEWAY/events/stream"

if [ -n "$VIEWER" ]; then
  echo "== Roles (viewer token)"
  check 200 "GET /things with the viewer token"              -H "Authorization: Bearer $VIEWER" "$GATEWAY/things"
  check 403 "PUT a property with the viewer token"           -H "Authorization: Bearer $VIEWER" "${json[@]}" -X PUT -d '{"value":false}' "$GATEWAY/things/lamp/properties/on"
  check 403 "POST an action with the viewer token"           -H "Authorization: Bearer $VIEWER" -X POST "$GATEWAY/things/lamp/actions/toggle"
fi

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
