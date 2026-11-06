# Build prompt: TCG webshop + collection tracker (Emetain/pokemon-webshop)

The skeleton from the setup prompt is already in this repository (Kotlin + Spring Boot backend, Vue 3 + Vite frontend, Docker Compose, profiles dev/postgres/docker/prod). Build the full application on top of it, in phases. Read `README.md` first and follow its conventions everywhere (Controller → Orchestrator → Service/Repository, per-feature file names, `/api/v1/...` paths, `services/api.js`, composable stores, CSS variables only).

## What we are building

A Dutch trading card game webshop that focuses on Pokémon, but also sells Disney Lorcana, Magic: The Gathering and One Piece Card Game products. Next to the shop, customers get a free collection tracker: they can mark which cards they own for every set of all four games, see how far along they are per set, and see what their collection is worth based on market prices. Pokémon collectors also get a Pokédex collection: one slot per Pokémon, filled when they own at least one card of it.

The shop owner manages products, stock, orders and drops in an admin area.

## General rules

- **Language:** the whole UI is in Dutch. Put all UI text in `frontend/src/data/i18n/nl.js` (one object, nested by feature) and read it through a small `t('key')` helper in `utils/`, so English can be added later without touching components. Code, comments and commit messages are in English.
- **Money:** store amounts as integer cents (`Long`) in the backend, never `Double`. All shop prices are in EUR, including 21% VAT. Show "incl. btw" where prices are shown.
- **Dates:** store as `Instant` (UTC). Show them in the `Europe/Amsterdam` time zone.
- **Region setting:** a `useRegion()` store with the customer's country. Default is the Netherlands. It is saved in `localStorage` (inside try/catch) and on the user's account when logged in. The country decides shipping costs and which payment methods are shown. The region does not change card prices.
- **Currency setting:** logged-in customers choose EUR or USD in their profile. Card market prices are shown in that currency, converted with the ECB's daily reference rate. Shop prices, orders and payments always stay in EUR.
- **Secrets:** never hardcode keys. Add every new environment variable to the matching `.env.example` with a placeholder and a comment.
- **Database:** Hibernate manages the schema in `dev`, `postgres` and `docker`. Every phase that adds or changes entities also adds a Flyway migration in `backend/src/main/resources/db/migration/` (`V1__...`, `V2__...`) that matches the entities exactly, so the `prod` profile (Flyway + `ddl-auto: validate`) keeps working.
- **Demo data:** a `DevDataSeeder` that only runs in the `dev` profile creates an admin account, a customer account, some products for all four games, stock, and one upcoming drop. Log the demo login details on startup. Never seed data in `prod`.
- **External data:** never call card APIs while handling a customer request. Import card data into our own database with a scheduled sync job and serve everything from there. Store image URLs from the provider, don't download images. Before writing each API client, read that API's current documentation and terms (rate limits, attribution, image use), and follow them. Show the required attribution in the footer.
- **Tests:** every service with logic (prices, stock, collection progress, drop limits, order totals) gets unit tests. Each phase adds at least one integration test for its main flow.
- **Design:** clean and modern, mobile first, works from 360px wide. Define colours, spacing, radius and font sizes as tokens in `main.css`. Every game gets its own accent colour token. Use `lucide-vue-next` for icons. Use the `frontend-design` skill if it is available.

## How to work in phases

Do the phases below in order. For each phase:

1. Build everything listed for that phase, and nothing from later phases.
2. Run the checks listed under "Done when", plus `./gradlew test` and `npm run build`. Fix anything that fails.
3. Write `docs/phases/phase-NN.md` with: what was built, the main files, new environment variables, the checks you ran with their results, and any decisions or open issues.
4. Commit everything for the phase with the message `Phase N: <title>`, then tag it `phase-N` so I can compare phases with `git diff phase-2 phase-3`.
5. Stop and give me a short summary. Wait until I say "next" before you start the next phase.

Don't push, and don't start the next phase on your own.

## Changes made during the build

These decisions came up while building and apply to every later phase. The phase texts below already include them. Details and research are in `docs/phases/`.

