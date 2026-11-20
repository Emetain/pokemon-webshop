# Build prompt: TCG webshop + collection tracker (Emetain/pokemon-webshop)

The skeleton from the setup prompt is already in this repository (Kotlin + Spring Boot backend, Vue 3 + Vite frontend, Docker Compose, profiles dev/postgres/docker/prod). Build the full application on top of it, in phases. Read `README.md` first and follow its conventions everywhere (Controller → Orchestrator → Service/Repository, per-feature file names, `/api/v1/...` paths, `services/api.js`, composable stores, CSS variables only).

## What we are building

A Dutch trading card game webshop that focuses on Pokémon, but also sells Disney Lorcana, Magic: The Gathering, One Piece Card Game and (since phase 8) Riftbound products. Next to the shop, customers get a free collection tracker: they can mark which cards they own for every set of all five games, see how far along they are per set, and see what their collection is worth based on market prices. Pokémon collectors also get a Pokédex collection: one slot per Pokémon, filled when they own at least one card of it.

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

- **Shop name and design:** the shop is called Poketopia (renamed from Uithoorn TCG on 2026-11-10), white and blue, with visible Pokémon touches. Follow the familiar patterns of big Dutch TCG shops such as catchyourcards.nl for shop pages. Never copy their logo, images or text.
- **Prices:** Pokémon and Magic use Cardmarket (EUR). Lorcana and One Piece use TCGplayer prices (USD), delivered by Lorcast and the OPTCG API, because Cardmarket's files can't be matched to these cards reliably. A card price always shows its source and date.
- **Dollar prices are converted, not left out.** A converted price is better than "geen prijs". Customers on the default euro setting see every price in euros. Lists sort on `Card.sortPriceCents`, the list price in euro cents.
- **Currency setting:** customers choose EUR or USD in their profile. This only changes how card prices are shown. No "≈" sign in front of converted prices: the card page says in a small line that a price was converted.
- **Prices must be right (changed 2026-11-19).** Until phase 11 the rule was to show prices as the source gives them, even when TCGdex links several prints of a Pokémon card to one Cardmarket product. On 2026-11-19 the owner reversed that: a Special Illustration Rare must not show the price of the Double Rare with the same name. A correct dollar price, converted, is better than a known wrong euro price. Phase 11 fixes this.
- **Premium (added 2026-11-19):** a paid subscription bought on the site, €6.99 per month or €59.99 per year, with the card scanner beyond 100 free scans, the "Max" period of the value graph and the pre-grade check. Admins always have it. See phase 11. More Premium follows in phase 12 (no ads, price history and alerts, export, extra binders, themes), and the Android app with widgets comes last, in phase 13. No early access to drops for Premium: the owner finds that unfair.
- **The Premium popup (added 2026-11-20):** a customer without Premium who tries a Premium feature gets a popup with what Premium adds and the plans, not a new page. `PremiumDialog.vue`, opened with `openPremium(feature)` from `stores/premiumDialog.js`. A link to a page with `meta.premium` opens it instead of the page (an address typed in, shared or coming back from Stripe still opens the page), and every API answer `premium_required` opens it by itself. Every new Premium feature uses it.
- **Colours and dark mode (2026-11-21, phase 12):** dark mode and the colour themes swap the tokens in `main.css` through `data-mode` and `data-theme` on `<html>`. Text and icons in the brand colour use `--color-brand-ink`; `--color-brand` is a background for white text. A new colour token also gets a dark value. The default look stays light, white and blue. The app of phase 13 uses the same tokens and themes.
- **Home page order (2026-11-20):** right below "Nieuw binnen" come the logged-in customer's value graph (the same `ValueChart.vue` as the collection) and the tiles for the value graph, the card scanner and the pre-grade check, then "Kies je spel" and "Populair".
- **Photos (2026-11-20):** every place that asks for a photo offers "Foto maken" and "Foto kiezen". On a phone "Foto maken" opens the phone's own camera app; on a computer it opens the live camera view `CameraCapture.vue`, never mirrored, cropped to the card frame.
- **Addresses have no name.** They use the name from the profile.
- **Card images are never changed.** Scryfall's terms forbid covering, cropping, distorting, blurring or tinting images, and adding anything on top. Use `CardImage.vue` for every card image, and put prices, labels and badges below the image.
- **Ads:** added by the owner on 2026-11-16: other companies' ads through Google AdSense, behind the `ADS` switch (off by default). Only on the free card and news pages through `AdSlot.vue`, never on shop, cart, checkout, drop, collection or account pages. Google's own consent message asks for consent. Details in `docs/phases/phase-10.md`.
- **Skipped on purpose:** TCG Pocket and digital Magic cards, because they aren't physical cards.
- **Card lists:** use the sorting, filters and URL pattern of `CardFilters.vue` for new lists of cards. Filters and sorting live in the URL, so every view can be shared.
- **Owned and missing cards:** a blue frame around the image of owned cards, with "In bezit" below it. Missing cards get no label: the owner had "Gemist" taken out on 2026-11-10, and the collection filter and the list of missing cards say "Nog niet in bezit". Never change the image itself. Use `CollectionControls.vue` for adding and removing.
- **Share page:** shows the first name, progress, cards and value. Never the full name, because that is also the name on orders.
- **Home binder:** logged-out visitors see the most expensive Pokémon cards, logged-in customers their own most expensive cards. A choice always stays, and is made in My account only, not on the home page. The binder opens like a real one: page 1 next to the inside of the cover, then 2 and 3, then 4 and 5. A phone turns one page at a time.
- **Card tiles:** logged-in customers get a + next to the price. It turns into `- count +` in the same element.
- **Admin images:** the owner can replace the picture of a card or set at `/admin/afbeeldingen`, and browse all cards without a picture there, by game and set, sorted by set, name or Pokédex number, 25, 50 or 100 per page from a dropdown. Uploads sit next to the source's images and a sync leaves them alone. `MediaStorage` handles the files.
- **Every product in the shop:** the owner wants all products that exist to be in the shop, shown as "Uitverkocht" while there's no stock. See phase 4.
- **Except sets out of print:** the owner can't get those, so their products only show while they are in stock, and their sets stay out of the navigation. A set is in print while it's legal in its game's rotating format, set per game in `app.shop.in-print-from`.
- **No price while a product can't be bought:** no "Prijs volgt" and no old price, only the "Uitverkocht" badge.
- **Product lists go by set:** "Nieuwste" and the default order put the newest set first, by release date, with the products of a set together. "Populair" puts the most popular sets of the moment first, from `app.shop.popular-sets`, until orders can decide.
- **Hero carousel:** the hero on the home page is a carousel, the owner's idea. The hero with the binder stays the first slide, and admins add, edit, reorder, switch off and remove the other slides: promotions, upcoming set releases and other news. Phase 6 adds a slide for upcoming and live drops by itself, and phase 8 lets the admin have a slide written by AI from a prompt.
- **Value graph:** added at the owner's request, inspired by the portfolio graph in the Collectr app. Card prices have no history, so one value per customer per day is saved. It only starts when a customer starts, and can't be filled in afterwards.
- **Viewer on public endpoints:** a nullable `@CurrentUser` parameter gives the logged-in user when there is one. Card lists then include what the viewer owns.
- **Payments:** Stripe Checkout behind a `PaymentGateway` interface. An order is made when checkout starts and only becomes paid through the verified webhook, after the shop asks Stripe for the session itself. Stock is reserved for 15 minutes. Stripe's page lasts at least 30, so the shop closes it at Stripe before it releases the stock. Customers see "iDEAL | Wero", as Stripe asks from 2026.
- **Popular follows sales:** "Populair" and the bestsellers put what sold here in the last 90 days first, then the popular sets, then the newest sets.
- **Before going live:** the owner does a legal check and has asked to be reminded. See phase 9.
- **Going live is its own phase:** added by the owner on 2026-11-10. Phase 10 opens the shop under its own domain with live credentials, after the legal check.
- **Online preview first:** added by the owner on 2026-11-10, so a friend can help with missing images. Phase 7 puts the shop online as a closed preview on the owner's own server, with automatic deploys on every push to `main`. The phases after it moved one number up: the phase docs up to `phase-06.md` still use the old numbers, where phase 7 means Extras (now 8), phase 8 Polish and hardening (now 9) and phase 9 Go live (now 10).
- **Riftbound is the fifth game:** added by the owner on 2026-11-12, in phase 8, with catalog, collection and shop. Its card data, pictures and prices come from TCGplayer through tcgcsv.com. Where earlier phases say "all four games", Riftbound counts too.
- **Mystery boxes:** added by the owner on 2026-11-12, in phase 8. The owner packs batches by hand; the shop shows the chances and draws a random remaining box at payment. The Dutch gambling law is part of the legal check.
- **Left out of phase 8:** bundles, case deals and quantity discounts, and the buylist ("kaarten verkopen"), by the owner's choice on 2026-11-12.
- **TCGplayer as a helper source:** besides Riftbound, the picture search (cards and sealed products without a picture) and the release calendar suggestions use TCGplayer's catalog through tcgcsv.com. See phase 8.
- **Never show how many are left:** the owner's rule from 2026-11-13. Customers never see how many of a product are left or how many are in a drop: not on a page, not in an API reply, not in an error message. "Laatste stuks" and "Bijna uitverkocht" are fine, numbers aren't. The server still refuses more than the stock. Mystery boxes show their chances only. Admin pages may show stock.
- **Drop prices only once it's your turn:** the price of a product in a drop shows when the customer is through the queue and can buy, not before, and not on the product page.
- **Customer service:** added by the owner on 2026-11-13, in phase 8. The contact page leads through the FAQ and an AI chatbot to a ticket. See phase 8.
- **The server and its deploys:** since phase 7 the shop runs on the owner's VPS. GitHub Actions deploys every push to `main` with `deploy/server/deploy.sh`, through an SSH key that may only run that script. The server's settings and secrets are in `/opt/poketopia/.env`, never in the repo. Migrations must keep the previous version working (add first, remove in a later release), because a rollback can't undo them. Details in `docs/phases/phase-07.md`.

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
- Home binder: the data for the 9-pocket binder in the home hero, with pages. Sources: popular, own selection, most expensive cards owned, Pokédex, most expensive cards in general. Save the choice on the user, and always show that choice, even while it's empty. Define "popular" (for example, the cards in the most collections). `Card.sortPriceCents` already gives the most expensive cards. Add an index when a query needs it.
- Custom images: the admin can upload a picture for a card or set, which goes before the image of the source. A sync never touches it, and the admin can go back to the original.

