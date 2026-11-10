# Phase 7: Online preview and automatic deploys

The shop runs online as a closed preview on the owner's VPS: HTTPS with a Let's Encrypt certificate, one shared login
in front of every page, out of search engines, and no online payment. Every push to `main` is tested and, when
everything passes, deployed by GitHub Actions. A version that doesn't start is rolled back by itself. Daily backups,
a restore check, a firewall and SSH with keys only came with it.

## Before you try it

1. **Your own computer:** Docker Desktop stopped during this phase (see Open issues). Quit and reopen Docker Desktop,
   run `docker compose up -d` in the repository, and your backend on 8080 finds Postgres again by itself.
2. **The preview:** the address and the login are in `~/poketopia-preview-login.txt` on your Mac (and in
   `/root/poketopia-preview-login.txt` on the server). The address is `<ip-with-dashes>.sslip.io`. The browser asks
   for the login once; after that the shop works as usual.
3. **Become admin:** register on the preview with your own email address, then on the server:
   `/opt/poketopia/bin/make-admin.sh <your email>`. Do the same for your friend once they registered.
4. **Card data:** the nightly sync at 03:00 fills all four games by itself (Pokémon takes about an hour and a half).
   Then start the product list import at `/admin/producten/import`, and set prices, stock and shipping costs.
5. **Automatic deploys:** add the three GitHub secrets (below), merge this branch into `main` and push. The first run
   builds the images in GitHub's container registry and deploys them.

## What was built

