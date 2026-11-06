# Phase 2: Card catalog for all four games

Every set and card of Pokémon, Magic, Lorcana and One Piece in our own database, browsable on the site.

## What was built

**Backend**

- Entities `CardSet`, `Card`, `CardPrice` and `SyncRun`, with Flyway migration `V2__card_catalog.sql`. `Card` and `CardPrice` use sequences, because Hibernate can't batch inserts with identity columns.
- `CardDataProvider` with one implementation per game, so a source can be swapped later. The mapping lives in pure objects (`TcgdexMapper`, `ScryfallMapper`, `LorcastMapper`, `OptcgMapper`) that have their own tests.
- `CatalogHttpClient` for all outside calls: a client name as `User-Agent`, a pause before every request, up to 4 attempts on 429, 5xx and IO errors, and it honours `Retry-After`.
- `CatalogImportService` imports one set per transaction, so a run that stops keeps what it finished. Only fields that differ are written. A price the source no longer has is removed. Cards are never deleted, because later phases point at them.
- `CardSyncService` runs a sync in the background, one run per game at a time. `CatalogSyncJob` starts all games at night. `SyncRunService` logs progress after every set, and marks runs that were cut off by a restart as failed.
- Public API under `/api/v1/catalog`: sets per game, a set with its filter options, cards of a set with rarity and variant filters, card detail with all prices, and search across games.
- Admin API under `/api/v1/admin/catalog`: start a sync, and see the last run and counts per game.
- Sorting and filtering for card lists: number, name and price in both directions, plus kind of card, energy type or colour, rarity, variant and "with price only". Prices sort through `Card.sortPriceCents`, a copy of the list price in euro cents, so dollar and euro prices sort together.
- `ExchangeRate` with `ExchangeRateService`: every sync fetches the ECB's daily reference rate. `GET /api/v1/catalog/exchange-rates` serves it from our own database.
- `User.currency`, changed through `PATCH /api/v1/account`. Addresses lost their `name` and use the profile name (`V3__profile_currency_and_address_name.sql`).

**Frontend**

- `/kaarten/:game`: sets grouped by series, newest first, with links to jump to a series. Sets without art show their set code.
- `/kaarten/:game/:setCode`: card grid with sorting and filters, trend price or "geen prijs", and pagination. Sorting, filters and page are in the URL, so every view can be shared. On phones the filters sit behind a button that shows how many are active.
- `/kaarten/:game/:setCode/:cardId`: large image, details, and prices with their source and date. This is a page rather than a modal, so it can be shared.
- `/zoeken?q=`: results with the same sorting and filters, plus game. The header search works on desktop and mobile.
- My account: a currency choice, and an address form without a name field.
- `/admin/sync`: last run per game, counts, errors and a start button. It refreshes every 3 seconds while a run is busy.
- A link to the catalog in each game's dropdown, and the required attribution in the footer.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/Card.kt`, `CardSet.kt`, `CardPrice.kt`, `CardVariants.kt`, `SyncRun.kt`, `Game.kt` |
| Sources | `service/provider/` (`CardDataProvider.kt`, `CatalogHttpClient.kt`, `PriceCents.kt`, `EcbRates.kt`, a mapper and provider per game) |
| Sync | `service/CardSyncService.kt`, `CatalogImportService.kt`, `SyncRunService.kt`, `CatalogSyncJob.kt`, `ExchangeRateService.kt`, `CatalogText.kt` |
| API | `controller/CatalogController.kt`, `AdminCatalogController.kt`, `orchestration/CatalogOrchestrator.kt`, `AdminCatalogOrchestrator.kt`, `dto/CatalogDtos.kt`, `repository/CardSpecifications.kt` |
| Migrations | `db/migration/V2__card_catalog.sql`, `V3__profile_currency_and_address_name.sql`, `db/vendor/postgresql/R__card_name_trigram_index.sql` |
| Pages | `views/catalog/`, `views/SearchView.vue`, `views/admin/AdminSyncView.vue` |
| Building blocks | `components/catalog/` (`CardFilters.vue` holds sorting and filters), `data/games.js`, `utils/catalog.js`, `stores/currency.js` |

## New environment variables

All are in `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `SCRYFALL_BASE_URL` | Scryfall API | `https://api.scryfall.com` |
| `LORCAST_BASE_URL` | Lorcast API | `https://api.lorcast.com/v0` |
| `OPTCG_BASE_URL` | OPTCG API | `https://optcgapi.com/api` |
| `ECB_BASE_URL` | European Central Bank, for exchange rates | `https://www.ecb.europa.eu` |
| `CATALOG_SYNC_CRON` | When the nightly sync starts, in Dutch time. `-` switches it off | `0 0 3 * * *` |

