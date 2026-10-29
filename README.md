# Boundary — Cricket Live Scoreboard

A Spring Boot cricket scorebook with a responsive dashboard, match setup, scorecards, player statistics, and real-time updates. The app starts with an empty scorebook: it does not seed fixtures or simulate deliveries. Matches and scores come from the scorer's input.

## Project details

Boundary is a live cricket scoring application for setting up matches, entering deliveries, and following scorecards in real time. It is designed around scorer-provided data: there are no demo fixtures, generated players, or automatic score simulations.

### Features

- Create matches with custom competition, venue, teams, batting order, player lineups, and overs per innings.
- Leave a match upcoming or start scoring immediately.
- Record batter runs (0–6) and wickets; the scorecard tracks team totals, overs, batter and bowler statistics, recent deliveries, and match status.
- Follow live score changes in the dashboard using Server-Sent Events (SSE).
- Search and filter the scorebook, and view match summaries and scorecards.
- Persist matches in MongoDB so scores remain available after the app restarts.

### Technology and architecture

| Area | Technology | Responsibility |
| --- | --- | --- |
| Frontend | React 19, React DOM, esbuild | Builds the responsive dashboard bundle; Spring Boot serves the resulting static assets |
| Backend | Java 21, Spring Boot 3.5 | Provides REST endpoints, validates requests, applies scoring rules, and publishes score updates |
| Persistence | Spring Data MongoDB, MongoDB 8 | Stores versioned match state and scorecards |
| Live updates | Server-Sent Events | Sends an initial snapshot and broadcasts score changes to connected clients on the app instance |
| Build | Maven, frontend-maven-plugin, npm | Builds the Java application and installs/builds the frontend; there is no Vite server |

The request flow is **React dashboard or API client → Spring MVC controller → scoring service → MongoDB repository**. After a successful score update, the service publishes the new match snapshot to SSE subscribers. Optimistic versioned writes detect concurrent updates; a stale update returns `409 Conflict` rather than silently overwriting another score.

### Main source locations

- `src/main/frontend.jsx` — React dashboard source.
- `src/main/java/com/scoreboard/cricket/api/MatchController.java` — REST and SSE endpoints.
- `src/main/java/com/scoreboard/cricket/service/ScoreService.java` — match operations, validation, and score updates.
- `src/main/java/com/scoreboard/cricket/service/MatchState.java` — match state and innings scoring rules.
- `src/main/java/com/scoreboard/cricket/service/MatchRepository.java` — MongoDB repository.
- `src/test/java/com/scoreboard/cricket/service/ScoreServiceTest.java` — scoring service tests.
- `postman/Cricket Live API.postman_collection.json` — importable Postman API collection.

## Requirements

- Java 21
- Maven 3.9+
- MongoDB 8+ (or Docker Compose)

The Maven build downloads a pinned Node/npm toolchain and builds the React dashboard with esbuild. No Vite or separate frontend development server is used.

## Run

Start a local MongoDB with Docker Compose:

```powershell
docker compose up -d mongodb
```

Then start the app:

```powershell
mvn spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). React UI source lives in `src/main/frontend.jsx`; Maven bundles it to the static assets Spring Boot serves. To rebuild the frontend directly, run `npm ci` followed by `npm run build`. Match documents are stored in the MongoDB `cricket_live.matches` collection and remain available after app restarts. Configure another MongoDB deployment by setting `MONGODB_URI`, for example `mongodb://localhost:27017/cricket_live`.

Create a match with its teams, lineups, venue, batting order, and overs per innings (1–1,000). Start it immediately or leave it upcoming and start it later. In a live scorecard, record runs 0–6 or a wicket. Score changes are delivered to connected dashboards over Server-Sent Events; there are no automatic or randomly generated scores.

## API

An importable Postman collection with REST assertions and an SSE request is available at [postman/Cricket Live API.postman_collection.json](./postman/Cricket%20Live%20API.postman_collection.json). Import it into Postman and set its `baseUrl` collection variable to the URL where the backend is running. The default local URL when following the Run instructions is `http://localhost:8080`; if the app is running on another port, use that port instead (for example, `http://localhost:18084`). Run the **REST API** folder in order to exercise the requests below. It creates a uniquely named one-over test match; remove that test match from MongoDB afterward if you do not want to keep it.