Frontend:
- Home binder: the binder in the hero shows real cards and opens like a real binder, with two pages side by side where there's room. Logged-in customers see their most expensive cards by default, logged-out visitors see the most expensive Pokémon cards. Customers change what it shows in their settings. Nothing goes on top of the card images.
- `/collectie`: overview per game with progress bars, total value, most valuable cards.
- Value graph on `/collectie`, like a stock chart: the change over the chosen period, a line with a tooltip, and the periods 7D, 1M, 3M, 6M, 1J and Alles.
- On set pages, logged-in users can see which cards they own and which are missing. A + button next to the price adds a card with one click, and then shows the count with a - in the same element. Greying out images isn't allowed for Magic under Scryfall's terms, so owned cards get a blue frame around the image and "In bezit" below it.
- Toggle per set between "normale set" and "master set" progress.
- `/collectie/pokedex`: a grid of all Pokémon with Pokédex number and name, filled slots show the image of one of the owned cards, filter per generation, and a progress bar per generation. Clicking a slot shows all cards of that Pokémon and which ones you own.
- Missing-cards list per set, with a link to the matching singles in the shop (once products exist).
- Logged-out visitors see a short explanation and a register button.

Admin:
- `/admin/afbeeldingen`: list all cards or sets, filter on name, game, set and "without a picture", sort by set, name or Pokédex number, upload a picture, or go back to the original.

