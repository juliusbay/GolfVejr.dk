# GolfVejr.dk

A dynamic weather assessment tool for golfers in Denmark. GolfVejr.dk fetches live forecasts from MET Norway, runs each hour through a multi-factor scoring algorithm, and delivers a clear **0–100 golf suitability score** so you can decide at a glance whether today — or any of the next nine days — is worth teeing off.

---

## Features

- **0–100 Golf Score** — Each hourly slot is scored across three weighted dimensions: temperature (35 pts), wind speed & gusts (35 pts), and precipitation (30 pts). The daily score is a time-weighted average with customisable emphasis.
- **Time-of-Day Preferences** — A segmented control lets you filter scoring by *Morning* (06–12), *Afternoon* (12–18), *Evening* (18–sunset), or *All Day*. Hours outside the selected window are down-weighted so the daily score reflects your actual availability.
- **Best Golf Window** — A sliding four-hour window search identifies the highest-scoring consecutive block of the day and surfaces it as a recommended tee time.
- **Side-by-Side Club Comparison** — A modal overlay lets you pick two clubs and a date, then renders a VS card with scores, status badges, summaries, best windows, and factor tags for both clubs simultaneously.
- **Wind Direction Indicator** — The hourly forecast table shows a rotated arrow next to each wind reading, oriented toward the direction the wind is blowing based on the API's `wind_from_direction` value.
- **Sunset-Aware Scoring** — NOAA solar equations calculate the precise sunset time for each club's coordinates. Post-sunset hours are excluded from the daily average and displayed with a calm "Solnedgang" badge.
- **Interactive Map** — A full-width Leaflet.js map shows every golf club in Denmark as a colour-coded pin (green / amber / red) reflecting tomorrow's conditions, with click-through to the full forecast.
- **Favourites** — Clubs can be starred and are persisted to `localStorage` for one-click access on return visits.
- **45-Minute API Cache** — Forecast responses are cached in memory per coordinate pair to avoid redundant upstream requests, especially important during multi-club comparisons.

---

## Tech Stack

### Backend
| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 |
| ORM | Spring Data JPA / Hibernate |
| Database | H2 (file-based, local) / PostgreSQL-compatible for production |
| HTTP client | `RestTemplate` (Spring Web) |
| Build | Maven |
| Utilities | Lombok, `spring-dotenv` |

### Frontend
| Layer | Technology |
|---|---|
| Language | Vanilla JavaScript (ES2020) |
| Styling | Plain CSS with custom properties |
| Fonts | DM Sans + DM Mono (Google Fonts) |
| Map | Leaflet.js 1.9 + OpenStreetMap tiles |
| Served by | Spring Boot embedded Tomcat (`/static`) |

---

## External APIs

| API | Purpose | Auth |
|---|---|---|
| [MET Norway Yr API](https://api.met.no/) (`locationforecast/2.0/complete`) | Hourly & 6-hourly weather forecasts for any lat/lon in Denmark | None — requires a descriptive `User-Agent` header with contact email per their [terms of service](https://api.met.no/doc/TermsOfService) |
| [OpenStreetMap Overpass API](https://overpass-api.de/) | One-time import of all Danish golf courses (name, coordinates, address, website, phone) | None |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+

### 1. Clone the repository

```bash
git clone https://github.com/your-username/GolfVejr.dk.git
cd GolfVejr.dk
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

Open `.env` and fill in your own values. All keys are documented in `.env.example`. The only required change before running is setting a real contact email in `MET_NORWAY_USER_AGENT` — MET Norway's terms of service require it.

> **Note:** `.env` is git-ignored and will never be committed. Keep your credentials there only.

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The app starts on **http://localhost:8080**.

### 4. Import golf clubs (first run only)

On first startup the database is empty. Hit the import endpoint once to populate all Danish golf courses from OpenStreetMap:

```
POST http://localhost:8080/api/admin/import-clubs
```

You can also inspect the data directly via the H2 console at **http://localhost:8080/db**
(JDBC URL: `jdbc:h2:file:./data/golfvejr_db`, username: `sa`, password: empty).

---

## Deployment

The project is production-ready for zero-config deployment on [Railway](https://railway.app) using **Nixpacks** — no `Dockerfile` required. Railway auto-detects the Maven wrapper and builds a runnable JAR.

1. Push the repository to GitHub.
2. Create a new Railway project and connect the repo.
3. In Railway's *Variables* panel, add each key from `.env.example` with your real values. Railway injects these as environment variables at runtime.
4. For a persistent database, provision a PostgreSQL plugin in Railway and point `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` at the provided connection string.

---

## Project Structure

```
GolfVejr.dk/
├── src/main/java/com/example/golfvejr/
│   ├── Controller/          # REST endpoints (forecast, map data, admin import)
│   ├── DTO/                 # HourlyForecastDTO, DailyGolfAssessmentDTO
│   ├── Model/               # JPA entities + MET Norway JSON model
│   ├── Repository/          # Spring Data repositories
│   └── Service/
│       ├── GolfAssessmentService.java   # Per-hour scoring logic
│       ├── ForecastService.java         # Daily aggregation, sunset, best window
│       ├── YrApiService.java            # MET Norway API client + cache
│       ├── GolfClubImportService.java   # Overpass API import
│       └── MapCacheService.java         # Pre-computed map pin data
├── src/main/resources/
│   ├── application.properties
│   └── static/
│       ├── index.html
│       ├── css/style.css
│       └── js/app.js
├── .env.example
└── pom.xml
```

---

*Weather data provided by [MET Norway](https://api.met.no). Golf course data © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors.*
