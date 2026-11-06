# pokemon-webshop

this is a pokemon webshop which i'm creating with a few friends of mine. here you can also collect cards, add them to certain lists and keep track of the price market what your cards are worth accoording to card market

The repository has a Kotlin + Spring Boot backend in `backend/` and a Vue 3 + Vite frontend in `frontend/`.

## Running

Supporting services:

```sh
docker compose up                 # Postgres, Adminer and Mailpit only
docker compose --profile full up  # also builds and starts the backend
```

| Service | URL                   |
|---------|-----------------------|
| Adminer | http://localhost:8081 |
| Mailpit | http://localhost:8025 |

Defaults work out of the box. To change a setting, copy `.env.example` to `.env` in the repo root (see [Environment variables](#environment-variables)).

Backend (runs on port 8080 with the `dev` profile, no Docker needed):

```sh
cd backend
./gradlew bootRun
```

Swagger UI is at http://localhost:8080/api/swagger-ui.html and the H2 console at http://localhost:8080/api/h2-console.

To run against the Postgres container instead:

```sh
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

Frontend (runs on port 5173 and proxies `/api` to the backend):

```sh
cd frontend
npm install
npm run dev
```

## Environment variables

Settings and API keys live in `.env` in the repo root. Git ignores it, so each developer keeps their own copy. Docker Compose and the backend both read it, and real environment variables take priority over it.

To add a new API key:

1. Add it to `.env` with the real value, and to `.env.example` with an empty value.
2. Reference it from `application.yml`, for example `${FOO_API_KEY:}`.
3. Pass it to the `backend` service in `docker-compose.yml`, because containers don't receive `.env` automatically.

Keep API keys on the backend. Anything in a `VITE_` variable ends up in the browser.

## Card data

The card catalog comes from four public APIs that need no key. Their base URLs are in `.env.example`.

| Game | Source | Prices |
|------|--------|--------|
| Pokémon | [TCGdex](https://tcgdex.dev) | Cardmarket, EUR |
| Magic: The Gathering | [Scryfall](https://scryfall.com/docs/api) | Cardmarket, EUR |
| Disney Lorcana | [Lorcast](https://lorcast.com) | TCGplayer, USD |
| One Piece Card Game | [OPTCG API](https://optcgapi.com) | TCGplayer, USD |

Every sync also fetches the [ECB reference rate](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html). Customers choose EUR or USD in their profile, and card prices are converted for display only. Shop prices stay in EUR.

Customer requests never reach these APIs. A job imports everything into our own database at the time in `CATALOG_SYNC_CRON` (03:00 Dutch time by default, `-` switches it off), and an admin can start a game by hand at `/admin/sync`. The job is off in `dev`, because the in-memory database starts empty anyway.

A full sync takes about an hour and a half for Pokémon, because TCGdex needs a request per card, and under a minute for each other game. Keep the computer awake: a sync slows to a crawl while it sleeps. To keep the data between restarts, run on the `postgres` profile. Switch off the SQL log for a sync, or it floods the console:

```sh
SPRING_PROFILES_ACTIVE=postgres SPRING_JPA_SHOW_SQL=false ./gradlew bootRun
```

Only the `dev` profile has demo accounts. To reach `/admin` on another profile, register an account and run this in Adminer:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = '<email>';
```

Run one backend per database during a sync: a second instance that starts up marks the running sync as failed.

## Profiles

| Profile  | Database                                    | ddl-auto    | show-sql | Flyway |
|----------|---------------------------------------------|-------------|----------|--------|
| dev      | H2 in-memory, `MODE=PostgreSQL`             | create-drop | true     | off    |
| postgres | PostgreSQL on `localhost:5432`              | update      | true     | off    |
| docker   | PostgreSQL on host `postgres`, port 5432    | update      | false    | off    |
| prod     | PostgreSQL from environment variables       | validate    | false    | on     |

`postgres`, `docker` and `prod` read `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. `prod` has no defaults, so all three must be set.

## Conventions

### Backend

- Requests flow Controller → Orchestrator → Service and Repository. Controllers only forward to an orchestrator. Orchestrators decide what happens and assemble the result. Services do the logic and calculations. Repositories do the data access.
- Name files per feature: `FooController`, `FooOrchestrator`, `FooService`, `FooRepository`, `domain/Foo.kt`, and `dto/FooDtos.kt` holding all of that feature's DTOs.
- The `/api` prefix comes from the context path, so controller mappings start at `/v1/...`. The public path is `/api/v1/...`.
- Every change to an entity needs a Flyway migration in `db/migration/` (`V1__description.sql`, `V2__...`) before it goes to prod. Local profiles let Hibernate manage the schema, prod does not.

### Frontend

- Routed pages are `views/FooView.vue`. A section with child routes gets its own subfolder in `views/`.
- All backend calls go through `services/api.js` with base URL `/api/v1`. Create that file when the first call is needed.
- Stores are plain composables such as `useSession()` exported from `stores/session.js`.
- Components use the CSS variables from `main.css`, never raw colours.