Done when:
- Adding and removing cards updates progress and value right away.
- The home binder shows the chosen source, pages work, and a logged-out visitor sees the most expensive Pokémon cards.
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
- Every product can be in the shop without stock. An active product with no stock stays in lists and on its own page, with "Uitverkocht", no price, a disabled cart button and "mail me when back in stock". Products in stock come first in the default order, and the in-stock filter hides the rest. Products of a set that is out of print only show while they are in stock.
- Product list import, so the shop shows every sealed product from the start, as "Uitverkocht" until there is stock: name, type, set, language and release date. An import never changes stock or a product the admin edited, and leaves out what isn't sold in Europe, such as Pokémon Center exclusives. Read the terms first. Sources checked on 2026-11-08:
  - Cardmarket's public product list (`downloads.s3.cardmarket.com/productCatalog/productList/products_nonsingles_6.json` for Pokémon): 5,109 products with a category (Booster, Display, Elite Trainer Boxes, Box Set, Tins, Blisters, Theme Decks and more) and an expansion id, no images.
  - TCGplayer through tcgcsv.com: products per set with images, for example 51 sealed products for Prismatic Evolutions.
  - Neither says anything about commercial use, so this is part of the legal check. Without a usable source, an admin tool creates the usual product types of a set in one go instead.
- Image upload for products. `MediaStorage` from phase 3 already stores files on disk in a configurable folder, serves them under `/api/v1/media/...`, and sits behind an interface so cloud storage can be added later.
- Public endpoints: list with filters (game, type, set, language, price range, in stock, pre-order, on sale), sorting (new, price, popularity), search, product detail, "new arrivals", "bestsellers" (based on order lines later; the most popular sets of the moment until then).
- Product wishlist and a "mail me when back in stock" signup. `/verlanglijst` already exists for cards from phase 3: add products there.

Frontend:
- Home page: a hero carousel, upcoming/live drop block, new arrivals, bestsellers, browse per game, latest pre-orders. The hero with the binder is the first slide. The other slides are managed in admin: title, text, button with link, image, game colour, start and end date, on or off, and their order. Use them for promotions, upcoming set releases and other news. The carousel turns by itself, stops on hover, focus and for visitors who prefer reduced motion, and has buttons to go back, forward and pause.
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
- A product without stock shows "Uitverkocht" in lists and on its page, and can't go in the cart.
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
- A slide in the home hero carousel for every upcoming and live drop, added by itself from the drop's banner, name and start time, with the countdown. It goes away when the drop ends. Admins can switch it off per drop.
- A "LIVE" badge in the navigation while a drop is live.

Admin:
- Create and edit drops: choose products, allocate stock, set limits, prices and times, preview the drop page.
- Live view during a drop: queue length, admitted, sold per product, remaining stock, and buttons to pause the queue, change the admit speed, or end the drop early.
- Drop report afterwards.

Done when:
- A drop scheduled two minutes ahead switches to live by itself, the countdown matches, and people in the waiting room are admitted in batches.
- Limits can't be bypassed by calling the API directly (add tests for this).
- A test with many simulated buyers never sells more than the allocated quantity.

## Phase 7: Online preview and automatic deploys

Goal: the shop runs online as a closed preview, so the owner and a friend can work in the admin (the friend adds missing images), and every push to `main` updates it by itself. Added by the owner on 2026-11-10.

Decisions by the owner:
- **Closed preview:** every page asks for one shared password at the reverse proxy, the site stays out of search engines (an `X-Robots-Tag: noindex` header and a `robots.txt` that disallows everything), and online payment is off (no Stripe key). Opening the shop to customers is phase 10, after the legal check.
- **The owner's own server:** a VPS rented from mc-node in the Netherlands, shared with the owner's Minecraft server. Checked on 2026-11-10: Ubuntu 24.04 LTS, 4 CPUs, 11 GB memory, 33 GB of 96 GB disk free. Docker 29.1.3 from Ubuntu's own package is there without the Compose plugin. Nothing listens on 80 or 443, no firewall is on, and SSH accepts root and passwords. The address and login are in the owner's hands and in GitHub secrets, never in the repo.
- **Claude works on the server over SSH from the owner's Mac** (key login works). Read-only checks are fine, but every command that changes something on the server is shown to the owner and only runs after their approval.
- **Leave alone:** Minecraft on port 25565 (it must stay reachable through any firewall, and needs its memory: the shop gets about 3.5 GB of the 11), the owner's monitor container `mck-wachtlijst-monitor` that mails them, and the `gitlab-runner` service from an old school project, which the owner keeps just in case.
- **Firewall:** only SSH, HTTP, HTTPS and Minecraft's 25565 in. **SSH with keys only:** the owner never logs in with a password and no longer knows it. Before switching passwords off, check once more that key login works, and tell the owner that mc-node's web console is the way in if the key is ever lost.
- **A temporary address** based on the server's IP address, such as `<ip-with-dashes>.sslip.io`, with a real Let's Encrypt certificate, until the legal check has cleared the name Poketopia. A domain can be added later without moving anything.
- **The friend gets full admin:** a normal account promoted to admin. Add to the legal check that the privacy policy says who can see customer data.