### Postman test cases

For manual testing, use the following requests in order. For the match-specific requests, replace `{matchId}` with the `id` returned by **Create an upcoming match**. Add `Content-Type: application/json` to requests with JSON bodies.

| # | Request | Body / setup | Expected result |
| --- | --- | --- | --- |
| 1 | `GET {{baseUrl}}/api/matches` | None | `200 OK`; returns a JSON array, possibly empty |
| 2 | `POST {{baseUrl}}/api/matches` | Use the upcoming-match JSON below | `201 Created`; returns the match and its `id`, with status `UPCOMING` |
| 3 | `GET {{baseUrl}}/api/matches/{matchId}` | Use the ID from step 2 | `200 OK`; returns that match |
| 4 | `POST {{baseUrl}}/api/matches/{matchId}/start` | None | `200 OK`; status changes to `LIVE` and opening batters are set |
| 5 | `POST {{baseUrl}}/api/matches/{matchId}/deliveries` | `{"runs":4,"wicket":false}` | `200 OK`; batting team and striker each have 4 runs, and the striker has one four |
| 6 | `POST {{baseUrl}}/api/matches/{matchId}/deliveries` | `{"runs":0,"wicket":true}` | `200 OK`; with this two-player batting lineup, the innings is `COMPLETED` |
| 7 | `POST {{baseUrl}}/api/matches/{matchId}/deliveries` | `{"runs":1,"wicket":false}` after step 6 | `409 Conflict`; completed matches cannot accept deliveries |
| 8 | `GET {{baseUrl}}/api/matches/00000000-0000-0000-0000-000000000000` | None | `404 Not Found`; the match ID does not exist |
| 9 | `POST {{baseUrl}}/api/matches` | Use the create body below but give `homePlayers` only one player | `400 Bad Request`; a lineup must contain 2–11 players |

Use this body for step 2:

```json
{
  "competition": "Postman Test Match",
  "venue": "Test Ground",
  "homeTeam": "Falcons",
  "awayTeam": "Hawks",
  "homePlayers": ["Alex Batter", "Sam Batter"],
  "awayPlayers": ["Taylor Bowler", "Jordan Bowler"],
  "battingFirst": "HOME",
  "maxOvers": 1,
  "startImmediately": false
}
```

For step 9, use the same body but change `homePlayers` to `["Alex Batter"]`.

#### Test live score updates (SSE)

Run `GET {{baseUrl}}/api/matches/stream` as a separate request with `Accept: text/event-stream`. The connection stays open. It should first emit an `event:snapshot`. While it is connected, record a delivery for a live match using another Postman request; the stream should then emit an `event:score` containing the updated score. Stop the stream request when finished.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/matches` | List fixtures and current scorecards |
| `POST` | `/api/matches` | Create a match and provide real teams, players, overs, and batting order |
| `GET` | `/api/matches/{id}` | Fetch one match |
| `POST` | `/api/matches/{id}/start` | Start an upcoming match |
| `POST` | `/api/matches/{id}/deliveries` | Record a delivery, e.g. `{"runs":4,"wicket":false}` |
| `GET` | `/api/matches/stream` | Subscribe to initial and live score events (SSE) |

Create matches with this JSON shape:

```json
{
  "competition": "Saturday League Final",
  "venue": "Riverside Ground",
  "homeTeam": "Falcons",
  "awayTeam": "Hawks",
  "homePlayers": ["Player One", "Player Two", "Player Three"],
  "awayPlayers": ["Player Four", "Player Five"],
  "battingFirst": "HOME",
  "maxOvers": 20,
  "startImmediately": true
}
```

Each lineup must contain 2–11 distinct player names; `battingFirst` is `HOME` or `AWAY`. Manual delivery runs are limited to 0–6. Only live matches accept deliveries; invalid state changes return `409 Conflict`, and unknown match IDs return `404`.

## Scaling notes

MongoDB stores complete match state, including scorecards and ball history. Versioned writes detect concurrent updates and return `409 Conflict` so a scorer can refresh rather than silently overwrite another update. For multiple app instances, MongoDB is shared but SSE events are currently sent only to browsers connected to the instance that handled the update; use a shared broker (for example Redis Streams or Kafka) and an SSE-capable load balancer to broadcast events between instances.