- **Shop name and design:** the shop is called Uithoorn TCG, white and blue, with visible Pokémon touches. Follow the familiar patterns of big Dutch TCG shops such as catchyourcards.nl for shop pages. Never copy their logo, images or text.
- **Prices:** Pokémon and Magic use Cardmarket (EUR). Lorcana and One Piece use TCGplayer prices (USD), delivered by Lorcast and the OPTCG API, because Cardmarket's files can't be matched to these cards reliably. A card price always shows its source and date.
- **Dollar prices are converted, not left out.** A converted price is better than "geen prijs". Customers on the default euro setting see every price in euros. Lists sort on `Card.sortPriceCents`, the list price in euro cents.
- **Currency setting:** customers choose EUR or USD in their profile. This only changes how card prices are shown. No "≈" sign in front of converted prices: the card page says in a small line that a price was converted.
- **Prices stay as the source gives them.** Some Pokémon cards share a price with a card of the same name, sometimes because TCGdex links them to one Cardmarket product. The owner decided to keep showing these. Don't hide or correct them.
- **Addresses have no name.** They use the name from the profile.
- **Card images are never changed.** Scryfall's terms forbid covering, cropping, distorting, blurring or tinting images, and adding anything on top. Use `CardImage.vue` for every card image, and put prices, labels and badges below the image.
- **Skipped on purpose:** TCG Pocket and digital Magic cards, because they aren't physical cards.
- **Card lists:** use the sorting, filters and URL pattern of `CardFilters.vue` for new lists of cards. Filters and sorting live in the URL, so every view can be shared.
- **Owned and missing cards:** a blue frame around the image of owned cards, with "In bezit" or "Gemist" below it. Never change the image itself. Use `CollectionControls.vue` for adding and removing.
- **Share page:** shows the first name, progress, cards and value. Never the full name, because that is also the name on orders.
- **Value graph:** added at the owner's request, inspired by the portfolio graph in the Collectr app. Card prices have no history, so one value per customer per day is saved. It only starts when a customer starts, and can't be filled in afterwards.
- **Viewer on public endpoints:** a nullable `@CurrentUser` parameter gives the logged-in user when there is one. Card lists then include what the viewer owns.
- **Before going live:** the owner does a legal check and has asked to be reminded. See phase 8.

---

## Phase 1: Foundation

Goal: accounts, roles, layout and navigation, so every later phase has a place to live.

Backend:
- `User` entity: email (unique), password hash (BCrypt via `spring-security-crypto`), display name, role (`CUSTOMER` or `ADMIN`), country (default `NL`), created at.
- Register, login, logout and "who am I" endpoints. Use a random session token stored in the database and sent as an `HttpOnly`, `Secure` (except in dev), `SameSite=Lax` cookie. Add an interceptor that loads the current user, and an `@AdminOnly` annotation that returns 403 for non-admins.
- Password reset by email: a single-use token that expires after 1 hour. Mails go to Mailpit locally.
- Rate limit login and register per IP (simple in-memory limiter is fine for now).
- A global exception handler that returns `{ "error": "<code>", "message": "<Dutch text>" }` with the right status codes.
- Address entity for later use: street, house number, postcode, city, country. An address has no name of its own: it uses the name from the profile.

Frontend:
- Layout: top bar (shipping notice), header with logo placeholder, search field (not working yet), account icon, wishlist icon, cart icon with count, and main navigation: Pokémon, Lorcana, Magic, One Piece, Accessoires, Pre-order, Drops, Collectie. Mobile gets a slide-in menu.
- Footer with info links (placeholders for now), payment method icons as text labels, and the data attribution line.
- Pages: login, register, forgot password, reset password, my account (profile, country, addresses).
- `useSession()` store, route guards for logged-in and admin routes, an empty `/admin` page with its own admin layout and sidebar.
- `t()` helper and `nl.js`.

Done when:
- Register, log in, log out and reset a password (mail visible in Mailpit at `localhost:8025`) all work.
- A customer gets 403 on an admin endpoint and is redirected away from `/admin`.
- The layout looks right at 360px and 1280px.

## Phase 2: Card catalog for all four games

Goal: every set and every card of all four games in our database, browsable on the site.

Data sources (check the current docs first):
- **Pokémon:** TCGdex (`tcgdex.dev`). It includes Cardmarket price data per card and the National Pokédex number(s) of each card.
- **Magic: The Gathering:** Scryfall. Use the daily bulk data file (`default_cards`) instead of thousands of API calls. Scryfall's `prices.eur` comes from Cardmarket.
- **Disney Lorcana:** Lorcast (`lorcast.com`).
- **One Piece Card Game:** OPTCG API (`optcgapi.com`).