Server, built for production because phase 10 opens this same server:
- A production Docker Compose file with Postgres, the backend on the `prod` profile and Caddy as the reverse proxy, which serves the built frontend and passes `/api` to the backend. Only Caddy can be reached from outside. Everything restarts after a crash or a reboot. The database and `MEDIA_DIRECTORY` live on volumes that survive a new version. `FORWARD_HEADERS_STRATEGY=native`, and a heap of at least 2 GB for the Magic sync.
- Mail goes to a Mailpit container that is only reachable through an SSH tunnel, until phase 10 sets up real mail.
- A `.env` on the server that only its owner can read, with every variable from `.env.example`. Secrets never go into the repository, the docs or a commit.
- Backups from the first day, because what the friend adds only exists on this server: a daily `pg_dump` and a copy of the media folder, kept for 30 days. The owner chose to keep them on the same VPS, outside the Docker volumes, and no copy on their Mac (not enough space there). They know these backups are lost with the server; phase 10 can still add a copy elsewhere.

From phase 9, because the shop is reachable from the internet now:
- A way to create the first admin without demo data, and to promote the friend's account.
- Swagger and the H2 console off in `prod`, and security headers at the proxy.
- Tests that every admin endpoint refuses customers and visitors.

Automatic deploys with GitHub Actions (the repository is on GitHub):
- On every push to `main`: run `./gradlew test` and `npm run build`. Only when both pass, build the backend and frontend images, push them to GitHub's container registry tagged with the commit, and deploy them.
- The deploy starts the new version (Flyway runs the migrations), waits until the backend answers a health check, and goes back to the previous version when it doesn't. Going back can't undo a migration that already ran: say so in the docs.
- The SSH key and the server's address are GitHub Actions secrets. The server's `.env` stays on the server.
- Pushing stays with the owner: Claude never pushes.

Filling the preview:
- The owner chose to fetch the card data on the server rather than copy the local database: run the card sync for all four games and the product list import there (Pokémon takes about an hour and a half). Products, prices and stock are then set again in the admin.

Done when:
- The preview loads over HTTPS at the temporary address, asks for the password first, and from outside the server only answers on SSH, HTTP and HTTPS.
- The owner and the friend log in as admin and upload a picture at `/admin/afbeeldingen`, and it is still there after a new deploy.
- A push to `main` with a passing build is online within minutes, a push with a failing test deploys nothing, and a version that doesn't start is rolled back by itself.
- A backup is restored on a test database and the shop starts from it.
- Minecraft's port 25565 and the owner's monitor container still work afterwards.
- `docs/phases/phase-07.md` describes the server, the address, where every secret lives (never the secret itself), how a deploy works, how to roll back and how to restore a backup.

## Phase 8: Extras

Goal: the features that make the big Dutch TCG shops feel complete.

On 2026-11-12 the owner kept discount codes, the pre-order page, the release calendar, hero slides written by AI, news, the newsletter, reviews, info pages and the cookie notice, and left out bundles, case deals and quantity discounts, and the buylist. They added Riftbound as a fifth game and mystery boxes, and on 2026-11-13 the pictures from TCGplayer, release calendar suggestions and customer service.

- **Riftbound** (added): the League of Legends TCG as a full fifth game, with catalog, collection and shop. Card data, pictures and prices from TCGplayer through tcgcsv.com.
- **Mystery boxes** (added): the owner packs a batch by hand and enters it in admin. The product page shows the chance of every kind of box, from the boxes that are left, never how many. At payment the server draws a random remaining box from the oldest batch, so the chances stay true, and the packing list says which box to send.

