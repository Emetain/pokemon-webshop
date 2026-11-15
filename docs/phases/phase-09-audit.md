# Phase 9a: Audit

Read-only audit of the whole repository on 2026-11-15, on branch `feature/phase-9-polish` (commit `6b2e490`, the
phase 8 handover). No code was changed. Phase 9b works through the checklist at the bottom and ticks items off here.

## How it was checked

- Read the security layer, every controller's annotations, checkout, stock, payment, drop stock, auth, media and
  mail code, the deploy files and the CI workflow.
- `./gradlew test` and `npm run build` (results under "What is in good shape").
- Started the `prod` profile on an empty Postgres database, and a second backend on a copy of the local database
  (142,588 cards, 3,124 products) with a customer and an admin test account. Both were removed afterwards.
- Opened 49 pages (24 as a visitor, 5 as a customer, 20 as an admin) at 360, 768 and 1280 px in headless Chromium.
  Measured horizontal overflow, console errors, failed requests, titles and headings, and ran axe-core
  (WCAG 2 A and AA) at 360 and 1280 px.
- Called the API directly for wrong input, oversized bodies, rate limits, other customers' data and admin paths.
- Searched the tree and the whole git history for keys and passwords.

Not checked, so still open for 9b or 9c:

- A keyboard walk-through by hand of the menus, dialogs and the checkout form. Only the automatic checks ran (axe,
  the skip link, the `:focus-visible` style in `main.css:244`).
- The live drop flow and a real Stripe payment. Both were tested in phases 5 and 6 and their code was only read here.
- The preview server itself. Only the files in `deploy/` were read.
- Single CVEs for the dependencies. Only the versions and `npm audit` were looked at.

## What is in good shape

Listed so 9b doesn't redo it and 9c knows what was already verified.

- **Tests and build:** 952 backend tests, 0 failures. `npm run build` passes; the entry bundle is 222 kB (80 kB
  gzipped) and every view is its own chunk.
- **`prod` profile:** starts on an empty database in 10 seconds. Flyway applies all 13 migrations (V1 to V12 plus the repeatable
  vendor one), Hibernate validation passes. Swagger, the API docs, the H2 console and `/actuator/env` answer 404.
- **Admin endpoints:** every controller under `/v1/admin` has `@AdminOnly`. A customer gets 403, a visitor 401.
  `AdminEndpointsIntegrationTest` finds the endpoints from the request mappings and tests each one.
- **Other customers' data:** changing or deleting another account's address gives 404; so does another account's
  order, an order without its key and an order with a wrong key. Addresses, collection entries, the binder, reviews
  and tickets are all looked up by the logged-in user's id.
- **Uploads:** all eight upload endpoints are admin only, limited to 5 MB, and accept only JPG, PNG and WebP by the
  first bytes of the file. Files get a generated name, and the media endpoint only serves names it made itself.
- **Stripe webhook:** signature checked on the raw body with a five-minute tolerance, the session is fetched from
  Stripe again before an order is marked paid, and a second delivery changes nothing.
- **Stock and drops:** the version on the stock row, the lock on the order and the lock on the drop are in place, with
  concurrency tests for both.
- **Secrets:** none in the tree or in any commit. Only `.env.example` files were ever committed.
- **Sessions:** random 32-byte tokens stored as a hash, `HttpOnly`, `Secure` in `prod`, `SameSite=Lax`. A password
  reset logs out every session. Login takes equally long for a known and an unknown address.
- **Proxy headers:** HSTS, `nosniff`, `X-Frame-Options`, a Referrer-Policy, a Permissions-Policy and a strict
  Content-Security-Policy are set in `deploy/Caddyfile`.
- **Layout:** no page overflows sideways at 360, 768 or 1280 px. Every page has exactly one `h1`. All 23 images have
  an `alt`. No console errors apart from the expected 401 of `/auth/me` for visitors.
- **States:** every data view has loading and error handling.
- **Speed on the full catalog:** product lists 30 to 130 ms, card search 120 to 135 ms, set pages 25 ms, the Pokédex
  15 ms. Lists that grow are paged and the page size is capped. The big tables have the indexes their filters use.

## Critical

Must be fixed before going live.