Backend:
- Enum `Game` with `POKEMON`, `LORCANA`, `MTG`, `ONE_PIECE`.
- Entities: `CardSet` (game, external id, code, name, series/era, release date, card count, logo/symbol URL) and `Card` (set, external id, number, name, rarity, supertype/type, image URLs small and large, finishes/variants, National Pokédex numbers for Pokémon).
- `CardPrice`: card, variant, source (`CARDMARKET` or `TCGPLAYER`), currency, trend price and average price in cents, updated at. Pokémon and Magic use Cardmarket. Lorcana and One Piece use the TCGplayer prices from Lorcast and the OPTCG API, because Cardmarket's public files can't be matched to these cards reliably. When a source has no price for a card, store nothing. Never make up a price.
- A `CardDataProvider` interface with one implementation per game, so a provider can be swapped later (for example for a paid Cardmarket price source). Each provider maps the external data to our entities.
- `CardSyncService` with a scheduled job (daily at night, configurable) and an admin endpoint to start a sync per game. Syncs are idempotent upserts, respect rate limits, and log progress in a `SyncRun` entity (game, started, finished, status, counts, error).
- Search endpoint across games: name, game, set, rarity, with pagination. Add database indexes for these filters.
- Sorting and filtering on set pages and search: sort by number, name and price in both directions. Filter on kind of card (such as Trainer or Energy), energy type or colour, rarity, variant, and "with price only".

