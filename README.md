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

Card data and Cardmarket prices come from [TCGdex](https://tcgdex.dev), a public API that needs no key. Its base URL is `TCGDEX_BASE_URL`.

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