**Server** (the owner's VPS at mc-node in the Netherlands, shared with Minecraft)

- **Docker Compose** (`deploy/compose.yml`) with Postgres 16, the backend on the `prod` profile, Caddy, and Mailpit.
  Only Caddy publishes ports (80, 443 and 443/udp for HTTP/3). Postgres and the backend are only on the internal
  network, Mailpit's page only on `127.0.0.1:8025` of the server. Everything has `restart: unless-stopped`, so it comes
  back after a crash or a reboot. Postgres, the uploaded images (`MEDIA_DIRECTORY`), Mailpit and Caddy's certificates
  live on named volumes that a new version doesn't touch. Container logs rotate at 5 × 10 MB.
- **Memory:** backend 2.6 GB with a 2 GB heap (the Magic sync needs it), Postgres 576 MB, Caddy 192 MB, Mailpit
  128 MB: about 3.5 GB of the 11, as agreed for Minecraft. Idle the shop uses about 0.8 GB.
- **Caddy** (`deploy/Caddyfile`): a certificate from Let's Encrypt that renews by itself, a redirect from HTTP to
  HTTPS, the built frontend with `index.html` for every route of the Vue app, and `/api` to the backend.
  `FORWARD_HEADERS_STRATEGY=native`, so rate limits see the visitor's IP address.
- **Closed preview:** `basic_auth` on every page and on `/api` (one shared login, bcrypt hash in the server's `.env`).
  `robots.txt` (`Disallow: /`) is readable without the login, and every response has `X-Robots-Tag: noindex,
  nofollow`. The backend never sees the preview login: Caddy removes the `Authorization` header. No Stripe key, so
  checkout says online payment isn't set up.
- **Security headers** at the proxy: HSTS (one year, without subdomains), a Content-Security-Policy (scripts, styles,
  fonts and API calls only from the shop, images from any https address because card images come from four sources),
  `X-Frame-Options: DENY`, `nosniff`, a referrer policy and a permissions policy. The `Server` header is removed.
- **Firewall** (ufw): only 22, 80, 443 (TCP and UDP) and Minecraft's 25565 come in. Docker's published ports bypass ufw,
  which is why only Caddy publishes any.
- **SSH with keys only:** `/etc/ssh/sshd_config.d/00-poketopia.conf` switches passwords off and allows root only with
  a key. Lost the key? Log in through mc-node's web console and remove that file.
- **Mail** goes to Mailpit. To read it: `ssh -L 8026:127.0.0.1:8025 root@<server>`, then open
  `http://localhost:8026` (8026 because your own Mailpit uses 8025).

**Deploys**

- **GitHub Actions** (`.github/workflows/deploy.yml`), on every push to `main` and by hand: `./gradlew test` and
  `bootJar`, then `npm ci` and `npm run build`. Only when both pass: two images, pushed to `ghcr.io/emetain/` with the
  commit as tag, then the deploy job. Runs go one at a time in the order of the pushes.
  - `pokemon-webshop-backend`: Temurin 21 JRE with the jar that passed the tests (`deploy/backend.Dockerfile`).
  - `pokemon-webshop-web`: Caddy with the built frontend and the Caddyfile (`deploy/web.Dockerfile`). Its build runs
    `caddy validate`, so a broken Caddyfile never reaches the server.
- **The deploy job** connects over SSH as the `deploy` user and passes only the commit. In `authorized_keys` that key
  has a forced command (`deploy.sh`) and `restrict`: it can't open a shell, run anything else or forward ports. The
  job's own `GITHUB_TOKEN`, which stops working when the run ends, goes in on stdin so the server can pull the images.
  The login is kept in a temporary folder and removed after the pull.
- **`deploy.sh`** on the server:
  1. Pulls the two images of the commit (only when they aren't there yet).
  2. Takes the compose file of that version out of its web image. Every version brings its own `compose.yml`, so a
     rollback uses the file that belongs to the old version, and the deploy key can't bring a compose file of its own.
  3. Saves the database to `/var/backups/poketopia/pre-deploy/` (the last 10), because a rollback can't undo
     migrations.
  4. Starts the new version with `docker compose up --wait`. Flyway runs the migrations, and Compose waits until the
     backend answers `GET /api/actuator/health` (up to 5 minutes, Flyway included).
  5. Healthy: `releases/current` points at the new version, and versions beyond the newest five are removed with their
     images. Not healthy: it shows the last 80 lines of the backend log, starts the previous version again, and ends
     with an error, so the GitHub run turns red.
  A lock makes a second deploy wait for the first. The site is down for about a minute during a deploy, while the new
  backend starts.
- **Health endpoint:** Spring Boot Actuator with only `/api/actuator/health`, without details. A mail server that is
  down doesn't count, so it can't roll a deploy back.

**From phase 9, because the shop is on the internet now**

- **The first admin without demo data:** `deploy/server/make-admin.sh <email>` (and `--undo`) changes the role of an
  account that registered on the site. psql variables quote the address. It works right away, also for someone who is
  already logged in.
- **Swagger, the API docs and the H2 console are off in `prod`** (`application-prod.yml`).
- **Every admin endpoint refuses customers and visitors:** `AdminEndpointsIntegrationTest` reads all request mappings
  of the app, finds the 61 admin endpoints, and calls each one as a visitor (401) and as a customer (403). It also
  fails when an endpoint under `/v1/admin` lacks `@AdminOnly`. A new admin endpoint is covered without changing the
  test.

**Backups**

- **`backup.sh`** at 05:30 Dutch time (`poketopia-backup.timer`, after the card sync and before the value graph job):
  `pg_dump` in the custom format to `/var/backups/poketopia/db/`, and a copy of the image volume to
  `/var/backups/poketopia/media/<date>/`. Files that didn't change are hard links to the day before, so a day only
  costs what changed. Kept 30 days, by the date in the name. Outside the Docker volumes, on the same server: the owner's
  choice, and these backups are lost with the server.
- **`restore-check.sh`** proves a backup works without touching the shop: it restores the newest backup (or a file you
  name) into a separate `restore_check` database, starts the live backend version on it with every job off, asks the
  API for the sets, and removes everything again.

## The server

| What | Where |
|------|-------|
| Server | VPS at mc-node in the Netherlands, Ubuntu 24.04, 4 CPUs, 11 GB memory. Shared with Minecraft (`/root/minecraft-servers`, port 25565) |
| Address | `https://<ip-with-dashes>.sslip.io`, until the legal check clears a name. A domain only needs a new `SITE_ADDRESS` and `APP_BASE_URL` |
| Scripts | `/opt/poketopia/bin/` (from `deploy/server/`, owned by root) |
| Versions | `/opt/poketopia/releases/<commit>/compose.yml`, `releases/current` is the live one |
| Settings and secrets | `/opt/poketopia/.env` (only the `deploy` user and root can read it), template `deploy/server/env.example` |
| Backups | `/var/backups/poketopia/db`, `media` (daily, 30 days) and `pre-deploy` (last 10) |
| Docker | project `poketopia`: containers `poketopia-{postgres,backend,web,mailpit}-1`, volumes `poketopia_{postgres-data,media,mailpit-data,caddy-data,caddy-config}` |
| Left alone | `mck-wachtlijst-monitor` (the owner's monitor), `gitlab-runner`, Minecraft |

Everyday commands on the server, as root:

```sh
/opt/poketopia/bin/compose.sh ps                     # what runs
/opt/poketopia/bin/compose.sh logs --follow backend  # the backend log
/opt/poketopia/bin/make-admin.sh <email>             # make an account admin
systemctl list-timers poketopia-backup.timer         # when the next backup runs
```

## Where every secret lives

Never in the repository, the docs or a commit.

| Secret | Where |
|--------|-------|
| Database password | `POSTGRES_PASSWORD` in `/opt/poketopia/.env`, generated by `setup.sh` (64 hex characters) and never shown |
| Preview login | The bcrypt hash in `PREVIEW_PASSWORD_HASH` in `/opt/poketopia/.env`. The password itself in `/root/poketopia-preview-login.txt` on the server and `~/poketopia-preview-login.txt` on the owner's Mac |
| Deploy key | Private key: GitHub secret `DEPLOY_SSH_KEY` (from `~/.ssh/poketopia_deploy` on the owner's Mac, which can go once it's in GitHub). Public key: `/home/deploy/.ssh/authorized_keys` on the server, with the forced command |
| Server address | GitHub secret `DEPLOY_HOST`, and `SITE_ADDRESS` in the server's `.env` |
| Server host key | GitHub secret `DEPLOY_KNOWN_HOSTS`, so the job only talks to this server |
| Registry access | None stored: the deploy job hands the server its own `GITHUB_TOKEN`, which expires with the run |
| Stripe | Not set: payments are off in the preview. Phase 10 puts the live keys in the server's `.env` |
| Root SSH | The owner's own key in `/root/.ssh/authorized_keys`. mc-node's web console if it's lost |

The values for the three GitHub secrets are in `~/poketopia-github-secrets.txt` on the owner's Mac. Delete that file
once they are in GitHub (Settings > Secrets and variables > Actions).

## How a deploy works, and how to go back

- **Normally:** push to `main`. The run in GitHub's Actions tab shows the tests, the images and the deploy log. A
  green run is live. A red deploy step means the new version didn't start and the server went back by itself: its
  log is at the end of that step.
- **Back to an earlier version by hand:** on the server, as root:

  ```sh
  ls -t /opt/poketopia/releases/                        # the versions still there, newest first
  sudo -u deploy /opt/poketopia/bin/deploy.sh <commit>  # start that one
  ```

  Or in GitHub: open the run of that commit and choose "Re-run all jobs".
- **Migrations can't be rolled back.** A failed migration is undone by Postgres itself (Flyway runs each one in a
  transaction), so the old version starts fine. But a migration that succeeded stays when you go back: the old version
  then runs on the newer schema. Added tables and nullable columns are fine for that. Removing or renaming a column
  isn't: do that in two releases (first stop using it, then remove it). When it does go wrong, restore the dump from
  just before the deploy (below), from `/var/backups/poketopia/pre-deploy/`.
- **Scripts in `deploy/server/` changed?** They aren't deployed by GitHub (the deploy key may only run them). Copy
  them and run `setup.sh` again: it is safe to repeat and never overwrites the `.env`:

  ```sh
  scp deploy/server/* root@<server>:/root/poketopia-setup/
  ssh root@<server> "/root/poketopia-setup/setup.sh '$(cat ~/.ssh/poketopia_deploy.pub)'"
  ```

## How to restore a backup

Test first, without touching the shop: `/opt/poketopia/bin/restore-check.sh [file]`.

A real restore of the database, as root:

```sh
C=/opt/poketopia/bin/compose.sh
$C stop backend web
$C exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
$C exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' \
  < /var/backups/poketopia/db/<date>.dump
$C up -d --wait
```

The uploaded images, as root (the folder of one day is complete on its own):

```sh
rsync -a --delete /var/backups/poketopia/media/<date>/ "$(docker volume inspect -f '{{ .Mountpoint }}' poketopia_media)/"
```

## Main files

| Area | Files |
|------|-------|
| Server stack | `deploy/compose.yml`, `deploy/Caddyfile`, `deploy/backend.Dockerfile`, `deploy/web.Dockerfile`, `.dockerignore` |
| Server scripts | `deploy/server/setup.sh`, `deploy.sh`, `compose.sh`, `backup.sh`, `restore-check.sh`, `make-admin.sh`, `poketopia-backup.service`, `poketopia-backup.timer`, `env.example` |
| Deploys | `.github/workflows/deploy.yml` |
| Backend | `build.gradle.kts` (Actuator), `application.yml` (`management`: health only), `application-prod.yml` (Swagger and H2 off) |
| Tests | `integration/AdminEndpointsIntegrationTest.kt` (124), `integration/ProdProfileIntegrationTest.kt` (5), `service/provider/CatalogHttpClientTest.kt` (fixed, see Checks) |

## New environment variables

All on the server, in `/opt/poketopia/.env` (template `deploy/server/env.example`). Nothing new for local development.

| Variable | Use |
|----------|-----|
| `SITE_ADDRESS` | The address Caddy serves and gets a certificate for, without `https://` |
| `PREVIEW_USER`, `PREVIEW_PASSWORD_HASH` | The shared preview login. The hash from `caddy hash-password`, in single quotes |
| `MAILPIT_PORT` | Port of Mailpit's page on `127.0.0.1` of the server (8025) |
| `BACKEND_HEAP` | Maximum heap of the backend (`2g`) |
| `HTTP_PORT`, `HTTPS_PORT` | Caddy's ports (80 and 443). Only for another machine where these are taken |

The rest are the variables from the root `.env.example` (database, `APP_BASE_URL`, mail, Stripe, cron times, card
sources), with production values. `IMAGE_TAG` is set by `deploy.sh`, not in the `.env`. GitHub needs the secrets
`DEPLOY_HOST`, `DEPLOY_SSH_KEY` and `DEPLOY_KNOWN_HOSTS`.

## Checks

| Check | Result |
|-------|--------|
| `./gradlew test` | 787 tests, 0 failed, also with the time zone and language of GitHub's runners (UTC, English) |
| `npm run build` | Passes |
| Done when: HTTPS at the temporary address, the login first | From outside: certificate from Let's Encrypt (valid until February 2027, renews by itself). `/` and `/api` give 401 without the login and with a wrong password, 200 with it. `robots.txt` 200 without it. HTTP gives 308 to HTTPS. Swagger and the H2 console 404. The headers above are on every page |
| Done when: from outside only SSH, HTTP and HTTPS | Scan from the owner's Mac: 22, 80 and 443 open. 5432, 8080, 8025, 1025, 2019, 3000, 8081 and 9000 closed. ufw logs and blocks the scanners that come by. 25565 is open in the firewall: a test listener on it was reached from the Mac (Minecraft wasn't running) |
| Done when: Minecraft and the monitor still work | Port 25565 gets through (above). `mck-wachtlijst-monitor` kept running, and a container on its network still reaches the internet with the firewall on. It already showed "unhealthy" before this phase started (its own health check, exit code 1), and still does. `gitlab-runner` is active |
| First deploy on the server | `deploy.sh` as the `deploy` user: 1 minute 8 seconds, of which most was pulling Postgres and Mailpit. Flyway applied V1 to V9 on the empty database, Hibernate validation passed, health UP. The Pokédex import that starts after startup reached PokéAPI through the firewall (690 species in the first minutes) |
| The deploy key | Through the forced command: another command gets only the usage line, port forwarding is "administratively prohibited", and a deploy like GitHub's (commit as the command, a token on stdin) runs and saves the database first |
| Done when: a version that doesn't start is rolled back | Tested with the real `deploy.sh` and `compose.yml` against Docker on the Mac (in a Linux container acting as the server, with the same Docker Compose): a backend that crashes at startup (rolled back in 52 seconds) and one that runs but never answers the health check (rolled back once the wait ran out). Both times the previous version came back and answered, and `current` stayed on it. A second deploy waits for the lock. A bad commit argument is refused |
| Done when: an upload survives a new deploy | Locally: an image uploaded as admin through Caddy was still served after a deploy of a new version. On the server the same volume (`poketopia_media`) is used. With the owner and the friend: still to do (Open issues) |
| Done when: a backup restored on a test database starts the shop | On the server: `poketopia-backup.service` made a dump and an image copy, and `restore-check.sh` restored it into `restore_check`, started the live backend on it (Flyway and validation passed, the API answered) and removed it. The database was still empty: run it again once the sync has filled it. Locally the same with a test account and an image, including the hard links between days and removing a backup older than 30 days |
| Done when: a push to `main` deploys, a failing one doesn't | Not run: Claude doesn't push, and the GitHub secrets aren't set yet (Open issues). The workflow only starts the deploy job when the test job, which builds the images, has passed |
| `make-admin.sh` | An unknown address gives a clear error, an address with spaces and capitals finds the account, an address with SQL in it changes nothing |
| Browser | Through Caddy with the CSP on: home, card and shop pages, drops, collection, Pokédex, account, cart, wishlist and login show no CSP violations, console errors or failed requests. The session cookie is `Secure` |

The first full test run hung for minutes: `CatalogHttpClientTest > an unreachable host counts as a failure` made a
"closed" port with `HttpServer.stop(0)`, which on macOS sometimes left it listening. Every attempt then waited for the
full 60-second timeout. It now takes a port from a plain `ServerSocket`, which closes right away: the class takes a
second.

## Decisions

- **The deploy key may only run `deploy.sh`,** and the compose file comes from the image of the version, not from the
  caller. A leaked GitHub secret can then only redeploy versions that are already in the registry. The `deploy` user
  is in the `docker` group, which is as good as root on that machine, so the forced command is what protects it.
- **The images contain what passed the tests.** CI builds the jar and the frontend once and copies them into the
  images, instead of building them again in Docker. `backend/Dockerfile` (from source) stays for local Docker Compose.
- **Backups with a systemd timer** instead of cron, because the timer understands Dutch time while the server's clock
  is on London time, and catches up after the server was off.
- **A dump before every deploy,** on top of the daily backup, because that is the moment migrations run.
- **Postgres 16** like local development, Caddy 2.11, Mailpit 1.33.
- **No zero-downtime deploys:** about a minute down per deploy is fine for a closed preview. Phase 10 can look at it.
- **The first version was built on the server** from the tested jar and frontend, so the preview could go online the
  same night. From the first push on, GitHub builds them.

## Open issues

- **Docker Desktop on the owner's Mac stopped.** The Mac's disk was full (3 GB free of 228) when the server images
  were built locally, and Docker Desktop powered its VM off at 23:08. Your Postgres, Mailpit and Adminer are down
  until you quit and reopen Docker Desktop and run `docker compose up -d`. The data is in the volume and kept. Claude
  wasn't allowed to restart Docker Desktop. Afterwards the images from this phase can go:
  `docker image rm poketopia-build/backend:amd64 poketopia-build/web:amd64 poketopia-harness docker:27-cli` and
  `docker builder prune`. The Mac needs more free space for Docker builds.
- **GitHub secrets and the first push** (owner): add `DEPLOY_HOST`, `DEPLOY_KNOWN_HOSTS` and `DEPLOY_SSH_KEY`, merge
  and push. Then check the remaining "Done when" items: the run deploys within minutes, and a push with a failing test
  (on a throwaway commit, reverted afterwards) deploys nothing.
- **The owner and the friend as admin** (owner): register, run `make-admin.sh`, upload a picture at
  `/admin/afbeeldingen`, and see it's still there after the next deploy.
- **Restore check on real data:** run `restore-check.sh` again after the first night, when the backup holds the catalog.
- **The 401 page has no security headers.** Caddy drops the `header` settings when `basic_auth` answers 401. Search
  engines don't index 401 pages, and the headers are on every page once logged in, so it's left as is. Phase 10
  removes the login anyway.
- **The monitor container is "unhealthy"** according to its own health check, since before this phase. The owner's
  script, left alone.

## Notes for later phases

- **Phase 10** opens this server: remove the lines marked "preview" in `deploy/Caddyfile` (the login, `X-Robots-Tag`,
  `robots.txt`), set `SITE_ADDRESS` and `APP_BASE_URL` to the domain, add the Stripe live keys and real mail to the
  `.env`, and keep backups somewhere other than this server. Consider zero-downtime deploys and uptime monitoring.
- **Legal check:** the friend has full admin (customer data), Caddy keeps no access logs (no IP addresses stored at the
  proxy), backups of customer data are kept 30 days.
- Use expand-and-contract migrations from now on, so a rollback keeps working (see "Migrations can't be rolled back").
