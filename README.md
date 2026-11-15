# Poketopia

Poketopia (repository `pokemon-webshop`) is a webshop for Pokémon and four other trading card games: Disney Lorcana,
Magic: The Gathering, One Piece Card Game and Riftbound. A few friends build it together. Customers buy sealed
products, singles and accessories, join limited drops, and keep track of their own collection, Pokédex and wishlist,
with what their cards are worth according to Cardmarket and TCGplayer. Admins manage products, stock, orders, drops,
news, info pages and customer service.

The repository has a Kotlin + Spring Boot 4 backend in `backend/` (Java 21), a Vue 3 + Vite 8 frontend in
`frontend/` (Node 20.19 or newer, or 22.12 or newer), and the files for the server in `deploy/`.

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

| Variable | Default | What it does |
|----------|---------|--------------|
| `APP_BASE_URL` | `http://localhost:5173` | The shop's public address: links in mails, Stripe's return page, the sitemap and link previews |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | `pokemonwebshop` | The Postgres container |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | per profile | The backend's database, see [Profiles](#profiles) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS` | Mailpit | Outgoing mail, see [Mail](#mail) |
| `MAIL_FROM` | `noreply@example.com` | Sender of every mail |
| `SHOP_EMAIL` | empty: `MAIL_FROM` | Where new tickets and replies are mailed |
| `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | empty: no online payment | Stripe Checkout, see [Orders and payments](#orders-and-payments) |
| `OPENAI_API_KEY` | empty: AI switched off | Hero slides written by AI and the chatbot |
| `OPENAI_MONTHLY_LIMIT_USD`, `OPENAI_CHAT_MONTHLY_LIMIT_USD` | `5`, `3` | What each may spend per month |
| `OPENAI_TEXT_MODEL`, `OPENAI_IMAGE_MODEL`, `OPENAI_CHAT_MODEL` | see `.env.example` | The OpenAI models |
| `CATALOG_SYNC_CRON`, `RELEASE_SUGGESTION_CRON`, `COLLECTION_SNAPSHOT_CRON` | 03:00, 04:45, 07:00 | The nightly jobs; `-` switches one off |
| `TCGDEX_BASE_URL` and the other `*_BASE_URL` | the public APIs | The card data sources, see [Card data](#card-data) |
| `MEDIA_DIRECTORY` | `data/media` | Uploaded images |
| `AUTH_COOKIE_SECURE` | off locally, on in `prod` | `Secure` flag of the session cookie |
| `FORWARD_HEADERS_STRATEGY` | `none` | `native` behind a proxy, so rate limits see the visitor's address |
| `SEO_INDEX_FILE` | empty; `/app/index.html` in `prod` | The built `index.html` the backend fills in for link previews |
| `SHOP_ACCESS` | `preview` | `preview` or `open`: the one switch that opens the shop, see [Preview server and deploys](#preview-server-and-deploys). The backend only takes a Stripe test key with `preview` and a live key with `open` |
| `COMPANY_LEGAL_NAME`, `COMPANY_ADDRESS`, `COMPANY_KVK_NUMBER`, `COMPANY_VAT_NUMBER` | empty | The business details in the footer; empty shows nothing |
| `CHECKOUT_MAX_OPEN_PER_CUSTOMER`, `CHECKOUT_MAX_OPEN_PER_IP` | `2`, `5` | Checkouts waiting for payment at once, see [Orders and payments](#orders-and-payments) |

The server has a few more in `deploy/server/env.example`: `SITE_ADDRESS`, `SITE_ALIASES`, the preview login,
`BACKEND_HEAP` and the ports, and the root-only `deploy/server/ops.env.example` for backups elsewhere and monitoring.

### Mail

Locally and on the preview server, Mailpit catches every mail and needs no login. For a real mail provider set:

| Variable | Value |
|----------|-------|
| `MAIL_HOST`, `MAIL_PORT` | The provider's SMTP server, usually port 587 |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | The SMTP login (empty for Mailpit) |
| `MAIL_SMTP_AUTH` | `true` to log in (default `false`) |
| `MAIL_SMTP_STARTTLS` | `true` to encrypt; a server that doesn't offer it is then refused (default `false`) |
| `MAIL_FROM` | The sender address of every mail |

Connecting, reading and writing each time out after 10 seconds, so a slow mail server can't hold a thread.

## Card data

The card catalog comes from five public sources that need no key. Their base URLs are in `.env.example`.

| Game | Source | Prices |
|------|--------|--------|
| Pokémon | [TCGdex](https://tcgdex.dev) | Cardmarket, EUR |
| Magic: The Gathering | [Scryfall](https://scryfall.com/docs/api) | Cardmarket, EUR |
| Disney Lorcana | [Lorcast](https://lorcast.com) | TCGplayer, USD |
| One Piece Card Game | [OPTCG API](https://optcgapi.com) | TCGplayer, USD |
| Riftbound | TCGplayer, through [tcgcsv.com](https://tcgcsv.com) | TCGplayer, USD |

The Pokédex comes from [PokéAPI](https://pokeapi.co). The backend imports new species right after startup and at every Pokémon sync. The first import takes about 14 minutes in the background, and later runs make one request. The `dev` profile only does this during a Pokémon sync.

An admin can replace the picture of a card or set at `/admin/afbeeldingen`. Uploads go to `MEDIA_DIRECTORY` (`data/media` by default), and a sync leaves them alone.

Cards and sealed products without a picture can get one from TCGplayer at `/admin/afbeeldingen/zoeken`: one search per game. Sure matches get their picture right away; doubtful ones show up to four candidates, and the admin picks one or none. A found picture comes after the source's own and after an upload, and a sync leaves it alone. Products in another language are skipped.

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

## Collection value graph

Card prices have no history, so the graph on `/collectie` is drawn from one saved value per customer per day. A job saves these at the time in `COLLECTION_SNAPSHOT_CRON` (07:00 Dutch time by default, `-` switches it off). Keep it after the card sync, so it uses the prices of that night. Saved days can't be filled in afterwards.

## Shop

Products and stock are managed at `/admin/producten`. Every product can be in the shop without stock: it then shows "Uitverkocht", and customers can ask for a mail when it is back. A product without a price is in the shop but can't be bought.

Ways to fill the shop:

- **Product list import** at `/admin/producten/import`: every sealed product of the sets in the card catalog, from [Cardmarket's public product list](https://downloads.s3.cardmarket.com/productCatalog/productList/products_nonsingles_6.json) (`CARDMARKET_PRODUCTS_BASE_URL`). It only runs when an admin starts it, takes about a second per game, and never changes stock, prices or products the admin edited. Run a card sync first: products of sets that aren't in the catalog stay out.
- **Products of one set**: the usual products of a set in one go, such as booster, booster box and Elite Trainer Box.
- **Losse kaart toevoegen**: a single from the card catalog, with its market price in euros as a suggestion.
- **CSV**: download all products with stock, change them in Excel and read the file back in. Excel's Dutch semicolons and decimal commas work.

Stock only changes through `StockService`, which writes every change to the stock log. The hero on the home page is a carousel: the first slide is the hero with the binder, the others are managed at `/admin/hero`.

Customers never see how many of a product are left: not on a page, not in an API reply and not in an error message. Only "Laatste stuks", "Bijna uitverkocht" and "Uitverkocht".

More for the shop, all in admin:

- **Discount codes** at `/admin/kortingscodes`: a percentage or an amount, with optional dates, a minimum order, a maximum number of uses, once per customer, and limits to games or product types. They never apply to shipping, and to drops only when switched on.
- **Mystery boxes**: a product of type mystery box gets batches of boxes the owner packed, with what each kind contains and is worth. The product page shows the chance per kind, from the boxes that are left. At payment the shop draws a random box from the oldest batch, and the packing list says which one to send.
- **Reviews** from customers who bought the product, shown after an admin approves them at `/admin/reviews`.
- **Release calendar** at `/admin/releasekalender`, shown on `/releasekalender`, with pre-orders on `/pre-order`. The admin page suggests upcoming sets that the card catalog and TCGplayer already know, refreshed at `RELEASE_SUGGESTION_CRON` (04:45 by default).
- **News** at `/admin/nieuws`, the **newsletter** with double opt-in and an export at `/admin/nieuwsbrief`, and **info pages** (shipping, returns, terms, privacy, FAQ, contact, about) at `/admin/paginas`. The legal pages start as drafts.
- **Hero slides written by AI**: with `OPENAI_API_KEY` set, `/admin/hero` writes a slide from a prompt, optionally with a picture. It stays switched off until an admin approves it, and stops at `OPENAI_MONTHLY_LIMIT_USD` per month.

## Customer service

The contact page (`/info/contact`) leads through the FAQ and a chatbot to a ticket. The chatbot also opens from the chat button on every shop page. It answers only from the published info pages and shipping costs, never mentions stock or prices, and points to a ticket for questions about an order. It runs on `OPENAI_CHAT_MODEL` (`gpt-6-luna`) and stops at `OPENAI_CHAT_MONTHLY_LIMIT_USD` per month. Without `OPENAI_API_KEY` the page goes straight to a ticket.

A ticket gets a number and a secret link for the customer. New tickets and replies are mailed to `SHOP_EMAIL` (empty: `MAIL_FROM`); the customer gets a confirmation and every answer. Answer them at `/admin/tickets`. Customers with an account see their tickets under "Mijn tickets".

## Orders and payments

Customers pay with [Stripe Checkout](https://docs.stripe.com/payments/checkout): iDEAL | Wero and card in the Netherlands, Bancontact and card in Belgium. Checkout holds the stock for 15 minutes while the customer pays. An order only becomes paid through Stripe's webhook, never through the page the customer returns to. Orders, track & trace, cancelling and refunds are at `/admin/bestellingen`, shipping costs at `/admin/verzending`.

Without `STRIPE_SECRET_KEY` the shop works, but checkout says online payment isn't set up. To test a payment locally:

1. Put a test key from the [Stripe dashboard](https://dashboard.stripe.com/test/apikeys) in `.env` as `STRIPE_SECRET_KEY=sk_test_...`.
2. Install the [Stripe CLI](https://docs.stripe.com/stripe-cli), log in and forward the webhooks:

   ```sh
   brew install stripe/stripe-cli/stripe
   stripe login
   stripe listen --forward-to localhost:8080/api/v1/payments/stripe/webhook
   ```

3. Put the `whsec_...` that `stripe listen` prints in `.env` as `STRIPE_WEBHOOK_SECRET` and restart the backend.
4. Order something and pay with card `4242 4242 4242 4242`, any future date and any CVC, or choose iDEAL and authorize the test payment. The order turns paid within seconds, and the confirmation mail is in Mailpit.

If checkout says "We konden de betaalpagina niet openen", switch off Managed Payments in the Stripe dashboard (Settings > Managed payments). Newer accounts have it on by default, and it refuses the payment methods the shop asks for. More tips, such as installing the CLI without Homebrew, are under "Testing a payment" in [docs/phases/phase-05.md](docs/phases/phase-05.md).

A cart line holds at most 10 of a product (`CartRules.MAX_PER_LINE`), and the cart never looks at the stock, so it
can't tell how many are left. Checkout tells the customer when there isn't enough, without a number. A customer can
have at most 2 checkouts waiting for payment (`CHECKOUT_MAX_OPEN_PER_CUSTOMER`): counted by account when logged in, so
guest checkouts on their address can't block them, and by email address for a guest. An IP address can have at most 5
(`CHECKOUT_MAX_OPEN_PER_IP`), more because a phone network, school or office puts many customers behind one address;
that refusal has its own message. Checkout takes 5 tries per 10 minutes per IP address
(`app.rate-limit.buckets.checkout`). The owner chose 2 and the rate limit in phase 9; 5 per IP address is a default
from phase 10. All are defaults the owner can change.

In production, add a webhook endpoint in the Stripe dashboard for `checkout.session.completed`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed` and `checkout.session.expired`, pointing at `https://<your domain>/api/v1/payments/stripe/webhook`, and use its signing secret. Managed Payments must be off in the live account too. The backend only uses a live key (`sk_live_...`) once `SHOP_ACCESS=open`, and only a test key before that: the other kind keeps online payment off, and the admin dashboard says why. The steps are in [docs/phases/phase-10.md](docs/phases/phase-10.md).