`TCGDEX_BASE_URL` already existed. The skeleton's `TcgdexConfig` and `TcgdexProperties` are replaced by `app.catalog.*`.

## Upgrading a local database

`prod` uses Flyway, but on the `postgres` and `docker` profiles Hibernate can't do these two changes itself. Run this once on a database from phase 1:

```sql
ALTER TABLE users ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';
ALTER TABLE addresses DROP COLUMN name;
```

## Checks

| Check | Result |
|-------|--------|
| `./gradlew test` | 289 tests, 0 failed |
| `npm run build` | Passes |
| Full sync, Pokémon | 205 sets, 21,291 cards, 28,276 prices, 0 failed. Exactly the expected numbers |
| Full sync, Magic | 1,004 sets, 110,861 cards, 149,359 prices, 0 failed. Exactly the expected numbers |
| Full sync, Lorcana | 26 sets, 3,505 cards, 6,211 prices on 3,367 cards (96%), 0 failed |
| Full sync, One Piece | 61 sets, 4,988 cards, 4,845 prices (97%), 0 failed |
| Sync twice | Lorcana, One Piece and Magic: 0 created, 0 changed, all 119,354 cards unchanged. No duplicate ids in any game |
| Interrupted run | A restart during a Pokémon sync marked it failed with the right message, and the next run continued normally |
| Browser, 360 and 1280px | 229 checks, 0 failed, no console errors. No horizontal scroll on any page |
| Card of each game | Pokémon, Magic, Lorcana and One Piece show image, details and prices at both widths |
| `prod` on an empty Postgres database | Flyway applies V1, V2, V3 and the trigram index, Hibernate validation passes. A full Lorcana sync, a filtered and sorted search, the currency setting and an address all work on that schema. The database was dropped afterwards |
| Search speed | "charizard" over all 140,645 cards: under 1 ms with the trigram index, on real data in a transaction that was rolled back |

The browser checks ran with a temporary Playwright script that is not part of the repo. They cover all four games, sets with awkward codes (`OP14-EB04`, `DON`), the empty `jumbo`, the 5,741 cards of `plst`, every sort direction, every filter, the image rules, currency switching, and the address form.

The Pokémon energy types needed a second full sync, because they only exist on each card. That sync was still running when this phase was committed, at 44 of 205 sets with 0 cards created and 0 failed. The energy type filter on Pokémon sets fills in as it goes. Kinds of card and sort prices were filled in from existing data, so those already work for every set.

Checks and screenshots found these bugs, all fixed: `EB-05` pushed "Extra boosters" above the main boosters, `?set=` counted as "the set with an empty code", the filter text was cut off at 360px, a typo in the source showed up as a colour called "Character", and Hibernate's `CHECK` on `card_prices.source` blocked new sources on local databases.

## Decisions

- **TCG Pocket and digital Magic are skipped.** These are not physical cards, so they don't belong in a shop or a collection of real cards. That leaves out 15 Pokémon sets (2,480 cards) and 62 Magic sets (9,172 cards).
- **Lorcana and One Piece use TCGplayer prices, not Cardmarket.** Decided during the phase, see "Prices for Lorcana and One Piece" below.
- **Converted prices have no "≈" sign,** which looked unprofessional. The card page says in a small line that a price was converted, with which rate and date.
- **Currency only changes how card prices are shown.** They are stored in their own currency, and shop prices stay in EUR. Logged-out visitors see EUR. The ECB says its rates are for information only, which fits showing a price but not charging one.
- **`sortPriceCents` is a stored copy** of the list price in euro cents. Sorting has to happen in the database to work across pages. It is refreshed by every sync, so a changing exchange rate moves the order a little at most. Phase 3 can use it for "most expensive cards".
- **One colour field for all games:** Pokémon energy types, Magic colours, Lorcana inks and One Piece colours are one filter with its own label per game. Basic energy cards only have their type in their name, so "Fire Energy" counts as Fire.
- **A failing Pokémon card doesn't stop a run.** It is counted in `cardsFailed` and keeps its existing data. 25 failures in a row stop the run, because the API is then probably down. Any other failure, such as a set that can't be fetched or saved, fails the run with the error logged. Sets already imported stay.
- **Card images are never touched,** which Scryfall's terms require: `object-fit: contain`, no rounded corners on the image and nothing on top of it. This applies to all games, to keep one component. Prices and labels go below the image.
- **Sync pool:** a private `Executors` field and not a bean. An `Executor` bean makes Spring Boot drop `applicationTaskExecutor`, and the `@Async` mails would then wait behind a sync.
- **Sorting:** cards sort on `sortKey` and then `id`. Alternate arts share a number, and without the id a card could show up on two pages or on none.
- **`nameSearch`** only holds letters, digits and spaces, so `%` and `_` can't reach a `LIKE` from a search term.
- **The fallback tile** for sets without art shows the set code, which is how collectors know these sets. All Lorcana and One Piece sets use it, because those sources have no set art.
- **catchyourcards.nl** answered 403 to automated requests during this phase, so these pages weren't compared with it. They use the familiar shop patterns from phase 1: breadcrumbs, a filter row above a card grid, and name and price below the image.

