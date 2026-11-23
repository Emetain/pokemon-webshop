# Phase 11: Correct prices, Premium and the card scanner

Built on 2026-11-19 on branch `feature/phase-11-premium` (from `main` at `6545edb`). Not pushed, not merged, and no
`phase-11` tag yet: several "Done when" checks need the owner (see [What's left for the owner](#whats-left-for-the-owner)).
Part 1 (the Magic sync fix) was done earlier that day in `65e1de1`.

## Before you try it

- **Restart your backend.** Your backend on 8080 runs an older build. Stop it and start it again in `backend/` with
  `SPRING_PROFILES_ACTIVE=postgres SPRING_JPA_SHOW_SQL=false ./gradlew bootRun`. Hibernate adds the new columns and
  tables to your local database by itself (the new NOT NULL user columns have defaults for that).
- **Correct Pokémon and Magic prices only appear after a sync of that game** (`/admin/sync`): that run downloads
  Cardmarket's singles list and price guide and links every card. The Pokémon sync takes about an hour, Magic about a
  minute.
- **Stripe:** for Premium in test mode, `stripe listen --forward-to localhost:8080/api/v1/payments/stripe/webhook`
  forwards all events, including the new ones.

## What was built

### 2. Correct Pokémon and Magic prices

TCGdex links several prints of a card to one Cardmarket product. Checking 11,944 cards of the 76 sets with shared
prices showed it's worse than reprints: a shared product is often a different card with the same name (two Squirtles of
Expedition with other attacks), or even another card (Lapras and Lapras VMAX of Sword & Shield, Kabuto and Kabutops of
Legends Awakened).

- **Cardmarket's daily files** (`products_singles_6.json` and `price_guide_6.json`, about 15 MB each) are read entry by
  entry at the start of every Pokémon sync and stored in `cardmarket_singles` (76,000 products). A failed download keeps
  yesterday's rows and never stops the sync.
- **The shop's own link per card** (`cards.cardmarket_product_id` and `cardmarket_link`), decided in `CardmarketLinks`:
  1. A product only one card of the set has stays that card's (`SOURCE`).
  2. Cards that share a product are taken together by name. Their candidates are the products with that name (attacks
     and "Lv.57" stripped) in the shared products' expansion that no other card of the set has. With as many candidates
     as cards, they go in order: lowest product id to lowest card number (`MATCHED`).
  3. When the counts differ, or a link of that order fails the price check, each card gets the free candidate closest
     to its TCGplayer price, as long as the two agree.
  4. A card left without a product is `DOUBTFUL` and shows its TCGplayer price (converted, "bron: TCGplayer"), never
     the shared one. A link set by hand (`MANUAL`) is never changed by a sync.
- **The price check** against TCGdex's TCGplayer price per print (`PriceAgreement`), with thresholds from the data:
  - For a link the shop made: the higher price at most **3 times** the lower one, unless both are under **€2**.
  - For TCGdex's own unique product: only doubted at **10 times**, above **€5**. Even correct links differ more than 3
    times for 6.9% of these cards (mostly vintage cards with few sales, where TCGplayer's market price is thin); at 10
    times it's 1.1%, and those are real errors such as Majestic Dawn Zapdos at €0.02 against $66, or Surging Sparks
    Quaxly at €17.55 against $0.16.
- **Prices of a linked card** come from the price guide row of its product (`trend`/`avg`, and the foil column for the
  reverse holo, with the same variant rules as before); the 1st edition price still comes from TCGdex, which has its
  own product for it.
- **Admin page `/admin/prijskoppelingen`**: the doubtful cards with what customers see now, the product TCGdex gave,
  and the products with that name in the expansion with their prices. "Deze" links a product, a product number can be
  typed in, "Geen van deze" keeps the TCGplayer price, and "Terug naar automatisch" gives the card back to the sync.
  A choice is priced right away.

**Before and after**, on the 11,944 cards of the 76 sets with shared prices (simulated on TCGdex and Cardmarket data of
2026-11-19 with the same rules):

| | Before | After |
|---|---|---|
| Cards sharing a price with a same-name card in their set | 963 | 137 |
| of which at €2 or more | 424 | 2 |
| of which with a different rarity | 352 | 6 |
| Cards with a Cardmarket price | 11,421 | 11,131 |
| Cards with a converted TCGplayer price | 0 | 549 |
| Cards without any price | 523 | 264 |

The 137 left are cheap commons that happen to have the same price (mostly under €0.50). The server had 1,161 cards
sharing a price on 2026-11-19; the real numbers after the first sync on the server still have to be counted:

```sql
WITH p AS (SELECT c.id, c.set_id, c.name, cp.trend_cents FROM cards c JOIN card_prices cp ON cp.card_id = c.id
           AND cp.variant IN ('normal', 'holo') WHERE c.game = 'POKEMON' AND cp.trend_cents IS NOT NULL)
SELECT count(DISTINCT a.id) FROM p a JOIN p b ON a.set_id = b.set_id AND a.name = b.name AND a.id <> b.id
   AND a.trend_cents = b.trend_cents;
```

**Delta Reign (`me06`)**, in the unit test and the simulation: Mega Rayquaza ex 084 → product 918912 (€2.93), 138 →
918982 (€285.45), 141 → 918985 (€123.77), all `MATCHED`.

**Magic** (added on 2026-11-19 at the owner's request). Scryfall has the same problem in a smaller way: it gives the
three Summer Magic Islands (295, 296, 297) Cardmarket product 16935, while Cardmarket has three (16935, 16940, 16945).
The Magic sync now does the same as the Pokémon one: it stores Cardmarket's Magic singles (`products_singles_1.json` and
`price_guide_1.json`, 125,505 products; the price guide calls its second column `foil` where Pokémon's says `holo`),
reads Scryfall's `cardmarket_id` and TCGplayer's `usd`/`usd_foil`/`usd_etched`, and runs `CardmarketLinks`. Two rules
differ from Pokémon, both from today's Scryfall and Cardmarket files:

- **Cards that share a product but each use another of its columns are fine** (`SOURCE`). Cardmarket keeps a card's
  foil print in the same product, while Scryfall lists old foils as cards of their own: Seventh Edition Duress 131
  (nonfoil, €0.64) and 131★ (foil, €128.72) are both product 2893. 351 cards are like this. Treating them like Pokémon
  would have made half of them doubtful.
- **A product only one card has is trusted without the price check.** Scryfall keeps its links by hand, and of the 170
  cards the 10-times check would doubt, the wrong price was as often TCGplayer's as Cardmarket's: Summer Magic Kird Ape
  €384 against $0.37, Library of Leng €0.02 against $400. So those keep their Cardmarket price as before.

The price check still applies to the prints the shop matches itself. Where today's file has no trend for a product
(some Summer Magic and 30th Anniversary cards), the card keeps Scryfall's copy of its price instead of losing it. Etched
foils have no column of their own at Cardmarket and get their TCGplayer price. The admin page got a Pokémon/Magic switch.

**Before and after** on a copy of the local database, with a real Magic sync (56 seconds, within the 2 GB heap):

| | Before | After |
|---|---|---|
| Magic cards with a price | 100,935 | 105,045 |
| with a Cardmarket price | 100,935 | 100,736 |
| with a converted TCGplayer price | 0 | 4,309 |
| Sharing a price with a same-name card in their set | 1,615 | 1,208 |
| of which at €2 or more | 114 | 60 |
| Links: `SOURCE` / `MATCHED` / `DOUBTFUL` / no product | | 102,162 / 233 / 231 / 8,413 |

The 60 left are different products that happen to have the same price (Final Fantasy's Cid 414 and 419 are products
824301 and 824306, both €2.86), or equal TCGplayer prices. Summer Magic now shows Island 295/296/297 at €593.42,
€655.81 and €654.54, and Swamp 298/299/300 at €380.66, €450.00 and €560.10, each its own product. The 4,309 TCGplayer
prices are mostly cards Cardmarket has no product for (8,413 have no `cardmarket_id`, many of them Secret Lair "★"
prints) and doubtful ones; before, they had no price at all.

**The other games** have no shared products: Lorcana, One Piece and Riftbound are priced from TCGplayer, which has a
product per print. In the local database no card of theirs shares a price with a same-name card in its set; the 13
pairs of different cards with an equal price (such as One Piece P-069 and P-079, both $200.00) are coincidences.

### 3. Card prices per day

- `card_price_history` (card, variant, source, date, currency, trend, average), written during each sync after the
  price is decided, so the history begins with the corrected prices. A row only when the price differs from what was
  saved last: `card_prices.recorded_on`/`recorded_trend_cents`/`recorded_average_cents` remember that, so it costs no
  lookups. A second sync on the same day overwrites that day's row. The first sync writes every price once.
- The primary key starts with the card (`card_id, variant, source, price_date`), so it is also the index for "the
  prices of one card over time"; there's no separate id or index. Plain JDBC batches.

**Growth estimate.** The server has 192,939 card prices (2026-11-19). Comparing the local Pokémon prices of 2026-11-15/16
with TCGdex's of 2026-11-19, 66% changed in about 3.5 days, so about 30% change per day. That gives roughly 58,000 rows
a day, about 21 million a year. At about 130 bytes per row including the key, that's at most **2.7 GB a year**; probably
less, because most Magic commons move less than Pokémon cards. The server has 29 GB free (71% used, the disk warning is
at 80%). Look at the real number after a week:
`SELECT price_date, count(*) FROM card_price_history GROUP BY price_date ORDER BY price_date;`. If it's too much,
phase 12 can keep daily rows for a year and weekly ones after that.

### 4. Lists that keep loading

- `utils/infiniteList.js` (`useInfiniteList`) with an `IntersectionObserver` 800 px before the end, and
  `components/catalog/ListEnd.vue` for the end of a list. Used on the set pages, search, the Pokédex cards of a Pokémon,
  the wishlist and every shop product list. 25 items per load. The missing-cards table of a set keeps its pages of 100.
- The URL's `page` says how many pages are loaded (`?page=3` shows 1 to 3) and is replaced, not pushed, on every load, so
  a shared link and the back button open the same depth. The router waits for the list to load again before it scrolls
  back (`whenThereIsRoom` in `router.js`).
- After three automatic loads a "Meer laden" button takes over, so the footer with the KVK number stays reachable;
  clicking it allows three more.
- The page links stay below the list for search engines and keyboard users. The ad slot stays one, below the list.
- Filters and sorting are unchanged; a change starts the list over.
- **Admin lists** (added on 2026-11-19, the owner saw them still paging): products, orders, stock movements (also the
  log on a product page), price links, images (cards and sets), picture search, tickets, reviews and the newsletter use
  `useAdminList`: the same loading, without "Meer laden", because the admin pages have no footer to reach. Products and
  images keep their size choice as the number per load. Lists whose filter isn't in the URL (picture search, stock)
  don't write their depth there (`syncUrl: false`). Checked in the browser on a database copy: products 25 → 125,
  price links 25 → 75 and back at that depth after a reload, image cards and sets, stock 25 → 29, no console errors.

### 5. Premium

- **On the user:** `premium_since`, `premium_until`, `premium_forever`, `premium_source` (`SUBSCRIPTION` or `GRANT`) and
  `stripe_customer_id`; admins have Premium from their role, never stored (`PremiumAccess`).
- **Stripe Billing** behind `SubscriptionGateway`, next to `PaymentGateway` and implemented by the same
  `StripePaymentGateway`: one account, one key, one webhook. The product "Poketopia Premium", its two prices (€6.99 a
  month, €59.99 a year, btw included, lookup keys `poketopia_premium_monthly` and `poketopia_premium_yearly`) and the
  customer portal's settings are found or made in Stripe the first time they're needed. A changed price in
  `app.premium` makes a new price that takes over the lookup key.
- **Buying** (`/premium`): the customer picks monthly or yearly and ticks that Premium starts right away and that the
  14-day withdrawal right ends with that (stored as `consent_at`); Stripe's page repeats it above the pay button. Admins
  and customers who already have Premium are refused.
- **Only verified webhooks change Premium**, after the shop asks Stripe for the subscription itself:
  `checkout.session.completed`, `customer.subscription.created/updated/deleted`, `invoice.paid`,
  `invoice.payment_failed` and `charge.refunded`. Paid up (`active`, `trialing`) gives Premium until the end of the
  period; a cancellation keeps it until then; a failed renewal (`past_due`) ends it now and a later payment brings it
  back; a full refund ends it now and cancels the subscription at Stripe.
- **My account** shows the plan, since when, the next payment or the end date, and opens Stripe's customer portal to
  cancel, switch between monthly and yearly, or change the payment method.
- **The server checks Premium** for the Max period, unlimited scans and the pre-grade check (`PremiumService.require`,
  `premium_required` with 403).
- The default terms and privacy texts got Premium and the scanner. **The pages on the server are your own edited
  copies**, so add those paragraphs there yourself (they're in `InfoPageDefaults.kt`, "11. Premium" and the Premium
  and Kaartscanner lines).

### 6. Value graph periods

`ValueChart.vue` has 1D, 1W, 1M, 3M, 6M, 1J and Max. Max is Premium: without it the button shows a lock and a line about
Premium, and `/api/v1/collection/history` gives at most a year (with `hasOlder` when there's more). 1D compares
yesterday's value with today's live value.

Switching periods morphs the line and the area: both are resampled to 64 points and blended in 300 ms with a strong
ease-out (`cubic-bezier(0.23, 1, 0.32, 1)`, `utils/tween.js`), the change counts along, a new click starts from where the
line is, and with `prefers-reduced-motion` it switches at once.

### 7. Card scanner

- `/scanner` (logged in): "Foto maken" (camera) or "Foto kiezen", optionally a game. The browser shrinks the photo to
  1280 px JPEG; the backend has **gpt-6-luna** read the name, set code, set name and number (Structured Outputs, `detail:
  high`, `store: false`) and looks the card up in the catalog by number and name (`ScanMatching`): one sure match, or up
  to five to choose from, each with its price, + and wishlist. The photo is never stored or logged.
- 100 scans with a result are free per account, for good (`users.free_scans_used`); Premium scans without limit. A scan
  that finds nothing or fails at the model doesn't count. Rate limits of 30 per 10 minutes per IP address and per
  account. Its own monthly limit `OPENAI_SCAN_MONTHLY_LIMIT_USD` (default $5), shared with the pre-grade check.
- `tools/scan-test.py` runs a folder of photos named `game__name__number.jpg` through a backend and reports the hit
  rate.

**Why a vision model and not image matching.** Matching against the card images needs every image of all five games
(about 140,000) downloaded and processed once and again for every new set, and each source's image terms to allow
that; it also struggles with sleeves, glare and reprints with the same art. The vision model works for every game from
day one, reads the number that tells reprints apart, and costs very little. **Model:** gpt-6-luna, the cheapest current
model that takes images ($0.10 / $0.50 per million tokens, no shutdown date on OpenAI's model page on 2026-11-19;
gpt-5-nano is cheaper per token but an older family).

**First test (2026-11-19), 23 photos made from card images**, 4 to 7 per game, at an angle of up to 8°, darker or
lighter, slightly blurred, a third in a glossy sleeve (rendered with Chromium; real phone photos are harder):

| | Result |
|---|---|
| Right card straight away | 22 of 23 (96%) |
| Right card in the list | 23 of 23 (100%) |
| Name and number read right | 22 of 23 (Riftbound "030a" read as "003a") |
| Time per scan | 1.7 to 13 s, mostly 2 to 5 s |
| Cost | 1,710 input and 259 output tokens on average: **$0.0003 per scan**, $0.03 for 100 free scans |

The real test with about 50 of your own photos is still to do (see below).

### 8. Pre-grade check

- `/pre-grade` (Premium; the server checks it before any photo goes anywhere): the front and the back, with tips for
  the photos. One gpt-6-luna call finds the card's outer edge and its inner frame on both sides, and the visible wear
  per corner and edge and on the surface.
- The page draws the edges as lines (blue outside, amber inside) that the customer drags, or moves with the arrow keys,
  onto the exact edges. The centering is measured live as left/right and top/bottom splits, and the highest grade the
  centering alone allows on the common 10-point scales (`utils/centering.js`: 55/45 front and 75/25 back for a 10,
  60/40 and 90/10 for a 9, and so on, from the published guidelines). Full-art and borderless cards get a guessed
  frame that the customer sets.
- Always "indicatie": a line above and below says it isn't an official grade, that it doesn't come from a grading
  company, and that only a grading company decides. No grading company's name or logo is used.
- **First test (2026-11-19)**, three straight photos made from card images with a known card position: the outer edge
  came back within 1 thousandth of the true position for Pokémon, Magic and One Piece. The inner frame gave 50/50 both
  ways for the Pokémon and One Piece cards (perfect scans, so right); for the Magic card it took the text box as the
  bottom of the frame (35/65), which the customer has to drag down. Sent the front twice, the check noticed it ("Dit is
  opnieuw de voorkant"). Real photos are less straight; the test against your graded cards is still to do.

### After the owner's first tests (2026-11-20)

- **Camera on a computer:** a computer's browser ignores `capture` on a file input and opens the file picker, so
  "Foto maken" opens a live camera view there (`CameraCapture.vue`, `getUserMedia`); phones keep their own camera app.
  The view is never mirrored, and the photo is cropped to the card frame plus 12% (a webcam picture is wide; the scanner
  gets more pixels on the card). The Caddyfile now allows `camera=(self)`. The pre-grade check got "Foto maken" and
  "Foto kiezen" for each side.
- **Scan result:** the photo and the card found side by side at the same size, with the card's large picture (it was
  about 2.5rem wide in a six-column grid). A card with more versions shows a row per version, and the line "Volgens de
  scan is dit: …": the model now also reads the finish (`normal`, `holo`, `reverse`, `unknown`), only as a hint.
- **Card versions of new sets:** TCGdex first gives every card of a new set a placeholder, "normal" only with
  `variantId: "generated"` (all of Delta Reign, `me06`). The sync then takes the finishes from TCGdex's TCGplayer prices
  (`normal`, `holofoil`, `reverse-holofoil`), and without those the owner's rule by rarity: common and uncommon normal +
  reverse holo, rare holo + reverse holo, everything above one holo print. Older sets keep TCGdex's own variants.
  When a card loses a finish, collection entries and shop singles of it move to the card's main finish
  (`CardVariantMover`), copies of one customer added up. A **Pokémon sync** applies this.
- **Pre-grade score:** one score with a decimal, "9.7 / 10", in a box right under "Resultaat", green for high and red
  for low, with the four parts (centering, corners, edges, surface). Computed in the page (`utils/pregradeScore.js`), so
  it follows the lines while they're dragged: the average of the parts, at most 0.5 above the weakest one and never
  above what the centering allows on the common scales. Always labelled "Indicatie"; part of the legal check.
- **Premium popup:** every Premium feature opens `PremiumDialog.vue` instead of a page, see "Changes made during the
  build" in `docs/build-prompt.md`.
- **Home page:** the customer's value graph and tiles for the value graph, the scanner and the pre-grade check, right
  below "Nieuw binnen".

### Fixed on 2026-11-23: photos from a phone were refused

The owner couldn't use the scanner or the pre-grade check on the phone. Caddy only allowed multipart bodies above
256 kB on `/api/v1/admin/*` (phase 9b, before this phase), so a scan photo from a phone (1280 px) was often refused
with 413 "Dit bestand is te groot", and the pre-grade check (two photos of 1600 px) nearly always. A webcam photo is
cropped and softer, so it often stayed under the limit on a computer. `/api/v1/scanner` and `/api/v1/pregrade` now
get the same 7 MB as admin uploads; the backend limits each photo to 5 MB. Checked through the real Caddyfile with a
backend stand-in that reads the whole body: before, a 400 kB scan and an 850 kB pre-grade got 413; after, both get
through, and 8 MB, 400 kB of JSON and a 400 kB upload to another path still get 413.

## Main files

- Backend: `CardmarketSinglesProvider`/`Parser`, `CardmarketSingleService`, `CardmarketLinks` (with `PriceAgreement`),
  `CardmarketLinkService`, `CardPriceHistoryService`, `CatalogImportService`, `TcgdexMapper`, `ScryfallMapper`, `PremiumService` (with
  `PremiumAccess`), `SubscriptionGateway`, `StripePaymentGateway`, `PaymentWebhookOrchestrator`, `ScanService`,
  `ScanMatching`, `PregradeService` (with `PregradeReading`), `CollectionHistoryService`, controllers
  `AdminCardmarketLinkController`, `PremiumController`, `ScannerController`, `PregradeController`.
- Migrations: `V15__card_price_links_and_history.sql`, `V16__premium.sql`.
- Frontend: `utils/infiniteList.js`, `components/catalog/ListEnd.vue`, `utils/tween.js`, `ValueChart.vue`,
  `views/PremiumView.vue`, `components/account/PremiumPanel.vue`, `views/ScannerView.vue`, `utils/photo.js`,
  `views/PregradeView.vue`, `components/pregrade/EdgeGuides.vue`, `utils/centering.js`,
  `views/admin/AdminPriceLinksView.vue`.
- `tools/scan-test.py`.

## New environment variables

| Variable | Default | What it does |
|---|---|---|
| `OPENAI_SCAN_MODEL` | `gpt-6-luna` | The model that reads card photos for the scanner and the pre-grade check |
| `OPENAI_SCAN_MONTHLY_LIMIT_USD` | `5` | What the scanner and the pre-grade check may spend per calendar month |

Both are in `.env.example`, `deploy/server/env.example` and both compose files. Premium's prices and the free scans are
in `app.premium` in `application.yml`, not in the environment.

**Stripe webhook:** the endpoint in the dashboard needs six more events: `customer.subscription.created`,
`customer.subscription.updated`, `customer.subscription.deleted`, `invoice.paid`, `invoice.payment_failed` and
`charge.refunded` (in test mode now, and again in live mode).

## Checks

- `./gradlew test`: all 1,113 tests pass, including the new unit tests (`CardmarketLinksTest` with the
  Rayquaza case and six Magic cases, `ScryfallMapperTest` for `cardmarket_id` and the dollar prices, `CardmarketSinglesParserTest`, `PremiumAccessTest`, `ScanMatchingTest`, `PregradeReadingTest`, the
  history limit in `CollectionHistoryMathTest`) and integration tests (`CardmarketLinkIntegrationTest` for a set with
  shared products, the price history and the admin page, and a Magic set with Summer Magic Islands and Duress ★; `PremiumIntegrationTest` for monthly, yearly, cancelling,
  a failed renewal, a refund and admins; `ScannerIntegrationTest` for counting, the 101st scan and the pre-grade check).
- `FlywayMigrationTest` and `ProdProfileIntegrationTest` pass, so V15 and V16 match the entities.
- `npm run build` passes.
- In the browser (Playwright, against the throwaway backend): a Magic set loads 25, 50, 75, 100 cards while scrolling
  with `?page=` following, then shows "Meer laden"; the footer is reachable; the back button from a card returns to
  `?page=4` with 100 cards. The value graph morphs between periods (64 blended points halfway, the real 366 points at
  the end), shows the lock and the Premium line for Max without Premium, and switches at once with reduced motion.
  `/premium`, My account, `/scanner` and `/pre-grade` render without console errors and without sideways scrolling at
  375 px.
- The scanner test above ran against a throwaway backend (port 8095) on a copy of the local database, with the real
  OpenAI key.
- The price-link rules were simulated on the real TCGdex and Cardmarket data of the 76 affected sets (numbers above);
  the full Pokémon sync with the new code hasn't run yet. The Magic sync ran for real on a database copy (part 2).

## Decisions

- The two price thresholds and why, see part 2. Doubtful cards keep TCGdex's product id (not used for prices), so the
  admin page knows which expansion to look in.
- A card with a trusted Cardmarket link but no Cardmarket price gets its TCGplayer price rather than none.
- Price history is written during the sync (per set) instead of after it, and the price date is the sync's day in Dutch
  time.
- `?page=N` means "pages 1 to N", replaced on every load: one history entry per list, and the back button returns to the
  same depth.
- Premium is its own gateway interface next to `PaymentGateway` instead of more methods on it, so the order code and its
  fake stay as they were.
- Stripe chooses the payment methods for Premium (from the dashboard): iDEAL and Bancontact become SEPA Direct Debit for
  the renewals there.
- A partial refund leaves Premium alone; only a full refund of a Premium payment ends it.
- The pre-grade check measures centering in the browser from lines the customer can correct, because a model's boxes are
  estimates; the model only gives the starting lines and the wear.
- Magic shares the linking with Pokémon but with its own two rules (columns apart, no check on a unique product), see
  part 2. The SOURCE price falls back to the source's own copy when today's file has no trend, for both games.

## Open issues

- **231 doubtful Magic cards** on `/admin/prijskoppelingen?game=magic`, most of them cheap; the ones worth looking at
  are Portal's "s" prints (TCGplayer $50 to $150, one Cardmarket product of €1.41 for all prints), the Summer Magic
  Forests (TCGplayer's $0.30 for 305 makes the order check fail) and meld backs such as Hanweir, the Writhing Township.
- **Grading company names on the pre-grade page:** the page names no company; the explanation says "grote
  gradingbedrijven". Part of the legal check.
- Lowering the scanner's latency: gpt-6-luna spends about 260 output tokens per scan; a lower reasoning setting might
  make it faster, untested.
- Phase 9's N4, N6, N8, N9, R6 and R7 are still open, see `docs/HANDOVER.md`.

## What's left for the owner

1. **Restart** your local backend (see the top), and run a **Pokémon sync** and a **Magic sync** in `/admin/sync`. Then
   look at `/admin/prijskoppelingen` (both games), Delta Reign's three Mega Rayquaza ex cards and Summer Magic's
   Islands.
2. **Premium in Stripe's test mode:** with `stripe listen` running, buy Premium monthly with test card 4242 4242 4242
   4242, check My account and the Max period, cancel in the portal (Premium stays until the end of the period), then buy
   yearly with a second account. Stripe's test clocks can move time forward to see a renewal and the end of a cancelled
   period.
3. **Add the six webhook events** to the endpoint in the Stripe dashboard (test mode now, live mode later).
4. **Real scanner test:** photograph about 50 cards of all five games, in different light, with and without sleeves,
   name the files `game__name__number.jpg`, and run
   `python3 tools/scan-test.py --base http://localhost:8080 --email <your admin email> <folder>`. Send me the output.
5. **Pre-grade test:** run your graded cards (front and back, out of the slab if you can, otherwise in it) through
   `/pre-grade`, and note the centering it measures next to the grade and the centering on the label. I'll put the
   results here.
6. **Magic sync on the server** (part 1): after this branch is merged and deployed, run the Magic sync in
   `/admin/sync` on the server and check that it completes.
7. **Legal check additions:** Premium in the terms (start right away, withdrawal right ends, automatic renewal,
   cancelling), btw on digital services to consumers in other EU countries (OSS above €10,000 a year across borders),
   the scanner's photos going to OpenAI in the privacy policy, and no grading company names or logos as if a result
   comes from them. Update your own legal pages on the server with the new paragraphs.