## Drops

A drop is a limited release at a set time, managed at `/admin/drops`. The stock you allocate to a drop leaves the normal stock right away (a `DROP_ALLOCATION` line in the stock log). When the drop ends, whatever wasn't sold goes back, or stays with the drop until you give it back, as chosen per drop.

How it runs:

- The waiting room opens 30 minutes before the start. At the start time everyone in it gets a random place, so refreshing at exactly the right second gives no advantage. Later arrivals join at the back.
- The shop lets people through in batches every 10 seconds, at the speed set per drop (60 a minute by default). Whoever gets through has 10 minutes to fill the drop cart and start paying. The checkout then holds the stock for 10 minutes.
- Limits per product and per drop count paid orders and running checkouts by account, email address (Gmail dots and `+tags` don't help) and delivery address. They're checked when something goes in the drop cart and again, under a lock on the drop, when the payment starts.
- An account is required by default. Drop endpoints are rate limited per IP address and per account, and the join and checkout forms have a hidden honeypot field.
- "Herinner mij" sends one mail 15 minutes before the start.
- Upcoming and live drops get their own slide in the home page hero, with a countdown, unless you switch that off per drop. The menu shows LIVE while a drop runs.
- The slide of a drop is black by default, or red, yellow or green as chosen per drop. It shows the drop's banner, or without one the pictures of up to three of its products. Product photos with a transparent background (PNG or WebP) look best on it.

During the drop, `/admin/drops/{id}/live` shows the waiting room and sales, and has buttons to pause the line, change its speed or end the drop. After the drop the same page is the report. The timings are under `app.drops` in `application.yml`.

## Search engines and link previews

Every page sets its own title, description and canonical link; private pages, search and drafts get `noindex`. For
search engines and link previews, which don't run JavaScript, the backend serves `/api/v1/seo/sitemap.xml`,
`/api/v1/seo/robots.txt`, and the product, drop and news pages as `index.html` with their title, text and picture
filled in. Caddy sends `/sitemap.xml`, `/robots.txt`, `/product/…`, `/drops/…` and `/nieuws/…` there. A drop's preview
never shows a price or stock. When the backend doesn't answer, those pages get Caddy's own `index.html` with 503, so the
app still loads. While `SHOP_ACCESS=preview`, Caddy's own robots.txt keeps everyone out; `open` uses the backend's.

## Limits

- Request bodies: 256 kB for JSON (`app.http.max-body-size`), 6 MB for an upload, in the backend and in Caddy.
- Logins: 10 a minute per IP address, and 10 failed ones per 15 minutes per email address. Reset mails: 3 an hour per
  email address. Known and unknown addresses get the same answers.
- API answers carry `Cache-Control: no-store`, except uploaded images.

All limits are under `app` in `application.yml`.

## Preview server and deploys

The shop runs as a closed preview on the owner's server: every page asks for a shared login, search engines are kept
out, and online payment is off. Every push to `main` runs the backend tests and the frontend build in GitHub Actions.
When both pass, the images go to GitHub's container registry and the server starts them, going back to the previous
version by itself when the new one doesn't get healthy or a product page doesn't come back as the app.

Opening the shop is one switch, `SHOP_ACCESS` in the server's `.env`: `preview` (the default) keeps the login, the
`X-Robots-Tag: noindex` header and the "keep out" robots.txt; `open` drops all three. Any other value stops Caddy
instead of opening the shop. Change it only with `/opt/poketopia/bin/shop-access.sh open`, which refuses while the
domain, a live Stripe key, real mail or the KVK number are missing and asks to confirm the legal check. Everything the
owner still has to bring for that, in order, is in [docs/phases/phase-10.md](docs/phases/phase-10.md).

- `deploy/compose.yml` and `deploy/Caddyfile`: Postgres, the backend on the `prod` profile, Caddy with HTTPS, Mailpit.
- `deploy/server/`: the scripts on the server (deploy, backup with an optional copy elsewhere, restore check, make an
  account admin, open or close the shop, disk check) and the templates of the server's `.env` and `ops.env`.
- `.github/workflows/deploy.yml`: needs the secrets `DEPLOY_HOST`, `DEPLOY_SSH_KEY` and `DEPLOY_KNOWN_HOSTS`.

How to deploy, roll back, restore a backup and where every secret lives: [docs/phases/phase-07.md](docs/phases/phase-07.md),
and for going live [docs/phases/phase-10.md](docs/phases/phase-10.md).

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

## Phases

The shop was built in phases, each with notes on what was built, checked and decided:

| Phase | Notes |
|-------|-------|
| 1. Foundation | [phase-01.md](docs/phases/phase-01.md) |
| 2. Card catalog | [phase-02.md](docs/phases/phase-02.md) |
| 3. Collection and Pokédex | [phase-03.md](docs/phases/phase-03.md) |
| 4. Shop products and stock | [phase-04.md](docs/phases/phase-04.md) |
| 5. Cart, checkout and orders | [phase-05.md](docs/phases/phase-05.md) |
| 6. Drops | [phase-06.md](docs/phases/phase-06.md) |
| 7. Online preview and automatic deploys | [phase-07.md](docs/phases/phase-07.md) |
| 8. Extras | [phase-08.md](docs/phases/phase-08.md) |
| 9. Polish and hardening | [phase-09-audit.md](docs/phases/phase-09-audit.md): the audit, and what phase 9b fixed |
| 10. Go live | [phase-10.md](docs/phases/phase-10.md): prepared, waiting for the domain, the KVK number and the legal check ([HANDOVER.md](docs/HANDOVER.md)) |

The plan for every phase is in [docs/build-prompt.md](docs/build-prompt.md).