Frontend:
- `/kaarten/:game` lists the sets of a game, grouped by series/era and sorted newest first, with logos and release dates.
- `/kaarten/:game/:setCode` shows a grid of all cards in the set with filters (rarity, variant) and the Cardmarket trend price when there is one ("geen prijs" when there isn't).
- Card detail as a modal or page: large image, details, prices with "bron: Cardmarket, bijgewerkt op ...".
- The header search works for cards.

Admin:
- Sync page: last sync per game, counts, errors, and a button to start a sync.

Done when:
- A full sync runs for all four games and the counts per game look complete (compare the number of sets with the source).
- Browsing, filtering and searching cards works, and a Pokémon, Magic, Lorcana and One Piece card each show correctly.
- Running a sync twice does not create duplicates.

## Phase 3: Collection and Pokédex

Goal: customers track what they own and see their progress and value.

Backend:
- `CollectionEntry`: user, card, variant, quantity, condition (optional), added at. Unique per user + card + variant.
- Endpoints to add, update quantity and remove; bulk add (a whole list of card ids at once).
- Progress per set: owned unique cards / total cards in the set, as a percentage. Count variants separately only for "master set" progress; the normal progress counts each card number once. Use the number of cards in our own database as the total, not `CardSet.cardCount`: some sources announce more cards than they deliver, and those sets could never reach 100%.
- Progress per game and overall.
- Collection value: sum of quantity × trend price of the owned variant, converted to the customer's currency with the stored ECB rate, plus how many cards have no price. Prices can be in EUR or USD, so add a backend conversion with unit tests. Do the sum in cents with `BigDecimal`, never `Double`.
- Pokédex: import the list of all Pokémon species (National Pokédex number, name, generation) from PokéAPI during the Pokémon sync. A slot is filled when the user owns at least one card with that Pokédex number. Progress overall and per generation.
- Wishlist for cards (separate from product wishlist).
- Option to make a collection public through a share link with a random slug (off by default). The public page shows the first name, progress, cards and value, without the value graph.
- Value history: save the value of every collection once a day after the card sync, in euros, one row per customer per day. The last point of the history is today's live value.
- Home binder: the data for the 9-pocket binder in the home hero, with pages. Sources: popular, own selection, most expensive cards owned, Pokédex, most expensive cards in general. Save the choice on the user. Define "popular" (for example, the cards in the most collections). `Card.sortPriceCents` already gives the most expensive cards. Add an index when a query needs it.

Frontend:
- Home binder: the binder in the hero shows real cards and has page buttons. Logged-in customers see their most expensive cards by default, logged-out visitors see the most popular cards. Customers change what it shows in their settings. Nothing goes on top of the card images.
- `/collectie`: overview per game with progress bars, total value, most valuable cards.
- Value graph on `/collectie`, like a stock chart: the change over the chosen period, a line with a tooltip, and the periods 7D, 1M, 3M, 6M, 1J and Alles.
- On set pages, logged-in users can see which cards they own and which are missing, and can tick cards off with one click, plus quantity +/-. Greying out images isn't allowed for Magic under Scryfall's terms, so owned cards get a blue frame around the image and missing cards a "Gemist" label below it.
- Toggle per set between "normale set" and "master set" progress.
- `/collectie/pokedex`: a grid of all Pokémon with Pokédex number and name, filled slots show the image of one of the owned cards, filter per generation, and a progress bar per generation. Clicking a slot shows all cards of that Pokémon and which ones you own.
- Missing-cards list per set, with a link to the matching singles in the shop (once products exist).
- Logged-out visitors see a short explanation and a register button.

Done when:
- Adding and removing cards updates progress and value right away.
- The home binder shows the chosen source, pages work, and a logged-out visitor sees the popular cards.
- Pokédex progress matches the owned cards (test with a few known cards).
- The value graph shows saved days, and adding a card moves today's point right away.
- Unit tests cover the progress and value calculations.

## Phase 4: Shop products and stock

Goal: a browsable shop and the admin tools to fill it.

Backend:
- `Product`: game, type (`BOOSTER_BOX`, `ELITE_TRAINER_BOX`, `BOOSTER_BUNDLE`, `BOOSTER_PACK`, `SLEEVED_BOOSTER`, `BLISTER`, `TIN`, `COLLECTION_BOX`, `STARTER_DECK`, `SINGLE`, `GRADED_SLAB`, `BULK`, `MYSTERY_BOX`, `ACCESSORY`), name, slug, description (plain text with line breaks), language (`EN`, `JP`, `CN`, `DE`, `FR`), optional link to `CardSet`, optional link to `Card` (for singles and slabs), condition (singles), grading company and grade (slabs), price, optional sale price, images, status (`DRAFT`, `ACTIVE`, `ARCHIVED`), pre-order fields (is pre-order, release date, ships from date), created at.
- Accessory sub-types: sleeves, toploaders, binders, deck boxes, playmats, display cases, storage boxes.
- Stock: quantity on hand, quantity reserved, and a `StockMovement` log (product, change, reason: `PURCHASE`, `SALE`, `RETURN`, `CORRECTION`, `DROP_ALLOCATION`, `RESERVATION`, `RELEASE`, note, admin user, time). Stock only changes through a `StockService` that always writes a movement. Use optimistic locking so two people can't buy the last item.
- Low-stock threshold per product.
- Image upload for products (store files on disk in a configurable folder, served under `/api/v1/media/...`; keep it behind an interface so cloud storage can be added later).
- Public endpoints: list with filters (game, type, set, language, price range, in stock, pre-order, on sale), sorting (new, price, popularity), search, product detail, "new arrivals", "bestsellers" (based on order lines later; newest active products until then).
- Product wishlist and a "mail me when back in stock" signup. `/verlanglijst` already exists for cards from phase 3: add products there.

Frontend:
- Home page: hero banner area (managed in admin), upcoming/live drop block, new arrivals, bestsellers, browse per game, latest pre-orders.
- Category pages per game, with navigation by series → set (like the big Dutch shops) and by product type. The Pokémon series in `data/navigation.js` are a fixed list that real sets from the catalog can replace now.
- Product cards: image, name, price, sale price with strike-through, badges ("Pre-order", "Uitverkocht", "Aanbieding", "Laatste X"), add-to-cart button (cart arrives in phase 5, so show the button but disable it for now).
- Product page: image gallery, price, stock status, pre-order ship date, description, for singles a link to the card in the catalog and its market price with source as a reference, related products from the same set.
- Card set pages from phase 2 show "te koop" below cards that have a single in stock, never on the image.
- The list of missing cards from phase 3 (`SetMissingView.vue`) links each row to the matching single.

Admin:
- Products: table with search and filters, create/edit form with image upload, duplicate product, archive.
- Quick-add a single by searching the card catalog and choosing condition and price, with the market trend price in euros shown as a suggestion. Never use it as the shop price automatically: Scryfall says its prices aren't fit for a storefront.
- Stock: adjust stock with a reason, movement history per product, low-stock overview.
- CSV import and export for products and stock.

Done when:
- An admin can create every product type, upload images and change stock, and every change shows in the movement log.
- Filters and sorting on the shop pages work and give correct results.
- A stock test shows that two parallel purchases of the last item can't both succeed.

## Phase 5: Cart, checkout and orders

Goal: customers can buy, pay and follow their order.

Backend:
- Cart for guests (cookie id) and logged-in users; a guest cart merges into the account cart on login.
- Checkout reserves stock for 15 minutes; a scheduled job releases expired reservations.
- Shipping: `ShippingRate` per country with price, free-shipping threshold and delivery time text, managed in admin. Start with NL and BE.
- Payments with Stripe Checkout in test mode: iDEAL, Bancontact (BE), card. Keys from `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET`. The order becomes paid only through the verified webhook, never through the redirect.
- `Order` and `OrderLine` with copies of name and price at the time of purchase, always in EUR whatever currency the customer chose for card prices, status (`PENDING_PAYMENT`, `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`, `REFUNDED`), shipping address, totals, track & trace code.
- Orders with pre-order products show their expected ship date. Option per order: ship everything together, or ship in-stock items first.
- Emails (HTML + text): order confirmation, payment received, shipped with track & trace, cancelled.
- Guest checkout allowed, with an option to create an account after paying. Addresses have no name of their own: an order copies the profile name for logged-in customers, and guests fill in a name at checkout.

Frontend:
- Cart drawer and cart page, quantity changes, shipping estimate for the selected country, progress to free shipping ("nog €X tot gratis verzending").
- Checkout: address, shipping, summary, accept terms checkbox, redirect to Stripe, success and failed pages.
- My account: order history and order detail with status timeline.

Admin:
- Orders table with filters per status, order detail, change status, add track & trace (sends the mail), cancel and refund through Stripe, print a packing list.
- Shipping rates management.
- Simple dashboard: revenue today/week/month, open orders, low stock.

Done when:
- A full test payment works end to end with the Stripe CLI forwarding webhooks, and the confirmation mail appears in Mailpit.
- An abandoned checkout releases its stock after the reservation expires.
- Refreshing the success page doesn't create a second order or change stock twice.

## Phase 6: Drops

Goal: the shop can do limited, timed releases that are fair and hard for bots to abuse. Drops are where shops like this get the most traffic and the most bot activity, so make this robust.

Backend:
- `Drop`: name, slug, description, banner image, starts at, optional ends at, status (`DRAFT`, `SCHEDULED`, `LIVE`, `ENDED`), requires account (default true), max items per customer for the whole drop.
- `DropProduct`: drop, product, quantity allocated to this drop (moved from normal stock with a `DROP_ALLOCATION` movement), max per customer for this product, optional drop price.
- A scheduler switches drops from `SCHEDULED` to `LIVE` and to `ENDED` at the right time. At the end, unsold allocated stock goes back to normal stock (or stays reserved, chosen per drop).
- Waiting room: visitors can join the drop page before it starts. At the start time, everyone already waiting is put in random order (so refreshing at exactly the right second gives no advantage), later arrivals join at the back. The backend admits a configurable number of people per minute. Admitted people get a time-limited access token for the drop checkout. Show the queue position and an estimated wait.
- Limits are checked on the server per account, per product and per drop, at add-to-cart and again at payment. Also limit per shipping address and per payment email.
- Bot protection: account required by default, rate limiting per IP and per account on drop endpoints, a honeypot field, and blocking of obvious repeat orders.
- "Mail me when this drop starts" signup, and a reminder email shortly before the start (time configurable).
- After the drop: report with sold per product, number of buyers, average time to sell out.

Frontend:
- `/drops`: upcoming, live and past drops.
- Drop page: banner, products with drop prices and limits, countdown to the start, "herinner mij" button, then the waiting room with position, then the shop part with live stock counters ("nog 12 over"). Show a clear "uitverkocht" state.
- A drop banner on the home page and a "LIVE" badge in the navigation while a drop is live.

Admin:
- Create and edit drops: choose products, allocate stock, set limits, prices and times, preview the drop page.
- Live view during a drop: queue length, admitted, sold per product, remaining stock, and buttons to pause the queue, change the admit speed, or end the drop early.
- Drop report afterwards.

Done when:
- A drop scheduled two minutes ahead switches to live by itself, the countdown matches, and people in the waiting room are admitted in batches.
- Limits can't be bypassed by calling the API directly (add tests for this).
- A test with many simulated buyers never sells more than the allocated quantity.

## Phase 7: Extras

Goal: the features that make the big Dutch TCG shops feel complete.

- **Discount codes:** percentage or fixed amount, minimum order amount, valid dates, max uses, once per customer, optionally only for certain games or product types, never on drop prices unless enabled.
- **Bundles:** "stel je eigen bundel samen", for example a sealed product plus a matching acrylic display case with a discount on the case. Admin defines which products can be combined and the discount.
- **Case deals and quantity discounts:** a tiered price per product (for example 6+ boosters cheaper per piece).
- **Pre-order page:** all pre-orders with release dates on a timeline.
- **Release calendar:** upcoming sets per game, managed in admin, with links to the pre-orders.
- **News:** simple articles (title, image, text, date, game tag) managed in admin, shown on `/nieuws` and on the home page.
- **Newsletter signup** stored in the database with double opt-in by email, and an admin export.
- **Kaarten verkopen (buylist request):** a form where customers list cards they want to sell (they can pick cards from the catalog or from their own collection), with condition and photos. The admin sees requests, gives an offer, and changes the status. No automatic payouts.
- **Reviews:** customers who bought a product can leave a star rating and short review; admin approves them before they appear.
- **Info pages** with editable content in admin: verzending en levertijd, retourneren (14 dagen bedenktijd, with the note that opened sealed products can't be returned), algemene voorwaarden, privacybeleid, FAQ, contact form (mails to the shop address), over ons. Mark the legal pages clearly as drafts that must be checked before going live.
- **Cookie notice** for functional cookies only. Don't add tracking.

Done when:
- Each feature works from both the customer side and the admin side, and discount codes calculate correctly in combination with bundles, tiered prices and shipping (unit tests for every combination).

## Phase 8: Polish and hardening

Goal: ready to show, ready to deploy.

- Go through every page at 360px, 768px and 1280px and fix layout issues. Check keyboard navigation, focus styles, alt texts and colour contrast.
- Add loading, empty and error states everywhere they are missing.
- SEO: page titles and meta descriptions per page, Open Graph images for products and drops, readable slugs, a `sitemap.xml` and `robots.txt`. Keep the public collection pages out of the sitemap and mark them `noindex`.
- Performance: pagination everywhere lists can grow, database indexes for all filters, lazy-loaded images, route-level code splitting.
- Security review: authorization on every admin and account endpoint (add tests), input validation, file upload checks (type and size), no secrets in the repo, security headers.
- Start the app with the `prod` profile against the Docker Postgres and check that all Flyway migrations run and Hibernate validation passes.
- Update the root `README.md`: what the app does, how to run it, environment variables, how to sync card data, how to test payments with the Stripe CLI, and the phase overview with links to `docs/phases/`.
- Production basics left open by earlier phases: a way to create the first admin (only `dev` seeds accounts), SMTP username and password settings, Swagger closed in `prod`, `FORWARD_HEADERS_STRATEGY=native` behind a proxy, a heap of at least 2 GB for the Magic sync, and the `npm audit` issue in dev tooling.
- **Go-live reminder:** before anything goes live, remind the owner to do the legal check. They asked for this reminder. It covers the Wizards Fan Content Policy, Ravensburger's Community Code and Lorcast's "prohibited from charging" notice for a commercial shop, reusing TCGplayer prices from Lorcast and the OPTCG API, Cardmarket and ECB data, the PokéAPI data, the ball emblem in the logo, the public collection pages and saved value history in the privacy policy, and the draft legal pages. Also bring up the One Piece images: they load from one person's server and carry a "SAMPLE" watermark. Full notes are in `docs/phases/phase-02.md` and `docs/phases/phase-03.md`.

Done when:
- All tests pass, the production build works, and the `prod` profile starts cleanly on an empty database.

---

## Out of scope

Don't build these unless I ask: livestreams or "rip & ship", card scanning with the camera, a real Cardmarket API integration (it is closed to new applications), automatic shipping labels, other games than the four above, and translations other than Dutch.