- **Discount codes:** percentage or fixed amount, minimum order amount, valid dates, max uses, once per customer, optionally only for certain games or product types, never on drop prices unless enabled.
- **Bundles** (left out by the owner): "stel je eigen bundel samen", for example a sealed product plus a matching acrylic display case with a discount on the case. Admin defines which products can be combined and the discount.
- **Case deals and quantity discounts** (left out by the owner): a tiered price per product (for example 6+ boosters cheaper per piece).
- **Pre-order page:** all pre-orders with release dates on a timeline.
- **Release calendar:** upcoming sets per game, managed in admin, with links to the pre-orders. An upcoming release can get its own slide in the home hero carousel with one click.
- **Hero slides written by AI:** on the admin page for hero slides, the admin writes a prompt (for example "pre-order Destined Rivals, release 30 mei") and the backend asks OpenAI for a title, text and button label, and optionally an image. The owner has an OpenAI API key: read it from `OPENAI_API_KEY` on the backend only, never in a `VITE_` variable. Read OpenAI's current API docs, terms and pricing first, pick a current model, and set a monthly cost limit with a clear message when it's reached. The result is a draft that the admin edits and approves: nothing goes live without that. Store generated images through `MediaStorage`. Without a key the button is hidden and everything else works. Generated images must not show Pokémon or other characters, logos or card art, because those belong to the games' owners: add that to the prompt and to the legal check.
- **News:** simple articles (title, image, text, date, game tag) managed in admin, shown on `/nieuws` and on the home page.
- **Newsletter signup** stored in the database with double opt-in by email, and an admin export.
- **Kaarten verkopen (buylist request)** (left out by the owner): a form where customers list cards they want to sell (they can pick cards from the catalog or from their own collection), with condition and photos. The admin sees requests, gives an offer, and changes the status. No automatic payouts.
- **Reviews:** customers who bought a product can leave a star rating and short review; admin approves them before they appear.
- **Info pages** with editable content in admin: verzending en levertijd, retourneren (14 dagen bedenktijd, with the note that opened sealed products can't be returned), algemene voorwaarden, privacybeleid, FAQ, contact, over ons. Mark the legal pages clearly as drafts that must be checked before going live.
- **Pictures from TCGplayer** (added 2026-11-13): an admin page searches TCGplayer (through tcgcsv.com) for pictures of cards and sealed products that have none. Sure matches get the picture right away, doubtful ones wait for the admin to pick one or none. A found card picture never replaces the source's or an upload, and a sync leaves it alone.
- **Release calendar suggestions** (added 2026-11-13): no game has a release API, so the calendar's admin page suggests upcoming sets from the card catalog and TCGplayer. The admin adds one (the form opens filled in) or dismisses it.
- **Customer service** (added 2026-11-13): before a ticket, the contact page offers the FAQ and an AI chatbot. The chatbot answers from the published info pages and shipping rates only, on a small, cheap model (`gpt-6-luna`, the owner didn't want an expensive one), with its own monthly limit. It never mentions stock or prices and points to a ticket for anything about an order. A chat button on every shop page opens it too. A ticket gets a number and a secret link; the shop gets it by mail, the customer gets a confirmation that we'll look at it as soon as possible. Logged-in customers see their tickets in their account. The shop answers on an admin page, and the customer gets every answer by mail. The shared shop mailbox (`SHOP_EMAIL`) waits for the domain of phase 10.
- **Cookie notice** for functional cookies only. Don't add tracking.

Done when:
- Each feature works from both the customer side and the admin side, and discount codes calculate correctly in combination with shipping and drops (unit tests for every combination).

## Phase 9: Polish and hardening

Goal: ready to show, ready to deploy. This phase runs in three separate sessions:
9a (audit, Fable 5.2), 9b (fixes, Opus), 9c (final review, Fable 5.2).

### Phase 9a: Audit (read-only)

Do not change any code in this sub-phase. Only read, run the app and tests, and write
`docs/phases/phase-09-audit.md`.

Go through the whole repo (all games: Pokémon, Lorcana, MTG, One Piece and Riftbound) and check:

- **Layout and accessibility:** every page at 360px, 768px and 1280px; keyboard navigation,
  focus styles, alt texts, colour contrast.
- **States:** pages or components missing loading, empty or error states.
- **SEO:** page titles and meta descriptions, Open Graph images for products and drops,
  slugs, `sitemap.xml`, `robots.txt`, public collection pages kept out of the sitemap and `noindex`.
- **Performance:** lists without pagination, filters without database indexes, images not
  lazy-loaded, missing route-level code splitting, N+1 queries.
- **Security:** authorization on every admin and account endpoint, input validation, file
  upload checks (type and size), secrets in the repo or git history, security headers,
  race conditions in drops and stock, Stripe webhook verification.
- **Production:** does the `prod` profile start against the Docker Postgres, do all Flyway
  migrations run, does Hibernate validation pass. SMTP username/password settings, `npm audit`.
- **Anything else** you think is a risk before going live, even if it's not on this list.

Write each finding as: what is wrong, where (file and line), why it matters, and a suggested fix.
Group them as **Critical** (must fix before going live), **Should fix** and **Nice to have**.
End with a short checklist the next session can work through in order.

### Phase 9b: Fixes

Work through `docs/phases/phase-09-audit.md` from top to bottom. Tick off each item in the file
when it's done. If you disagree with a finding, write why next to it instead of skipping it silently.

Also in this sub-phase:
- Add tests for authorization on every admin and account endpoint.
- Update the root `README.md`: what the app does, how to run it, environment variables, how to
  sync card data (all five games), how to test payments with the Stripe CLI, and the phase
  overview with links to `docs/phases/`.

Phase 7 already brings the first admin, Swagger closed in `prod`, `FORWARD_HEADERS_STRATEGY=native`
and the 2 GB heap. Don't redo those.

Done when:
- Every Critical and Should fix item is fixed or has a written reason why not.
- All tests pass, the production build works, and the `prod` profile starts cleanly on an empty database.

### Phase 9c: Final review (read-only)

Don't change code. Review the work from 9b against `docs/phases/phase-09-audit.md`: is every item
really fixed, did the fixes introduce new problems, and is anything still missing? Add the
result as a "Final review" section at the bottom of the audit file.

Then give the **go-live reminder.** Before anything goes live, remind the owner to do the legal check.
They asked for this reminder. [paste your full legal paragraph here unchanged, plus:]
Also Riot Games' rules for fan and commercial use of Riftbound, and the source of the Riftbound
card data and images. And from phase 8: TCGplayer's data and pictures, used for Riftbound and by the
picture search for cards and sealed products of every game (and tcgcsv.com's "careful if you seek to
profit"); mystery boxes and the Dutch gambling law (Kansspelwet); the newsletter, reviews and tickets in
the privacy policy, and how long tickets are kept; OpenAI's terms for the generated hero slides and for
the chatbot (customer messages go to OpenAI: a processor agreement, and saying that it is AI); and the
draft legal pages, in particular the returns clause on opened sealed products.

## Phase 10: Go live

Goal: the shop runs on its own server under its own domain, with live credentials, and real customers can order and pay.

The server, the proxy, the automatic deploys and the backups from phase 7 are already there. This phase opens that preview: it adds the domain, real mail and live payments, and takes away the password and the `noindex`. Reuse what phase 7 built and only add what the lists below ask for beyond it.

Most steps here need the owner: accounts, payments, DNS and passwords. Prepare everything that can be prepared, tell the owner exactly what to fill in where, and check the result after each step. Live keys and passwords only go into the environment file on the server, never into the repository, the docs or a commit. Don't start before the owner confirms that the legal check from phase 9 is done.

Ask the owner first for:
- The domain name and where it is registered.
- The hosting. Recommend a VPS in the EU, for the privacy policy, with a fixed public IPv4 address, at least 4 GB of memory (the Magic sync needs a 2 GB heap), and room for the database, media and logs.
- The shop's email address and a mail provider that can send from the shop's domain.
- A Stripe account activated for live payments, with the business details, KVK number and bank account approved.

Server:
- A current Ubuntu LTS. SSH with a key only, password and root login off. A firewall that only lets in SSH, HTTP and HTTPS. Automatic security updates.
- A production Docker Compose file with Postgres, the backend on the `prod` profile and a reverse proxy. Only the proxy can be reached from outside. Everything restarts after a crash or a reboot.
- The reverse proxy (Caddy, or nginx with Let's Encrypt) serves the built frontend and passes `/api` to the backend, with certificates that renew by themselves, a redirect from HTTP to HTTPS and between `www` and the main domain (the owner chooses which one stays), and HSTS. Set `FORWARD_HEADERS_STRATEGY=native`, so rate limits see the customer's IP address.
- The database and `MEDIA_DIRECTORY` on volumes that survive a new version.
- A deploy script: build, upload, start the new version (Flyway runs the migrations), and go back to the previous version when the new one doesn't start.

Domain and DNS:
- An A record for the domain and for `www` pointing at the server's IP address, and an AAAA record when the server has IPv6. Lower the TTL a day before the switch.
- SPF, DKIM and DMARC records from the mail provider, so order mails don't end up in spam.

Configuration:
- A `.env` on the server that only its owner can read, with every variable from `.env.example` filled in for production: `APP_BASE_URL=https://<domain>` (used in mails and Stripe's return pages), the database with a long generated password, `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` and the SMTP login from phase 9, `MEDIA_DIRECTORY`, the cron times, and `FORWARD_HEADERS_STRATEGY`. Keep a checklist of every variable and where its value comes from, without the values.
- Stripe live: `STRIPE_SECRET_KEY=sk_live_...`, a webhook endpoint in the live dashboard at `https://<domain>/api/v1/payments/stripe/webhook` with the four events from the README, and its signing secret as `STRIPE_WEBHOOK_SECRET`. iDEAL | Wero, Bancontact and card switched on in the dashboard, and the shop name as the statement descriptor.
- `OPENAI_API_KEY` from phase 8 with a monthly limit in OpenAI's dashboard, if the owner wants AI-written slides.
- The owner's admin account, created the way phase 7 added. No demo accounts or demo data.

Filling the shop:
- Run the card sync for all five games and the product list import on the server, then set prices, stock and shipping rates in admin. Copying the local database instead saves the long Pokémon sync, but Hibernate made that schema, not Flyway: only do it after checking it against the migrations and removing test orders and test accounts.

Backups and monitoring:
- A daily `pg_dump` and a copy of the media folder, stored somewhere other than the server and kept for at least 30 days.
- An uptime check on the home page and on an API endpoint that mails the owner when the shop is down. Logs rotate, and the owner gets a warning when the disk is 80% full.
- If the owner wants, submit the sitemap from phase 9 to Google Search Console.

Done when:
- `https://<domain>` loads with a valid certificate, `http://` and `www` redirect to it, and from outside the server only answers on SSH, HTTP and HTTPS (a port scan doesn't reach Postgres or the backend).
- A real order of a small amount is paid with iDEAL in live mode, the webhook marks it paid, the confirmation mail arrives in a normal Gmail or Outlook inbox and not in spam, and a refund from the admin comes back.
- Swagger, the H2 console and demo data are off, and no test key is left anywhere.
- A backup is restored on a test database and the shop starts from it.
- A new version goes out with the deploy script, and a version that doesn't start is rolled back by it.
- `docs/phases/phase-10.md` describes the server, the domain and DNS records, where every secret lives (never the secret itself), and how to deploy, roll back and restore a backup.

## Phase 11: Correct prices, Premium and the card scanner

Goal: card prices customers can trust, card lists that keep loading while you scroll, and a paid Premium tier with a card scanner, the full value history and a pre-grade check.

Added by the owner on 2026-11-19. Do the parts in the order below: the first two fix data that every visitor sees today.

Decided by the owner on 2026-11-19:
- **Premium** is a subscription of €6.99 per month or €59.99 per year, including btw.
- **Free scans:** 100 per account in total, never reset.
- **Infinite scroll** goes on the card lists and on the shop's product lists.
- **All five games** get the scanner and the pre-grade check from the start.

### 1. The Magic sync stops on a reused set code

Done on 2026-11-19, ahead of the phase, at the owner's request. Found that day: since 2026-11-18 every Magic sync fails with `duplicate key value violates unique constraint "uk_card_sets_game_code"`, key `(MTG, punk)`. Scryfall renamed the set "Black Lotus Unknown Planechase" from `punk` to `ounk` and gave `punk` to the new set "Prismatic Pride" (released 2026-11-13). `CatalogImportService.importSet` finds sets by external id, so it inserts the new set while the old one still holds the code. Magic prices have stood still at 2026-11-17 since then.

- When another set of the same game holds the code, move that set to a temporary code first. Its own import gives it its new code later in the same run. This works for every game, not only Magic.
- A test with exactly this case: two sets swap codes in one run.
- Run the Magic sync on the server afterwards and check that it completes. An old link to `/kaarten/mtg/punk` now opens the new set, which is fine.

### 2. Correct Pokémon prices

What's wrong (checked on 2026-11-19): TCGdex links several prints of a card in one set to the same Cardmarket product. In Delta Reign (`me06`), Mega Rayquaza ex 084 (Double Rare), 138 (Special Illustration Rare) and 141 (Mega Hyper Rare) all carry Cardmarket product 918912, so all three show €3.15. On the server, 1,161 of the 21,432 Pokémon cards (in 64 sets) share a price with another card of the same name in their set, and 446 of those have a different rarity than the card they share it with.

What the sources have:
- **Cardmarket's public daily files** on `downloads.s3.cardmarket.com` (the host the product import already uses): `productCatalog/productList/products_singles_6.json` and `productCatalog/priceGuide/price_guide_6.json`. They do have a product per print: 918912 (trend €2.93), 918982 (€285.45) and 918985 (€123.77), all named "Mega Rayquaza ex [Roar of the Ruler | Storm Emeralda]" in expansion 6704. But the files have no card number, so a product alone doesn't say which print it is.
- **TCGdex's `pricing.tcgplayer`** is per print and right: 084 $4.91, 138 $277.29, 141 $219.86 (market price), each with its own TCGplayer product.
- Cardmarket's API is closed to new applications. Paid price APIs exist; only look at them if the free sources can't be made right, and ask the owner before anything costs money.

Build:
- Read Cardmarket's price guide and singles list for Pokémon once a day during the sync.
- Our own link from each card to a Cardmarket product, stored on the card. Use TCGdex's `idProduct` when no other card in the set has it. When several cards share one, link the products with that name in that Cardmarket expansion to the cards with that name in order (lowest product id to lowest card number). Check every link against the card's TCGplayer price from TCGdex. When the two disagree too much, the link isn't trusted. Pick the threshold from real data and write it down.
- A card without a trusted Cardmarket link gets its TCGplayer price from TCGdex, converted like Lorcana and One Piece, with "bron: TCGplayer". It never gets the shared wrong price.
- An admin page that lists the doubtful links, where the admin can pick the right Cardmarket product by hand. A sync never overwrites a link set by hand.
- Unit tests with the Rayquaza case, and an integration test for a set with shared products.
- Check Magic, Lorcana, One Piece and Riftbound for the same problem and report what you find. Fix it only when it's the same cause.

### 3. Start saving card prices every day

Phase 12 shows a price graph per card, risers and fallers, and price alerts, and those need history. History can't be filled in afterwards, so saving starts in this phase, right after part 2, with no screens yet (the owner agreed on 2026-11-19).

- After each sync, save a card's price for that day: card, variant, source, currency, trend and average, date. Only when it differs from the last saved price, so a price that stays the same costs no row.
- Estimate the growth first from the real catalog (Magic alone has about 100,000 prints), and write the estimate in `docs/phases/phase-11.md`. Add an index for "the prices of one card over time".
- Saving starts after part 2, so the history begins with correct prices.

### 4. Lists that keep loading

- Card lists (set pages, search, the collection, the Pokédex cards of a Pokémon) and the shop's product lists load the next 25 items when the customer nears the end, instead of page buttons. Build it once as a composable with `IntersectionObserver`, and keep the filters and sorting of `CardFilters.vue` and the shop's filters as they are.
- The URL keeps how far the customer got, so a shared link and the back button return to the same place.
- The footer stays reachable, because the KVK number and the legal links are there: after a few automatic loads, show a "Meer laden" button.
- Search engines don't scroll: keep plain links to the next pages (`PageLinks.vue`) in the page for them.
- Ads on card pages (`AdSlot.vue`) don't come back with every load.

### 5. Premium

- Premium on the user: from when and until when (or for good), and how it was bought. Admins always have Premium without buying it: decide it from the role, don't store it.
- Buying goes through Stripe behind `PaymentGateway`, like orders: Stripe Billing subscriptions of €6.99 per month or €59.99 per year. Only the verified webhook switches Premium on, renews it and ends it (a failed renewal, a cancellation, a refund). My account shows the status, the next payment date, and a link to Stripe's customer portal to cancel or switch between monthly and yearly.
- Premium is a digital service: at checkout the customer agrees that it starts right away and that the 14-day withdrawal right ends then. Add Premium to the terms and the privacy policy, and to the legal check. Also for the legal check: btw on digital services to consumers in other EU countries can follow their country's rate (OSS, above €10,000 a year in sales across borders).
- The server checks Premium for every Premium feature. Hiding a button in the frontend is not enough.

### 6. Value graph periods

- `ValueChart.vue` gets the periods 1D, 1W, 1M, 3M, 6M, 1J and Max, instead of 7D, 1M, 3M, 6M, 1J and Alles. Max is Premium. Without Premium, the Max button shows a lock and a short line about Premium, and the API gives at most one year of history.
- Switching periods animates (the owner's wish): the line and the area morph from the old period to the new one, and the value and the change count along. Resample both lines to the same number of points, then interpolate. Short (about 300 ms) with an ease-out, a new click during the animation starts from where the line is, and with `prefers-reduced-motion` it switches without animation. Use the `animate` skill if it's available.
- The history has one value per day, so 1D compares yesterday's value with today's live value. Card prices change once a day, so more points per day wouldn't add anything.

### 7. Card scanner

- `/scanner`: the customer takes or uploads a photo of a card of any of the five games and gets the matching card, or a short list to choose from, with its price and the button to add it to the collection.
- Only for logged-in customers, so scans can be counted: 100 free scans per account in total, which never reset, and unlimited with Premium. When Premium ends, the customer has whatever free scans were left. A scan counts when it gives a result, not when it fails on our side. A rate limit per user and per IP address.
- How the scanner recognises a card: first test with about 50 real photos from different games, light and sleeves, and choose by hit rate and cost:
  - a cheap vision model reads the name, set code and number from the photo, and the backend looks the card up in our own database. Every scan costs a little, and it works for every game.
  - or matching the photo against the card images (a perceptual hash or image embeddings). No cost per scan, but every card image has to be processed once, so check each source's image terms first.
  Report the test results to the owner before building the rest.
- The photo goes to the backend only and isn't kept after the scan. When a model provider gets it: put that in the privacy policy, and give the scanner its own monthly cost limit, like the chatbot. Pick the cheapest current model that does the job (the owner wants cheap AI) and check that it isn't close to being shut down.

### 8. Pre-grade check

How hard it is (estimated on 2026-11-19): centering can be measured well from a sharp, straight photo of the front and the back, from the widths of the borders, and centering is where graders are strictest. Corners, edges and surface (whitening, dents, scratches) are hard to judge reliably from a phone photo. A vision model can point out visible damage, but it can't grade like PSA or BGS. So the check gives an indication, never a grade.

- Premium only, for cards of all five games. The customer uploads the front and the back, lying flat in good light, with tips on the page. Reuse the scanner's photo upload. Border widths differ per game and per card (full-art cards, borderless Magic cards), so test centering on each game.
- The result: the centering per side as ratios (for example 55/45) and what that allows on the common grading scales, and visible problems per corner and edge, all as "indicatie", with a clear line that it isn't an official grade and that only a grading company decides.
- Never use a grading company's name or logo as if the result comes from them. Add this to the legal check.
- Before it goes live, test it on cards with a known grade (the owner's graded cards) and report how close it gets.

Done when:
- The Magic sync completes on the server again, and the test with the swapped set codes passes.
- The three Rayquaza cards in Delta Reign each show their own price, and `docs/phases/phase-11.md` reports how many cards shared a price before and after.
- Card prices are saved after every sync, only when they changed, and the growth estimate is in `docs/phases/phase-11.md`.
- Card lists and product lists keep loading while scrolling, the back button returns to the same place, and the footer can be reached.
- In Stripe's test mode a customer buys Premium monthly and yearly and gets the Max period, unlimited scans and the pre-grade check, and a cancellation ends it at the end of the paid period. An admin has them without buying, and the API refuses them to a customer without Premium.
- Switching periods in the value graph animates smoothly, and doesn't with reduced motion.
- Scanning a few real photos of each of the five games finds the right card, and the 101st scan of an account without Premium asks for Premium.
- The pre-grade test results against graded cards are in `docs/phases/phase-11.md`.

## Phase 12: More Premium

Goal: more reasons to take Premium, on the website, before any work on the app.

Chosen by the owner on 2026-11-19. Not included: early access to drops for Premium members, because the owner finds that unfair to everyone else. Every feature below is Premium, and the server checks that, like in phase 11.

- **No ads:** Premium members never see an `AdSlot.vue`, and the AdSense script isn't loaded for them at all.
- **Price history per card:** a graph on the card page from the prices saved since phase 11, with the periods of the value graph, and the same animation. Without Premium the card page shows a locked graph with a short line about Premium. Also "biggest risers and fallers" among your own cards, for a day, a week and a month.
- **Price alerts:** on a card, the customer sets "mail me when it drops below €…" (for wishlist cards) or "when it rises or falls more than …%" (for owned cards). The check runs once a day after the sync and sends at most one mail per day with all alerts that went off, with a link to switch them off. A limit on the number of alerts per customer. Push messages for alerts come with the app in phase 13.
- **Export the collection** as CSV and as PDF, for example for insurance: card, set, number, variant, condition, quantity, price and source, total value and the date.
- **Extra binders:** besides the collection, Premium members make binders of their own, for example a trade binder, each with its own value. The collection stays what counts for progress, the Pokédex, the value graph and the home binder. When Premium ends, the extra binders stay visible but can't be changed, and nothing is deleted.
- **Themes:** a few colour themes for the website, built on the tokens in `main.css`, chosen in My account. Dark mode is free for everyone (the owner's choice, 2026-11-20): light, dark or the system setting; the extra colour themes are Premium. The app in phase 13 uses the same themes.

Done when:
- A test customer with Premium sees no ads, the price graph of a card, risers and fallers, can set alerts and gets one mail with them, exports CSV and PDF, makes an extra binder and picks a theme.
- Without Premium the API refuses each of these, and trying one opens the Premium popup.
- Unit tests for the alert rules and the export.

## Phase 13: The Android app

Goal: Poketopia as an Android app, with widgets on the home screen. The owner wants this as one of the last steps, after the Premium features of phases 11 and 12 work, and Android first (2026-11-19). iPhone comes later, not in this phase: it needs the Apple Developer Program (about €99 a year) and Xcode on a Mac, or a macOS build in GitHub Actions.

Ask the owner first for:
- **A Google Play developer account** (a one-time $25), in the business name once the KVK registration exists.
- **Selling Premium in the app:** Google wants digital purchases inside an app to go through Google Play's payment, and takes 15 to 30% of it. The simplest way: the app doesn't sell Premium, customers buy it on the website and log in in the app. Check Google's current rules before deciding, because they change.

Build, in this order:
1. **Installable web app (PWA):** a manifest, the app icon, a start screen, an offline page and "Zet op beginscherm". Cheap, and it also works on iPhone, until the iPhone app exists.
2. **The app:** wrap the Vue frontend with Capacitor, so the website and the app share one code base. Links to poketopia.nl open in the app (Android App Links), and the login stays. The rules of the website apply in the app too, card images included.
3. **Widgets** (Glance, in Kotlin): the collection value with today's change and a small graph, and the countdown to the next drop. They read a small read-only API with a token per device, a few times a day. Never stock counts, also not in a widget.
4. **Push notifications** through Firebase Cloud Messaging: a drop that starts, an answer on a ticket, and the price alerts from phase 12. Each kind can be switched off in My account. Add Firebase to the privacy policy.
5. **Exclusive app icons (Premium):** a few alternative launcher icons the customer picks in the app. No Pokémon or other characters, logos or card art in them (legal check).
6. **Google Play:** the listing, screenshots, the Data safety form, first the internal testing track, then the review.

Done when:
- The web app can be installed on Android and on iPhone.
- The app runs on a real Android phone from the internal testing track, with the login, App Links and push messages working.
- The widgets show the right value and refresh.
- Only Premium members can pick another app icon.
- `docs/phases/phase-13.md` describes how to build and publish a new app version.

---

## Out of scope

Don't build these unless I ask: livestreams or "rip & ship", a real Cardmarket API integration (it is closed to new applications), automatic shipping labels, other games than the five above (Riftbound came in phase 8), and translations other than Dutch.