## Source notes

Everything here was tested against the live APIs on 2026-11-05. All clients send `UithoornTCG/1.0` as `User-Agent`, not an email address.

### TCGdex (Pokémon)

- Lists have no rarity, Pokédex numbers or prices, and GraphQL has no prices. So every card needs its own request.
- Terms: MIT licensed. "No published hard rate limits, but please be considerate."
- Card ids need URL encoding: `exu-%3F` is requested as `exu-%253F`, `exu-!` as `exu-%21`.
- Four sets have a card total but no cards at the source: `jumbo` (160), `rc` (25), `sp` (10) and `wp` (7). They show an empty state. `mfb` and `tk-sm-l` also list fewer cards than their total. **Phase 3 must count stored cards for progress, not `cardCount`,** or those sets can never reach 100%.

The obvious price mapping shows wrong prices, so these rules come from checking real cards. `TcgdexMapperTest` covers each one.

1. Read `pricing.cardmarket` at the top level. `variants_detailed` can have no prices where the top level does (`sm12-1`).
2. Base fields `trend` and `avg` belong to `normal`, or to `holo` if there is no normal.
3. The `trend-holo` and `avg-holo` fields belong to `reverse`, and are only used if the card has a reverse. Charizard `base1-4` has a `trend-holo` although no reverse exists.
4. A card with normal and holo but no reverse: the `-holo` fields belong to `holo`.
5. A card with normal, holo and reverse: `holo` gets no price.
6. A card with neither normal nor holo: `reverse` uses `-holo`, or the base fields if those are missing.
7. `firstEdition`: only from the `variants_detailed` entry with stamp `1st-edition`, and only if its `idProduct` differs from the top level. If it is the same product (`neo1-9`), storing it would be making up a price.
8. Other stamps, subtypes and jumbo sizes are ignored.
9. `0` or `null` means no price. `updated` is the price date.

### Scryfall (Magic)

- `GET /bulk-data`, type `default_cards`, field `jsonl_download_uri`. The old `download_uri` no longer exists.
- The file is 81 MB, served as plain `application/gzip` without `Content-Encoding`, so the provider unzips it. One card per line, not ordered per set, so cards are grouped in memory first (about 100 MB of heap).
- Double-faced cards have their images on `card_faces[0]`.
- Some sets count digital cards in `card_count`, so 9 sets declare more cards than we store. `nau` is announced with 0 cards.

Terms that affect the design:

- No paywall: card data must stay reachable anonymously or with a free account.
- No Scryfall logo, and nothing that suggests they endorse us.
- Must add value beyond republishing their data. The tracker does.
- Images: do not cover, crop, distort, blur, sharpen, desaturate or colour-shift them, and no watermarks, stamps or logos.
- "Prices should be considered dangerously stale after 24 hours" and "not updated frequently enough to power a storefront or sales system".

### Lorcast (Lorcana)

- Beta API, 50 to 100 ms between requests. Use image URLs as they are, because they can change.
- Sets have no logo and no card count, so the count is the number of cards. Numeric codes are the main sets.
- There is no finishes field: `normal` if `prices.usd` exists, `foil` if `prices.usd_foil` exists, `normal` if neither.
- Prices change once a day and have no date, so they carry the date of our sync.
- `version` can be missing. Images are AVIF.

### OPTCG API (One Piece)

