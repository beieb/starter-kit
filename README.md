# Smart Lab on the Web - starter kit

Requirements: JDK 17 or later (tested with JDK 21), Maven 3.9, bash and curl for the tests (Git Bash on Windows).

## Contents

```
pom.xml                  parent project, one module per service
gateway/                 port 8080
  ThingsController       POST/GET /things, GET/DELETE /things/{id}      (TODO: proxy)
  EventsController       POST /events, GET /events/stream (SSE)         (TODO: rules)
  EventHub               SSE connections, keep-alive (@Scheduled)
  TokenFilter            Bearer token, 401
  RestartableTimer       timer for the rules (TaskScheduler)
  ErrorHandler           JSON errors
  static/index.html      minimal dashboard (fetch, EventSource)
thing-lamp/              port 8082
  LampController         /model, /properties, PUT /properties/on, POST /actions/toggle
  LampDescription        Web Thing Model of the lamp
  GatewayClient          registration, events, unregistration (RestClient)
check-routes.sh          tests of the routes and of the API conventions
run.sh, run.ps1, stop.ps1
```

## Run

```bash
./run.sh                      # Ctrl+C to stop
```
```powershell
.\run.ps1                     # then .\stop.ps1
```

Or one service per terminal: `mvn -pl gateway spring-boot:run`, then `mvn -pl thing-lamp spring-boot:run`.

Dashboard: http://localhost:8080/ (token `operator-secret`).

## Tests

```bash
./check-routes.sh
VIEWER=viewer-secret ./check-routes.sh     # with the 403 tests (roles, bonus)
```

With the kit as provided: 19 PASS, 16 FAIL (thermostat, motion sensor, brightness, proxy). At the end, every test must pass.

## Examples

```bash
T="Authorization: Bearer operator-secret"
curl -H "$T" http://localhost:8080/things
curl -H "$T" http://localhost:8080/things/lamp
curl -N "http://localhost:8080/events/stream?token=operator-secret"
curl -X POST http://localhost:8082/actions/toggle
curl -X PUT -H "Content-Type: application/json" -d '{"value":true}' http://localhost:8082/properties/on
```

## Notes

- The rules switch the lamp on with `PUT /properties/on`, not with `toggle`.
- The rules call the things directly, not through the proxy of the gateway (otherwise they would count as manual actions for R4).
- A thing sends its events from a separate thread (see `GatewayClient.emit`).

## To do

1. Modules `thermostat` (8081) and `motion-sensor` (8083), copied from `thing-lamp`; `brightness` and `setBrightness` for the lamp; simulation of the temperature.
2. Proxy of the gateway (`ThingsController`).
3. Rules R1 and R2 (`EventsController.receive`, `RestartableTimer`); R3 and R4 are bonus.
4. Token sent by the things and the dashboard; roles `viewer` / `operator` (bonus).
5. Dashboard: one card per thing, updated in real time.
6. `gateway.yaml` (OpenAPI), as in Lab 3.