- [x] **C1. Spring Boot 3.3.2 is more than two years old.**
  - Where: `backend/build.gradle.kts:5` (`org.springframework.boot` 3.3.2, July 2024), with it Spring Framework,
    Tomcat, Hibernate and Jackson of that time. Also `springdoc` 2.6.0 (line 35) and Kotlin 1.9.25 (lines 2 to 4).
  - Why: the shop is on the internet and takes payments. Two years of security fixes in the web server and framework
    are missing, and the 3.3 line no longer gets free patches.
  - Fix: move to the newest patch of the newest supported Spring Boot 3.x line, with the matching
    dependency-management plugin and springdoc. Keep `stripe-java` 29.2.0, because it pins the Stripe API version
    the code was written for. Run all tests and the `prod` start on an empty database afterwards. If a move to the
    newest line breaks too much, at least take the last 3.3.x patch and write down what blocked the rest.
  - Done (9b): Spring Boot **4.1.2**, not a 3.x line. Looked up on 2026-11-15 (Maven Central and the Spring Boot
    support data): no 3.x line has free support any more; 3.5.x ended on 2026-06-30 (last free patch 3.5.16,
    2026-06-25) and 3.3.x on 2025-06-30 (3.3.13). The supported lines are 4.0.x (until 2026-12-31) and 4.1.x (until
    2027-07-31), so only 4.1 solves the "why". With it: Spring Framework 7.0.10, Tomcat 11.0.27, Hibernate 7.4.13,
    Jackson 3.1.7, Flyway 12.4, Kotlin 2.3.21 (the version Boot 4.1.2 is built with), dependency-management 1.1.7,
    springdoc 3.2.0 (built against Boot 4.1.2), Gradle wrapper 8.14.7 (Boot 4 needs 8.14 or newer). `stripe-java`
    stays 29.2.0. What changed in the code:
    - Starters: `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `spring-boot-h2console` (dev only),
      `spring-boot-starter-webmvc-test`; `jackson-module-kotlin` from `tools.jackson`.
    - Jackson 3: imports moved to `tools.jackson`, `asText()`/`textValue()` became `asString()`/`stringValue()`.
      Jackson 3's `JsonNode` has its own `map()` that applies to the node itself and silently shadows Kotlin's list
      `map`, so every `node.map { }` over children is now `node.values().map { }` (found by the compiler where the
      types differed and by searching the rest). `CatalogHttpClient` also retries on Jackson's own (now unchecked)
      read exceptions, which used to be `IOException`s.
    - Stricter nullability from Spring 7: `findAll(Specification, Pageable)` overrides, and
      `PasswordEncoder.encode` is now nullable (`requireNotNull`).
    - Tests: `@MockBean` → `@MockitoBean`, `AutoConfigureMockMvc` moved package, `TestRestTemplate` replaced by the
      JDK `HttpClient` in `ProdProfileIntegrationTest`.
    - `management.endpoint.health.probes.enabled: false`: Boot 4 adds liveness/readiness groups to the health answer
      by default; it stays `{"status":"UP"}`.
    Checked: 1022 tests, 0 failures before the upgrade on 3.3.2 and again on 4.1.2. The `prod` profile on an empty
    Postgres 16 database: Flyway applies all 13 migrations, Hibernate validation passes, 0 ERROR lines, health 200
    in 6.6 s. Against that backend: body limits (also a chunked body, through Jackson 3), the Dutch type-mismatch
    answer, Swagger and API docs 404, sitemap, robots and link-preview pages; through Caddy (see S4) the same plus
    `no-store` and the media cache header. On the `dev` profile: Swagger, API docs and the H2 console answer, and the
    browser checks of S3, S7, S10 and S14 pass against it. Remaining compiler warnings are "unnecessary `!!`" and
    "condition is always true" where Spring 7 now declares values non-null; no behaviour changes.

- [x] **C2. A request body has no size limit.**
  - Where: no limit in `deploy/Caddyfile` (the `handle /api/*` block), and none in the backend for JSON. Only
    multipart uploads are limited (`application.yml:18`).
  - Why: tested: a 20 MB JSON body to `/v1/auth/login` was read completely before it was refused. Anyone can send
    many large bodies without logging in. The backend has 2 GB of heap on a server it shares with Minecraft, and
    `-XX:+ExitOnOutOfMemoryError` restarts it when memory runs out, so this takes the shop down.
  - Fix: in Caddy, `request_body { max_size ... }`: about 7 MB for the upload paths and the CSV import, 256 kB for
    the rest of `/api`. Add the same guard in the backend for requests that don't come through Caddy, as a filter
    that refuses a JSON body above the limit with 413 and the usual error format. Add a test.
  - Done (9b): `security/RequestSizeFilter.kt` refuses a non-multipart body above `app.http.max-body-size` (256 kB)
    with 413 `request_too_large`: by Content-Length before reading, or while reading a chunked body (the handler
    maps it in `GlobalExceptionHandler.handleHttpMessageNotReadable`). Multipart keeps `spring.servlet.multipart`.
    Caddy: `request_body` 7 MB for multipart under `/api/v1/admin/*`, 256 kB for the rest of `/api`. Tests in
    `ErrorHandlingIntegrationTest` and `RequestSizeFilterTest`. The Caddyfile passes `caddy validate` in the
    `caddy:2.11-alpine` image already on the Mac (no build or pull); CI validates it again when the image is built.

- [x] **C3. Mail has no username, password or TLS settings.**
  - Where: `backend/src/main/resources/application.yml:11` only has `MAIL_HOST` and `MAIL_PORT`. `deploy/compose.yml`,
    `docker-compose.yml`, `.env.example` and `deploy/server/env.example` have no variables for them either.
  - Why: Mailpit needs no login, a real mail provider does. Without this, phase 10 can't send order confirmations,
    password resets or ticket answers.
  - Fix: add `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH` and `MAIL_SMTP_STARTTLS` (default empty and `false`,
    so Mailpit keeps working), map them to `spring.mail.*`, pass them through both compose files, and describe them
    in both env examples and the README. Add connect and read timeouts, so a slow mail server can't hold a thread.
  - Done (9b): `spring.mail.username`, `password`, `properties.mail.smtp.auth` and `starttls.enable` from the four
    variables (empty and `false` by default; Spring's mail sender treats an empty username as "no login", so Mailpit
    keeps working). `MAIL_SMTP_STARTTLS` also sets `starttls.required`, so a server without encryption is refused
    instead of getting the password in plain text. Connect, read and write timeouts of 10 seconds. Passed through
    `docker-compose.yml` and `deploy/compose.yml`, described in `.env.example`, `deploy/server/env.example` and a
    "Mail" section in the README. `ProdProfileIntegrationTest` checks that the values reach the mail sender.

- [x] **C4. The cart gives away how many of a product are in stock.**
  - Where: `backend/.../service/CartService.kt:48` and `:71` refuse a quantity above `stock.available`.
  - Why: the owner's rule is that customers never learn the stock: not on a page, not in an API reply and not in a
    message. Tested on a product with 10 in stock: adding 99, 50 and 20 was refused, adding 10 worked, adding one
    more was refused. Five requests gave the exact number. It works for every product with fewer than 99.
  - Fix: let the cart accept any quantity up to a fixed maximum per line without looking at the stock, as long as
    the product can be bought. Checkout already refuses when there isn't enough, with a message without a number
    (`CheckoutService.kt:82`); make that message tell the customer to lower the quantity. Lower the maximum per
    line from 99 to 10, so a try at checkout says little. Together with S2 this turns five free requests into a few
    slow, limited checkouts. Check the drop cart (`DropCartService`) for the same pattern. Update the cart tests.
  - Owner decision: the maximum per line (10 is a suggestion), and whether a customer may only find out at checkout
    that a quantity isn't available. That is the price of not showing stock in the cart.
  - Done (9b), with the owner's confirmed choices (10 per line, finding out at checkout): `CartService.add` and
    `setQuantity` only check that the product can be bought and `CartRules.MAX_PER_LINE` (now 10, one constant, also
    used by the request DTOs and `data/shop.js` in the frontend). The cart line's `TOO_MANY` problem now means "above
    10" instead of "above the stock", which was a second way to read the stock. Checkout refuses without a number:
    "Van X hebben we niet genoeg voor dit aantal. Verlaag het aantal in je winkelwagen en probeer het opnieuw." The
    drop cart had the same pattern (`DropCartService.setQuantity` refused above `available`, and its cart showed
    `TOO_MANY`): it now only refuses a sold-out or fully reserved product (both shown on the drop page anyway), and
    the drop checkout checks the rest under the lock. Admin drop limits keep 1 to 99 (`DropRules.MAX_LIMIT`). Tests
    updated in `CartRulesTest`, `CheckoutIntegrationTest`, `DropLimitsIntegrationTest` and `ProductIntegrationTest`.

## Should fix

- [x] **S1. Logging in can be guessed per account, and reset mails can be sent in bulk.**
  - Where: `AuthController.kt:36` and `:50`. The limit is 10 a minute per IP address (`application.yml`,
    `app.rate-limit`). Tested: attempts 9 to 12 within a minute got 429.
  - Why: nothing counts per account. An attacker with many addresses can keep guessing one customer's password, and
    one address can send a customer 10 reset mails a minute.
  - Fix: also count per email address with `RateLimitService`, for example 10 failed logins per 15 minutes and 3
    reset mails per hour. Keep the answer the same for known and unknown addresses.
  - Done (9b): `RateLimitService` counts per email address too (`checkEmail`, `requireEmailBelowLimit`,
    `recordEmail`, trimmed and lowercased). `AuthOrchestrator.login` refuses once an address has 10 failed logins in
    15 minutes (`login-email` bucket; a correct password is refused too while it holds, or guessing could go on), and
    `forgotPassword` allows 3 per address per hour (`forgot-password-email`). Known and unknown addresses get the
    same answers. The 429 message now says how many minutes to wait instead of always "een minuut". Tests in
    `AuthFlowIntegrationTest` and `RateLimitServiceTest`. Side effect to know: someone can lock a customer out of
    logging in for 15 minutes by failing 10 times on their address; that is the usual price of a per-account limit.

- [x] **S2. One visitor can hold the whole stock.**
  - Where: `CheckoutController.kt:31` (`checkout` bucket, default 10 a minute per IP), `CheckoutService.createOrder`.
  - Why: every checkout reserves its stock for 15 minutes, also for a guest without paying. Only an earlier checkout
    of the same cart is closed. With a new guest cart each time, one address can reserve up to 99 of every product,
    ten times a minute, and keep the shop "Uitverkocht".
  - Fix: give `checkout` its own tight limit (for example 5 per 10 minutes per IP), and refuse a new checkout when
    the same email address or IP already has 2 open ones. The lower maximum per line from C4 helps too.
  - Done (9b), with the owner's confirmed numbers: the `checkout` bucket is 5 per 10 minutes per IP
    (`app.rate-limit.buckets.checkout`), and `service/OpenCheckoutService.kt` refuses a third shop checkout waiting
    for payment per email address or per IP (`app.checkout.max-open-checkouts: 2`) with 429
    `too_many_open_checkouts`. Email counts from the orders by `email_key` (Gmail dots and `+tags` are one person;
    every order now gets the key, not only drop orders). The IP isn't stored with an order, so the orders per IP are
    kept in memory like the rate limits. Retrying the same cart closes the earlier attempt first and doesn't count.
    Drops keep their own limits. Tests in `CheckoutIntegrationTest`. Two checkouts at the same moment can both pass
    the count; the rate limit keeps that small.

- [x] **S3. Every page has the title "Poketopia" and no description.**
  - Where: `frontend/index.html:6`. Nothing in `frontend/src` sets `document.title` or a meta tag. Measured: 147 of
    147 page loads had the same title and no description.
  - Why: search results and browser tabs all show the same word, and screen reader users don't hear which page
    opened.
  - Fix: a small composable that sets the title, description and canonical link. Static pages get theirs from the
    route's `meta`; product, set, card, drop, news and info pages set theirs once their data is loaded. Format:
    `<page> | Poketopia`.
  - Done (9b): `utils/pageMeta.js` sets the title (`<page> | Poketopia`), the description (cut to 160 characters,
    marks stripped), a `robots` tag and the canonical link (without filters, with `?page=` from page 2). The router
    sets them from `meta.title`/`meta.noindex` on every change of path (`router.afterEach`); a query-only change
    only moves the canonical link. Product, shop list, set list, set, card, Pokédex entry, drop, news and info pages
    call `usePageMeta` once their data has loaded. A product draft, an unpublished article and a draft info page
    (the legal pages) get `noindex`. `index.html` has a default title and description for crawlers without
    JavaScript.

- [x] **S4. No Open Graph tags for products and drops.**
  - Where: nowhere in the repository.
  - Why: a shared link in WhatsApp, Discord or Facebook shows no picture or name. Those services don't run
    JavaScript, so tags set by Vue (S3) don't reach them.
  - Fix: a backend endpoint that returns the built `index.html` with title, description, `og:title`,
    `og:description`, `og:image`, `og:url` and `og:type` filled in for `/product/:slug`, `/drops/:slug` and
    `/nieuws/:slug`, and Caddy sending those three paths to it. Escape every value. A drop has no price and no stock
    in these tags.
  - Done (9b): `GET /api/v1/seo/pages/{product|drops|nieuws}/{slug}` returns the built `index.html` with the
    block between `<!-- page-meta -->` markers replaced by the title, description, canonical link, `og:*` tags and
    `twitter:card`, every value HTML-escaped. An unknown or unpublished slug gets the plain `index.html` with 404,
    so the app shows its 404 page and search engines get the right status. A drop's tags have its name, text and
    banner (or a product photo): no price, no stock. The backend image carries the index file
    (`COPY frontend/dist/index.html` in `deploy/backend.Dockerfile`, `app.seo.index-file`, `/app/index.html` in
    `prod`); CI builds the frontend before that image. Caddy sends `^/(product|drops|nieuws)/[^/]+$` there. Tests
    in `SeoIntegrationTest` (escaping, 404s, no drop price).

- [x] **S5. No `sitemap.xml`, no real `robots.txt`, and nothing keeps private pages out of search engines.**
  - Where: `frontend/public/` is empty. `deploy/Caddyfile` only has the preview's "keep everyone out" robots file
    and the preview's `X-Robots-Tag`. Phase 10 removes both, and then nothing is left.
  - Why: the build prompt asks for a sitemap, a robots file, and shared collection pages that are `noindex` and
    stay out of the sitemap.
  - Fix: a backend endpoint for `sitemap.xml` (home, game and type pages, active products, published drops, news,
    published info pages, card sets) and Caddy sending `/sitemap.xml` to it. A `robots.txt` for the live shop that
    points to it and disallows `/admin`, `/account`, `/afrekenen`, `/winkelwagen`, `/bestelling`,
    `/klantenservice/ticket` and `/collectie/gedeeld`. A `noindex` meta tag from the S3 composable on those pages,
    on the search page and on the 404 page. Leave the lines marked "preview" in the Caddyfile alone: phase 10
    removes them.
  - Done (9b): `GET /api/v1/seo/sitemap.xml` (`SeoController` → `SeoOrchestrator` → `SeoService`) lists home,
    the fixed shop pages, every game's shop and card pages, the game/type pages that have active products, active
    products, published drops, published news, info pages that aren't drafts (so not the unchecked legal pages) and
    every card set, with `lastmod` where known. `GET /api/v1/seo/robots.txt` disallows the private paths (the
    audit's list plus `/drops/*/afrekenen`, `/verlanglijst` and `/nieuwsbrief`), and `/api/` except
    `/api/v1/media/`, and names the sitemap with `APP_BASE_URL`. I served robots.txt from the backend instead of a
    static file because it needs the shop's absolute address, which isn't known at build time. Caddy sends
    `/sitemap.xml` and `/robots.txt` there, inside the preview login; the preview's own robots handle comes first
    while it exists, so nothing changes until phase 10 removes the "preview" lines. `noindex` comes from the route
    meta of S3 (account, cart, checkout, orders, tickets, shared collections, wishlist, search, login pages, admin)
    and from the 404 page. Tests in `SeoIntegrationTest` and `SeoServiceTest`.

- [x] **S6. An unknown address goes to the home page.**
  - Where: `frontend/src/router.js:309` (`redirect: '/'`).
  - Why: a mistyped or old link silently shows the home page. Search engines see a second copy of the home page
    instead of a missing page.
  - Fix: a `NotFoundView` with a message, a search field and a link home, `noindex`, at the catch-all route. Use it
    for an unknown product, drop, set and article too, where the views now show their own error.
  - Done (9b): `views/NotFoundView.vue` (message, search field to `/zoeken`, link home, `noindex`) is the
    catch-all route inside the shop layout, and replaces the own "not found" notices of the product, shop list, set
    list, set, card, Pokédex entry and drop views. The news article and info page views showed the API's error text
    for a 404 and now show it too. The real 404 status for unknown product, drop and news paths comes from the
    backend with S4; other paths get 200 with the 404 page and `noindex` (the SPA can't set a status).

- [x] **S7. The admin menu fills the whole phone screen.**
  - Where: `frontend/src/components/layout/AdminLayout.vue:37`. The sidebar only moves to the side from 900 px
    (line 179).
  - Why: at 360 px the 19 menu links come first on every admin page. The friend who adds pictures has to scroll past
    all of them on a phone, every time.
  - Fix: below 900 px, a top bar with a menu button that opens and closes the list, closed after a click on a link,
    with `aria-expanded` and working with the keyboard.
  - Done (9b, commit `24aaf5b`): below 900 px `AdminLayout.vue` shows the logo and a "Menu" button
    (`aria-expanded`, `aria-controls="admin-menu"`); the links and "Terug naar de shop" sit behind it. It closes
    after a route change (so after clicking a link) and with Escape, which puts focus back on the button. From
    900 px the sidebar is as before. Checked in headless Chromium at 360 px: closed, the page starts 68 px from the
    top instead of below 19 links; Enter opens it and Tab goes to the first link; Escape closes it with focus on the
    button; a click on "Voorraad" navigates and closes it; axe finds nothing. At 1280 px the button is hidden and the
    sidebar shows.

- [x] **S8. A wrong query value shows Spring's own English error.**
  - Where: `GlobalExceptionHandler.kt:58`. `GET /v1/products?page=abc` answers
    `Failed to convert value of type 'java.lang.String' to required type 'int'; For input string: "abc"`.
  - Why: it shows internals, and it isn't Dutch.
  - Fix: for a field error with the code `typeMismatch`, answer "Dit verzoek klopt niet. Controleer de ingevulde
    gegevens." Handle `MethodArgumentTypeMismatchException` the same way. Add a test in
    `ErrorHandlingIntegrationTest`.
  - Done (9b): `GlobalExceptionHandler.handleMethodArgumentNotValid` answers a binding failure (a field error of
    the wrong type, such as `?page=abc` on a request object) with `bad_request` and the Dutch text.
    `MethodArgumentTypeMismatchException` (a single `@RequestParam` or path variable) already went through
    `handleExceptionInternal` to the same answer; the test covers all three in `ErrorHandlingIntegrationTest`.

- [x] **S9. `npm audit` reports 1 high and 1 moderate.**
  - Where: `frontend/package.json` (`vite` ^5.4.2, and `esbuild` through it).
  - Why: both are in the development server, not in what customers get. They matter on a developer's computer:
    any website open in the browser can read the dev server.
  - Fix: move to the current Vite with the matching `@vitejs/plugin-vue`, then check `npm run build`, `npm run dev`
    and the proxy. `npm audit` should report nothing.
  - Done (9b): `vite` ^8.3.8 and `@vitejs/plugin-vue` ^6.0.11, the current versions on npm on 2026-11-15 (plugin-vue
    6 supports Vite 5 to 8; Vite 8 needs Node 20.19+ or 22.12+, CI uses Node 22). Vite 8 builds with Rolldown and
    Lightning CSS instead of Rollup and esbuild, so esbuild is gone. `vite.config.js` needed no change. Checked in a
    copy of the frontend with the same sources and lockfile: `npm audit` "found 0 vulnerabilities", `npm run build`
    passes (the entry plus the shared chunk are about the same size as before), the dev server answers and its
    `/api` proxy works, and the browser checks of S3, S7, S10 and S14 pass against it. Only `package.json` and
    `package-lock.json` changed in the repository: `frontend/node_modules` still has Vite 5, because the owner's
    dev server on 5173 runs from it and replacing Vite under a running server can break it. Stop it and run
    `npm install` in `frontend/` once.

- [x] **S10. Three accessibility findings from axe.**
  - `color-contrast`: three links in the admin sidebar, among them "Kaartdata" and "Afbeeldingen"
    (`AdminLayout.vue`, `.sidebar-link` at line 158, `--color-on-brand-soft` on the brand blue). 20 hits, all this
    sidebar. Fix: a lighter text colour that reaches 4.5:1.
  - `definition-list`: the `dl` on the order page and the admin order page has children that aren't `dt`/`dd`
    groups (`components/order/OrderDetails.vue`, `views/admin/AdminOrderView.vue`). Fix: only `dt`, `dd` or a `div`
    that wraps a pair.
  - `scrollable-region-focusable`: the shipping rates table at 360 px can only be scrolled by touch
    (`views/content/InfoPageView.vue:46`, `.rates-scroll`). Fix: `tabindex="0"`, `role="region"` and a label.
  - Also do the keyboard walk-through that this audit skipped: main menu, search, cart, checkout, the chat widget
    and the cookie notice. Write what you find below this item.
  - Done (9b), checked afterwards with axe-core 4.14 in headless Chromium against the `dev` profile's demo data:
    0 violations on `/info/verzending` at 360 px, the order page, the cart, the admin order page, and `/admin`,
    `/admin/sync` and `/admin/afbeeldingen` at 1280×720.
    - `color-contrast`: I disagree with the cause. `--color-on-brand-soft` (#d6e4ff) on the sidebar's
      `--color-brand-deep` is about 10:1, and 7.5:1 on `--color-brand-raised`. The hits were the last links
      (Kaartdata, Afbeeldingen, Terug naar de shop): the 19 links are taller than a 720 px window, so they ran out of
      the 100dvh sticky sidebar onto the white page. Fixed with `overflow-y: auto` on the sidebar
      (`AdminLayout.vue`), no colour change.
    - `definition-list`: the VAT line was a `<p>` inside the `dl` of `components/cart/CartTotals.vue`, which both
      order pages (and the cart and checkout) use. It now sits between two `dl`s.
    - `scrollable-region-focusable`: `.rates-scroll` in `InfoPageView.vue` has `tabindex="0"`, `role="region"` and
      the table's caption as its label.
  - Keyboard walk-through (9b, scripted in headless Chromium at 1280 and 360 px, Tab/Enter/Escape only):
    - Skip link first, then logo, search, account, wishlist, cart, the menu buttons. Every stop showed a focus
      style.
    - Main menu: Enter opens a panel (`aria-expanded` true), Tab goes into it, Escape closes it and puts focus back
      on its button. Phone menu: opens as a modal dialog with focus on "Menu sluiten"; Escape closes it and focus
      returns to "Menu openen".
    - Search: typing and Enter goes to `/zoeken?q=…`.
    - Cart drawer: opens with focus on its close button and keeps Tab inside. Found: after Escape focus fell to the
      page body, because the "In winkelwagen" button is disabled while it adds, so the browser had nothing to return
      to. Fixed in `CartDrawer.vue`: it remembers the last element outside the drawer that had focus.
    - Chat widget: Enter opens it with focus in the text field. Found: Escape and the close button hid the panel
      with focus inside it, so focus was lost. Fixed in `ChatWidget.vue`: focus goes back to the chat button.
    - Checkout: every field has a label; the order follows the page. The pay button is skipped when online payment
      is off (it is disabled), which is right.
    - Cookie notice: the "Oké" button is reachable, but as the last stop on the page, after the footer. It doesn't
      block anything and its space is reserved, so I left it there; moving it to the start of the page is an easy
      change if the owner prefers it.

- [x] **S11. Personal API answers may be cached.**
  - Where: `deploy/Caddyfile`, the `handle /api/*` block. The backend sends no `Cache-Control` except for media.
  - Why: order, account and admin answers contain names and addresses. Without a header a browser or a proxy in
    between may keep them.
  - Fix: `Cache-Control: no-store` as the default for `/api/*` in Caddy (`header ?Cache-Control`, so the media
    endpoint keeps its own year-long header).
  - Done (9b): `header ?Cache-Control "no-store"` in the `handle /api/*` block of `deploy/Caddyfile`, as suggested.
    Passes `caddy validate`; the behaviour (media keeps its header) is checked with the Caddy test run under S4.

- [x] **S12. Tests for login-only endpoints, to go with the admin ones.**
  - Where: `AdminEndpointsIntegrationTest` covers admin. Nothing does the same for `@RequiresLogin`.
  - Why: the build prompt asks for authorization tests on every admin and account endpoint.
  - Fix: the same test for every `@RequiresLogin` endpoint (visitor gets 401), a check that everything under
    `/v1/account`, `/v1/collection` and `/v1/wishlist` has the annotation, and ownership tests: another customer's
    address, collection entry, order, ticket list and binder selection give 404 or nothing.
  - Done (9b): `LoginEndpointsIntegrationTest` finds every `@RequiresLogin` endpoint from the request mappings (30
    today) and checks a visitor gets 401, and that everything under `/v1/account`, `/v1/collection`, `/v1/wishlist`
    and `/v1/binder/selection` has the annotation. The discovery moved to `Endpoints.kt`, shared with
    `AdminEndpointsIntegrationTest`. Ownership: address, collection entry, ticket list and card wishlist already had
    tests; added a paid order of another account (`CheckoutIntegrationTest`) and another account's binder selection
    (`BinderIntegrationTest`).

- [ ] **S13. The README needs its phase 9 update.**
  - Where: `README.md`. The first paragraph is still the first draft, with typos, and there is no overview of the
    phases.
  - Fix: as the build prompt says: what the app does, how to run it, the environment variables (with C3's new
    ones), syncing card data for all five games, testing payments with the Stripe CLI, and a phase overview with
    links to `docs/phases/`. Most sections exist; check each against the code rather than rewriting them.

- [x] **S14. Repeat the password when making an account.** (Added in 9b at the owner's request, instead of the
  confirmation mail of N3.)
  - Where: every place a customer chooses a new password: the register page (`RegisterView.vue`), the reset page
    (`ResetPasswordView.vue`) and the "make an account" form a guest gets after paying (the order placed page,
    `POST /v1/orders/{number}/account`). Login stays as it is.
  - Why: a typo in a new password locks the customer out of the account they just made.
  - Fix: a second "repeat password" field with a label and `autocomplete="new-password"` on both; the form refuses
    to submit when they differ, with "De wachtwoorden zijn niet hetzelfde." tied to the second field. The backend
    checks it too: `passwordConfirmation` in `RegisterRequest`, `ResetPasswordRequest` and `GuestAccountRequest`,
    refused with `validation_failed` and the same message. Tests for the mismatch.
  - Done (9b): `FormField.vue` got an `error` prop (`aria-invalid`, the error first in `aria-describedby`, red
    border) and a `focus()`; no form had per-field errors yet, so this is the new pattern. The register page, the
    reset page and the guest's account form on the order placed page have a second field ("Herhaal je
    (nieuwe) wachtwoord", `autocomplete="new-password"`); a mismatch stops the submit, shows the message under that
    field and moves focus there; typing clears it. No other place chooses a new password (login and the account
    page don't). Backend: `passwordConfirmation` in the three requests, checked by `service/PasswordRules.kt` from
    `AuthOrchestrator` and `OrderOrchestrator` before anything else happens, so a mismatched reset keeps the link
    usable. Tests in `AuthFlowIntegrationTest` and `CheckoutIntegrationTest`; existing calls updated. Checked in
    headless Chromium on the register and reset pages; the guest form after payment was only checked by its test
    and by reading the code (it needs a paid order).

## Nice to have

- [ ] **N1. Started without a profile, the backend runs `dev`.** `application.yml:10` (`profiles.default: dev`): an
  in-memory database, the H2 console and demo accounts. `deploy/compose.yml` sets `prod`, so this only bites when
  someone starts the jar another way on a server. Fix: refuse to start on `dev` when `APP_BASE_URL` is `https`.
- [ ] **N2. A deploy cuts off requests that are running.** No `server.shutdown: graceful`. Stripe sends a webhook
  again, a customer in checkout sees an error. Fix: graceful shutdown with a 20 second timeout (the container gets
  30).
- [x] **N3. Registering doesn't check that the email address is yours.** Someone can make an account on another
  person's address. That person gets it back with "wachtwoord vergeten", which logs the other out, and no orders or
  tickets are tied to an account by email (checked). Registering also tells whether an address has an account
  (`email_taken`). Owner decision: a confirmation mail before the account works.
  - Decided (9b): the owner chose no confirmation mail, and a repeated password instead. That is S14, done.
- [ ] **N4. No "change password" and no "delete my account" on the account page.** A password can only be changed
  through the reset mail. Deleting can go through a ticket; say so in the privacy policy.
- [ ] **N5. Foreign keys without an index:** `binder_selections(card_id)`, `card_wishlist_entries(card_id)`,
  `drop_queue_entries(user_id)`, `product_reviews(user_id)`, `set_release_products(product_id)`,
  `set_releases(set_id)`, `mystery_box_prizes(card_id)`, `support_messages(author_id)`, `ai_usage(user_id)`,
  `order_status_changes(admin_user_id)`, `stock_movements(admin_user_id)`. All small tables today. A new migration
  (V13) for the first six is enough; never change an old migration.
- [ ] **N6. Lists without pages:** admin news, discount codes, drops and release calendar, "Mijn tickets", and the
  public pre-order list. They grow slowly. Page them when one passes a few hundred rows.
- [ ] **N7. Eight images without `loading="lazy"`.** Most are at the top of the page, where it shouldn't be lazy
  (logo, hero, banners). Check `views/catalog/SetView.vue:205`, `views/shop/ShopListView.vue:250` and
  `views/content/NewsArticleView.vue:49`, and add `width` and `height` where they are missing so the page doesn't
  jump.
- [ ] **N8. Order and ticket keys are stored as plain text,** unlike session tokens. Someone who reads the database
  can open every order page. Hashing them needs a migration and a change to the mails; low priority.
- [ ] **N9. A paid amount that differs from the order only writes a log line and a note** (`OrderPaymentService.kt:63`).
  The amount comes from our own Stripe session, so it shouldn't happen. Show such orders with a warning in the
  admin order list.
- [ ] **N10. GitHub Actions are pinned to a version tag, not a commit** (`.github/workflows/deploy.yml`). A tag can
  be moved by the action's owner. Pin to commit hashes.
- [ ] **N11. One broken set symbol:** `https://assets.tcgdex.net/univ/mc/2021swsh/symbol.webp` gives 404 on
  `/kaarten/pokemon`. Check that a broken symbol hides itself instead of showing a broken image.
- [ ] **N12. Hibernate warns at every start** that `hibernate.dialect` doesn't need to be set
  (`application-prod.yml:13` and the other profiles). Remove it where Postgres is detected by itself.

## For the owner, not for 9b

- Backups are on the same server as the shop (your choice in phase 7). Phase 10 can add a copy elsewhere.
- Stripe's webhook can't get past the preview's shared login. That is fine while payments are off in the preview;
  phase 10 opens it.
- The decisions in C4 (maximum per cart line, finding out at checkout) and N3 (confirming an email address).

## Checklist for 9b, in order

1. C2: body size limit in Caddy and in the backend, with a test.
2. C4: cart no longer reveals stock; maximum per line 10; drop cart checked; tests updated.
3. S2: tight `checkout` limit and a cap on open checkouts per email and IP.
4. S1: per-email limits for login and reset mails.
5. C3: SMTP login, TLS and timeouts, through compose files and env examples.
6. S8: Dutch answer for a wrong query value, with a test.
7. S12: tests for login-only endpoints and ownership.
8. S11: `no-store` for `/api/*` in Caddy.
9. S3: page titles, descriptions and canonical links.
10. S6: a real 404 page.
11. S5: sitemap, robots file and `noindex` on private pages.
12. S4: Open Graph tags from the backend for products, drops and news.
13. S7: admin menu behind a button on small screens.
14. S10: the three axe findings and the keyboard walk-through.
15. S14: repeat the password wherever a customer chooses a new one, checked in the page and in the backend.
16. S9: Vite upgrade until `npm audit` is clean.
17. C1: Spring Boot upgrade. Last of the code changes, so everything above is tested on the version it was written
    on, and then once more after the upgrade.
18. S13: README.
19. Nice to have, as far as time allows: N1, N2, N5, N7, N11, N12 first.
20. Done when: `./gradlew test`, `npm run build`, and the `prod` profile starting cleanly on an empty database.