- Run by one person on a paid server, who asks to keep calls low. A full sync uses 6.
- `/allPromoCards/` is documented but gives 404, so we use `/promos/filtered/?rarity=P`. All promos go into one set `P`, and DON!! cards into one set `DON`, whatever their own `set_id` says.
- `EB-05` and `ST-29` are in neither `/allSets/` nor `/allDecks/`, so sets are also taken from the cards. Two cards of ST-29 carry another set's name, so the most common name wins.
- Ids aren't unique: the same `card_image_id` occurs in several sets, and even twice in one set for different cards. The external id is `set_id|card_image_id|card_name`, and 4 exact copies are dropped.
- There are no release dates. The order of `/allSets/` is chronological, so it decides the `sortOrder`.
- 64 cards have no image, mostly promos. `set_id` is empty on 2 DON!! cards.
- Every price has its own `date_scraped`, and 758 of them are older than 30 days. The oldest is from June 2025. The card page shows that date.
- `card_color` holds two colours as "Green Red". One card has "Character" there, so only the six real colours are accepted.
- Images have a "SAMPLE" watermark at the source.

### Prices for Lorcana and One Piece

Tested on 2026-11-05 and 2026-11-06.

| Option | Result |
|--------|--------|
| Cardmarket's public files, One Piece (id 18) | Only 107 of 4,801 cards match one product. Names don't tell versions apart: "Roronoa Zoro (OP01-001)" exists at both €2.47 and €619.74 |
| Cardmarket's public files, Lorcana (id 19) | 2,603 of 3,505 cards (74%) match one product. Regular and Enchanted versions can't be told apart, and which expansion is which set has to be worked out from the names |
| eBay | The Buy APIs are for approved partners, sold prices are a limited release, and the licence couldn't be read. Asking prices also aren't market prices |
| Prices already in Lorcast and OPTCG | About every card, on the card record itself, so nothing has to be matched |

Neither source says where its prices come from. Comparing them with TCGplayer's public prices (mirrored on tcgcsv.com) settled it: Lorcast's `usd` is within 5% of TCGplayer's market price for 202 of 204 cards, and the OPTCG `market_price` for 85 of 86 cards. `inventory_price` matches nothing, so it isn't used.

The Cardmarket files are at `downloads.s3.cardmarket.com/productCatalog/priceGuide/price_guide_{id}.json` and `.../productList/products_singles_{id}.json`. They are offered to all Cardmarket users, but the announcement says nothing about commercial use, and their terms page blocks automated reads.

### ECB (exchange rates)

`https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml`, updated around 16:00 CET on working days. "Published for information purposes only", and "using the rates for transaction purposes is strongly discouraged". A nightly sync gets the rate of the day before, which is fine for showing prices. The parser refuses XML with a doctype, so outside XML can't read local files.

## Open issues

- **Pokémon prices shared between cards:** 1,384 of 20,057 priced Pokémon cards (6.9%) have exactly the same price as a card with the same name in their set. For Expedition Charizard #6, #39 and #40 this is proven: TCGdex links all three to one Cardmarket product (`idProduct 274881`). Some of these prices will be wrong, and the data doesn't show which. They are shown as the source gives them. This affects collection value in phase 3. To be decided: leave them, or hide prices that are shared.
- **Legal check before going live:** the Wizards Fan Content Policy and Ravensburger's [Community Code](https://cdn.ravensburger.com/lorcana/community-code-en) are written for fans and creators, and Lorcast says it is "expressly prohibited from charging you to use or access this content". A shop is commercial, even with a free tracker. Nothing found says this is allowed or forbidden. Neither Lorcast nor OPTCG says anything about reusing their prices, which come from TCGplayer. Someone should check all of this.
- **One Piece images** load straight from one person's server, as the prompt asks, which could be a burden at shop traffic. They also carry a "SAMPLE" watermark.
- **No admin on `postgres`, `docker` and `prod`:** only `dev` seeds accounts. Production needs a way to create the first admin (phase 8).
- **Heap:** the Magic sync groups the bulk file in memory. The syncs ran with `-Xmx2g`. Set a limit that is high enough in a container.
- **catchyourcards.nl** wasn't compared, see Decisions. Worth a look at these pages next to it.

## Notes for later phases

- **Phase 3:** "missing cards greyed out" is not allowed for Magic images under Scryfall's rules. Use something that leaves the image alone, such as a border, a "gemist" label below it, or a placeholder. To be discussed.
- **Phase 3:** count stored cards for set progress, see the TCGdex notes. `card_dex_numbers` already has the index the Pokédex needs.
- **Phase 4:** the "te koop" label must sit outside the card image, not on it.
- **Phase 4:** Scryfall says its prices aren't fit for a storefront. Showing them as a suggestion for the admin is fine, but never use them as the shop price automatically.
- **Phase 4:** the Pokémon series in `data/navigation.js` are a fixed list that real sets can replace now.
- **Phase 8:** set titles and meta descriptions on the catalog pages, and a sitemap that covers them.
