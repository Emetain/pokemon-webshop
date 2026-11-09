# Phase 4: Shop products and stock

A browsable shop for all four games and accessories, the admin tools to fill it, and a hero carousel on the home page.

## What was built

**Backend**

- `Product` with every field from the plan: game (empty for accessories that fit every game), type, accessory type, name, slug, description, language, set, card, card variant, condition, grading, price, sale price, images, status, pre-order fields, and the low-stock warning level. Imported products remember where they came from, and whether the admin edited them since.
- Stock in its own `ProductStock` row with a version, so two purchases of the last item can't both succeed, and editing a product never clashes with a purchase. `StockService` is the only way stock changes, and every change writes a `StockMovement` with the reason, the numbers after it, a note and the admin. It can adjust, set a counted number, reserve, release and sell. A lost race is tried once more with the new numbers, which then gives the real answer, such as "uitverkocht".
- Every product can be in the shop without stock: lists and its own page show "Uitverkocht". A product without a price is in the shop but can't be bought either.
- **Sets out of print stay out of the shop.** Every `CardSet` knows whether it is still printed, from a date per game in `app.shop.in-print-from`. Products of a set out of print only show while they can be bought, and the series navigation leaves those sets out. The product page itself stays reachable, for links and wishlists. The import skips them. `ShopSetRules` sets this when a sync saves a set, and for all sets at startup.
- Public endpoints: `GET /api/v1/products` with filters (game, type, accessory, series, set, language, price range, in stock, pre-order, sale, search) and sorting (aanbevolen, new, price both ways, name, popular, release date), plus the filter options that exist in that part of the shop. Product detail by slug, new arrivals and bestsellers.
- **Sorting goes by set.** "Nieuwste" sorts on the release date of the product, or else of its set, newest first, with the products of one set together and the big boxes first. "Aanbevolen" puts products in stock and just arrived first, then does the same. "Populair" puts the sets of `app.shop.popular-sets` first in their order, then the newest sets, and within a set what customers here want most. "Releasedatum" uses the same date, soonest first.
- **One Piece sets have release dates now,** from Bandai's English product pages in `OptcgReleaseDates`, because the OPTCG API has none. Without them, "Nieuwste" and the print status can't work for One Piece.
- Series and sets for the shop navigation: `GET /api/v1/shop/{game}/series` and `/series/{slug}`, from the card catalog, with product counts.
- Product wishlist (`/api/v1/wishlist/products`) and "mail me when back in stock" (`POST /api/v1/products/{slug}/stock-alerts`, rate limited). The mail goes once, after the change is saved, and also when a product with stock gets its price later.
- Card lists now say which cards the shop sells as a single in stock (`forSale`: cheapest price and number of offers), and the card detail lists the offers.
- **Product list import** from Cardmarket's public product list, per game, started by an admin. See Decisions and Source notes.
- **Products of one set**: the usual sealed products of a set in one go, without price or stock, skipping what the set already has.
- **Quick single**: a single from the card catalog with the market prices in euros as a suggestion. The shop price is never filled in from them.
- **CSV** export and import of products and stock in one file.
- **Hero slides** (`HeroSlide`): title, text, button with link, image, game colour, start and end date, on or off, order.
- Product images through `MediaStorage`. A duplicate shares the files of the original, and a file is only deleted when no product uses it any more.
- `DevDataSeeder` adds 20 demo products for all four games and accessories, with stock, a sale, a pre-order, sold-out products and three hero slides.

**Frontend**

- Home: the hero is a carousel. Slide 1 is the hero with the binder, then the slides from admin. It turns every 8 seconds, stops on hover and focus, while the tab is hidden or the hero is out of view, and for visitors who prefer reduced motion. It has buttons to go back, forward, to a slide and to pause. Below it: "Nieuw binnen", the games, "Populair" and the newest pre-orders.
- Shop pages per game (`/pokemon`), per kind of product (`/pokemon/booster-boxen`), per series (`/pokemon/serie/scarlet-violet`) and set (`/pokemon/serie/scarlet-violet/sv08`), accessories (`/accessoires`, `/accessoires/sleeves`), `/pre-order` and `/producten`. Kinds of products as chips in the band, filters and the series navigation in a sidebar, all filters and sorting in the URL.
- Product tiles with badges below the image ("Pre-order", "Uitverkocht", "−11%", "Laatste 2"), price with the old price struck through, "incl. btw", a wishlist heart and the cart button (disabled until phase 5). A product that can't be bought shows no price at all, on its tile and on its page.
- Product page `/product/{slug}`: gallery, price, stock status, pre-order ship date, the back-in-stock form when sold out, details, description, and for singles the card in the catalog with its market price and source. Other offers of the same card and related products from the set.
- The Pokémon series in the menu come from the catalog now: the newest series that have products.
- Card set pages show "Te koop vanaf €X" below cards the shop has as a single, never on the image. The list of missing cards has a column with a link to the single.
- `/verlanglijst` shows products above the cards. Search shows matching products above the cards.
- Admin: products table with search, filters and 25/50/100 per page; product form with card picker, photos and stock panel with the log; duplicate and archive; quick single; stock overview with "Bijna op" and the latest changes; import page (Cardmarket, one set, CSV); hero slides.

## Main files

| Area | Files |
|------|-------|
| Entities | `domain/Product.kt`, `ProductStock.kt`, `StockMovement.kt`, `ProductWishlistEntry.kt`, `StockAlert.kt`, `HeroSlide.kt` and the enums `ProductType`, `ProductLanguage`, `ProductStatus`, `AccessoryType`, `GradingCompany`, `StockReason`, `ProductImportSource` |
| Logic | `service/StockService.kt`, `ProductRules.kt` (prices, buyable, "Laatste", slugs), `ProductService.kt` (with `SetProductTemplates`), `ProductCsv.kt`, `ProductCsvService.kt`, `ProductImportService.kt`, `StockAlertService.kt`, `CardOfferService.kt`, `HeroSlideService.kt`, `ShopSetRules.kt` (in print and popular sets) |
| Source | `service/provider/CardmarketProductMapper.kt`, `CardmarketProductProvider.kt`, `OptcgReleaseDates.kt` |
| Settings | `app.shop.in-print-from` and `app.shop.popular-sets` in `application.yml`, with the sources in comments |
| Queries | `repository/ProductSpecifications.kt`, `ProductRepository.kt` |
| API | `controller/ProductController.kt`, `ShopController.kt`, `ProductWishlistController.kt`, `AdminProductController.kt`, `AdminStockController.kt`, `AdminProductImportController.kt`, `AdminHeroSlideController.kt` and their orchestrators |
| Migration | `db/migration/V5__shop_products_and_stock.sql`, `V6__set_print_status_and_popularity.sql` |
| Pages | `views/shop/ShopListView.vue`, `ProductView.vue`, `views/HomeView.vue`, `views/admin/AdminProducts*`, `AdminSingleView.vue`, `AdminStockView.vue`, `AdminImportView.vue`, `AdminSlidesView.vue` |
| Building blocks | `components/shop/` (`ProductTile`, `ProductGrid`, `ProductImage`, `ProductBadges`, `PriceTag`, `ProductFilters`, `SeriesNav`, `ProductRow`, `HeroCarousel`, `StockAlertForm`, `WishlistToggle`), `components/admin/` (`CardPicker`, `ProductImages`, `StockPanel`), `data/shop.js`, `stores/shopNavigation.js` |

## New environment variables

In `.env.example` and passed to the `backend` service in `docker-compose.yml`.

| Variable | Purpose | Default |
|----------|---------|---------|
| `CARDMARKET_PRODUCTS_BASE_URL` | Cardmarket's public product list, for the import in admin. Only read when an admin starts an import | `https://downloads.s3.cardmarket.com` |

## Upgrading a local database

Restart the backend: the running Phase 3 build doesn't know any of the new endpoints and answers 404 on all of them. Hibernate adds the new tables at startup. Then fill the shop at `/admin/producten/import`, per game.

After the first review: restart the backend again. Hibernate adds `in_print` and `popular_rank` to `card_sets`, and startup fills them for all sets (1,028 changed on the local database). One Piece sets get their release date with the next One Piece sync, at night or from `/admin/sync`. Until then One Piece keeps every set and sorts by the order of the API.

## Checks

The checks with real data ran on a throwaway copy of the local database, with the new backend on port 8095 and a second Vite on 5174. The copy was dropped afterwards, and the backend on 8080 was left alone.

| Check | Result |
|-------|--------|
| `./gradlew test` | 543 tests, 0 failed. After the first review: 553 tests, 0 failed |
| `npm run build` | Passes |
| Done when: every product type, images, stock in the log | An integration test creates all 14 types through the API, including a single, a slab and an accessory. In the browser: a new product through the form, a photo upload, a stock change that shows in the log at once, duplicate, archive |
| Done when: no stock means "Uitverkocht" and no cart | Lists and the product page show "Uitverkocht", `canBuy` is false, the cart button is disabled, and the back-in-stock form shows. The cart itself is phase 5 |
| Done when: filters and sorting | Integration tests for every filter, including the price range on the sale price, and every sort order. In the browser the filters and sort end up in the URL and give the right products |
| Done when: two purchases of the last item | One test makes two buyers read "1 left" at the same moment: exactly one succeeds, the other fails on the version, and the log has one sale. A second test lets 20 buyers go for 3 items: exactly 3 sold, stock 0. Both also passed on Postgres |
| Import on the real catalog | Pokémon: 5,109 products in the list, 1,456 imported. Magic: 5,159 and 1,451. Lorcana: 296 and 107. One Piece: 810 and 95. About a second per game. A second run made nothing new: every product unchanged |
| Import keeps edits and stock | Tests: a product the admin edited keeps its name and price, and stock stays as it was |
| `prod` on an empty Postgres database | Flyway applies V1 to V5, Hibernate validation passes. Creating a product, stock, a slide, the wishlist, popular sorting, the low-stock list and the CSV export all work on that schema |
| Product and stock tests on Postgres | 26 tests, 0 failed |
| Dev seeder | 20 products and 3 slides on a fresh `dev` start |
| Browser, 360, 768 and 1280px | 20 pages at each width: no horizontal scroll, no console errors or Vue warnings |
| Browser flows | 32 checks pass: the carousel and its controls, filters, series and set pages, sold out, back-in-stock signup, wishlist, "te koop" on a set page, products in search, unknown pages, product form, photo, stock, duplicate, quick single, set tool, CSV download, slides with an unsafe link refused |
| After the review: print status on the real catalog | Startup marks 184 of 205 Pokémon sets, 801 of 1,004 Magic sets, 13 of 26 Lorcana sets and, after a One Piece sync, 14 of 61 One Piece sets as out of print. That hides 2,569 of the 3,109 imported products: 1,180 Pokémon, 1,296 Magic, 64 Lorcana and 29 One Piece. Pokémon keeps 276 products in two series, Mega Evolution and Scarlet & Violet from Temporal Forces on |
| After the review: sorting on the real catalog | "Nieuwste" and "Aanbevolen" start with 30th Celebration, then Pitch Black, Chaos Rising, Perfect Order, Ascended Heroes and on to Temporal Forces, each set together with the Elite Trainer Box first. "Populair" goes 30th Celebration, Ascended Heroes, Prismatic Evolutions, Black Bolt, White Flare, Phantasmal Flames and so on. The popular row on the home page shows one product per set across the four games |
| After the review: One Piece sync | Every One Piece set gets a date except the promos and DON!! cards. "Nieuwste" goes OP-17, OP-16, ST-30, OP15-EB04, OP14-EB04, OP-13 |
| After the review: browser at 360 and 1280px | 7 pages at each width, no horizontal scroll. A sold-out tile and product page show no price and no "Prijs volgt", a product with stock shows its price. An old series such as `/pokemon/serie/xy` shows the not-found page, an old product's own page still opens |
| Delta Reign, 2026-11-09 | Restart on the real database: 13 sets changed rank, Delta Reign third. The Pokémon import again: 14 new products, all Delta Reign (booster, sleeved booster, two booster boxes, bundle, Elite Trainer Box, Build & Battle Box, blisters and the Mega Rayquaza ex Premium Tournament Collection), 276 unchanged, none edited. "Populair" shows 30th Celebration, then Delta Reign. `./gradlew test` passes |
| Half booster boxes, 2026-11-09 | A mapper test turns "Booster Box (18 Boosters)" into "Half Booster Box" and leaves "Booster Box" alone. On the real database 65 products renamed, none left with the old name, none edited by the admin. The shop and search show "Delta Reign Half Booster Box" |
| "Halve" instead of "Half", 2026-11-09 | The mapper test now expects "Halve Booster Box". On the real database the same 65 products renamed again, name and search name, none left with "Half". The shop and search show "Delta Reign Halve Booster Box" |

The browser checks ran with temporary Playwright scripts that are not part of the repo.

Checks and screenshots found these bugs, all fixed:

- The product rows on the home page made the page 6,000 pixels wide on a phone: a grid child needed `min-width: 0`, and the hidden labels in the tiles escaped the scrolling row until it was positioned.
- Tabular figures put wide gaps in prices on this typeface, as with the percentages in phase 3.
- "Te koop" below a card pushed its price out of line with the row.
- The long series list hid the filters in the sidebar.
- On a phone, promo slides took the height of the binder slide and showed a big empty blue area.
- The Poké Ball showed on the Lorcana slide.
- The import didn't save updates when called on its own, outside the admin endpoint.
- The CSV export quoted every price because of the decimal comma.
- The query for filter options skipped products without a set.

## Decisions

- **The hero is a carousel,** the owner's idea during this phase. The hero with the binder stays slide 1, as phase 3 decided. Admin slides follow in their own order. Phase 6 adds slides for drops by itself, and phase 7 lets the admin have slides written by AI. Both are in the build prompt now.
- **From 768 pixels all slides share the height of the tallest,** so the page never jumps. On a phone the binder slide is far taller than the rest, so there the hero follows the current slide, and it only turns while it's on screen.
- **A product without a price can't be bought.** Imported products have no price, so they show "Uitverkocht" until the owner sets one. Stock without a price doesn't make a product buyable either.
- **No price while a product can't be bought,** the owner's choice after the first review. The tile and the product page show nothing where the price goes, not "Prijs volgt" and not the last price. The badge and the stock line already say "Uitverkocht".
- **Imported products are active,** because the owner wants every product in the shop from the start.
- **Cardmarket's list is the import source,** not tcgcsv. Cardmarket is the European market and its categories map well to our product types. tcgcsv mirrors TCGplayer, the US market, with US-only shop exclusives, and its images would be another legal question.
- **Products are only imported for sets in our catalog.** Cardmarket lists every edition in one file without a language. An expansion is linked to the set that most of its products start with, and products of expansions that can't be linked stay out. This keeps Japanese, Chinese and other editions out, as well as sets the card sync doesn't have yet. Running the import again after a sync adds those.
- **Names say what stays out:** Pokémon Center, Japanese, Chinese, Korean, Indonesian and Thai editions, US shop exclusives, cases and displays of other products, promos and prerelease packs. Coins, lots and complete card sets aren't a product type of the shop.
- **Imported products are English,** because our catalog holds the English sets.
- **A box of 18 boosters is a "Halve Booster Box",** the owner's name for it, added on 2026-11-09 (first as "Half Booster Box", changed to the Dutch "Halve" the same day). A normal booster box has 36 boosters, and Cardmarket lists the half one as "Booster Box (18 Boosters)". `CardmarketProductMapper.shopName` renames it at the import. The 65 imported before, 12 of them in sets in print, were renamed once in the database. Their URLs keep "18-boosters", because a product keeps its URL when its name changes.
- **Both the import and the set tool are built.** The plan has the set tool as a fallback without a usable source. Whether the source may be used is part of the legal check, so the owner has a way either way.
- **"Nieuw binnen" means just back in stock,** not just created. Otherwise an import of 3,000 products would fill the row with sold-out products. `Product.arrivedAt` is set when a product goes from sold out to in stock.
- **"Popular" follows the market until there are orders,** the owner's choice after the first review. Wishlists and people waiting for stock decided first at the start of the phase, but in a new shop that came down to the order of the import. Now a list of the most popular sets per game goes first, then the newest sets, and the wishes of customers here only decide within a set. Phase 5 switches it to order lines.
- **"Nieuwste" means the newest set, not the newest in the shop,** also the owner's choice. The date the product was made in the shop is the order of an import, which put XY Evolutions above 30th Celebration. The products of one set stay together, booster boxes first, in the order of `ProductType`.
- **A set is in print while it's legal in its game's rotating format:** Pokémon Standard, Magic Standard, Lorcana Core and One Piece Standard. None of the four publishers says when a set goes out of print, and shops report sets running out around rotation. For Pokémon this gives what shops report: Scarlet & Violet up to Paldean Fates is gone, Temporal Forces onwards is available. For the other games it is the best rule there is. The owner moves a date in `application.yml` when a format rotates.
- **Out of print means not in lists, but still for sale.** A product of an old set that is in stock and has a price shows everywhere, and its set comes back in the navigation, because the owner sometimes gets hold of an old box or sells old singles. The product page always works, so links, wishlists and the back-in-stock mail don't break.
- **The import plans with every set, and then skips the old ones.** Planning with only the sets in print could put a product of an old set in a newer set with a similar name.
- **The popular row on the home page shows one product per set,** the first in the "Populair" order. Otherwise it showed eight products of 30th Celebration. The four games share the list, so the row mixes them: the most popular set of each game, then the second, and so on.
- **The rules are stored on the set,** as `in_print` and `popular_rank`, filled in from the settings when a sync saves a set and for all sets at startup. The product queries stay simple that way, and a changed setting counts after a restart.
- **One Piece release dates are a list in the code.** The OPTCG API has none, and Bandai's product pages have the English date of every set, including the next ones. A set that isn't on the list yet has no date and goes by the order of the API, as before.
- **Badges sit below the image for every product,** not only for card images, so tiles look the same everywhere.
- **"Laatste X" shows at the warning level or below,** 2 by default. The low-stock overview only lists products the shop has had in stock: an imported product that was never stocked isn't running out.
- **Singles have a card variant,** which the plan didn't list. The market price and the name depend on it, for example a holo next to a reverse holo.
- **Stock is its own row with the version.** A version on the product would make an edit in admin fail during a purchase.
- **The back-in-stock mail has no double opt-in.** It is one mail that the customer asked for, and the signup is rate limited per IP.
- **A product keeps its URL when its name changes,** so shared links keep working. The admin can change the slug by hand.
- **Hero slide links are a path in the shop or an https link,** never another scheme.
- **The home page has no drop block yet.** Drops don't exist until phase 6, which adds them as slides in the carousel.
- **One CSV for products and stock,** with semicolons, decimal commas and a byte order mark for Dutch Excel. Text that starts like a formula gets a quote in front.
- **Search shows products above the cards,** because a customer typing "Prismatic" probably wants the products.
- **The menu shows the newest series that have products,** up to 6. A shop without products gets the newest series with at least 3 sets.

## Source notes

### Cardmarket product list

- `downloads.s3.cardmarket.com/productCatalog/productList/products_nonsingles_{id}.json`, with id 6 for Pokémon, 1 Magic, 18 One Piece and 19 Lorcana. One JSON object with `version`, `createdAt` and `products`. Each product has `idProduct`, `name`, `idCategory`, `categoryName`, `idExpansion`, `idMetacard` and `dateAdded`. No language, no release date, no image.
- Categories for Pokémon: Box Set 1,121, Booster 796, Coins 695, Display 626, Tins 571, Theme Decks 560, Blisters 498, Elite Trainer Boxes 171, PCG Set 30, Lot 28, Trainer Kits 13.
- Names follow `<set> <product>` or `<set>: <detail> <product>`. Asian editions say so in the name or have their own expansion: "JP", "S-Ch", "Pokémon Card 151", "(Asia Region Legal)". Expansion 1645 is a mix of loose products without a set.
- Checked on 2026-11-08 in the build prompt: nothing about commercial use, so it is part of the legal check. The import never runs by itself.

### Print status, checked on 2026-11-08

- **Pokémon:** The Pokémon Company publishes no out-of-print dates. Regulation mark G (Scarlet & Violet base up to Paldean Fates) left Standard on 10 April 2026. Shops report those six sets as drying up or out of print, and Temporal Forces onwards (mark H, from 22 March 2024) as available at about the normal price ([PlayVault, August 2026](https://playvault.ae/blogs/guides/pokemon-sets-going-out-of-print-2026-uae), [Cardboard Superstars, September 2026](https://www.cardboardsuperstars.com/blogs/news/pokemon-sets-going-out-of-print-in-2026)). The H sets are expected to follow in 2027.
- **Magic:** no published policy. Standard rotates in February 2027 and drops Wilds of Eldraine up to Duskmourn, all 2024 sets except Foundations, which stays until 2029 ([Draftsim](https://draftsim.com/mtg-standard-rotation/)).
- **Lorcana:** sets 1 to 8 are out of Core since July 2026, and Ravensburger has said nothing about reprints since The First Chapter in 2024.
- **One Piece:** block 1 (OP-01 to OP-04, ST-01 to ST-10) left Standard on 1 April 2026 ([Shonen TCG](https://www.shonentcg.com/blog/one-piece-tcg-meta-rotation-guide)). Bandai reprints some cards in the premium boosters, not the old sets.

### Popular sets, checked on 2026-11-08

- **Pokémon:** sealed prices against the shop price, late August 2026 ([Rare Pull](https://rarepulltcg.com/blog/best-pokemon-sets-to-buy-fall-2026)). Elite Trainer Boxes: Ascended Heroes 3.3×, Black Bolt 3.2×, Prismatic Evolutions 3.1×, White Flare 2.9×, Phantasmal Flames 2.8×, Destined Rivals 2.6×, Journey Together and Surging Sparks 2.5×, Chaos Rising and Pitch Black 1.4×, Perfect Order 1.3×. 30th Celebration was on pre-order for three to four times its price.
- **Delta Reign,** added on 2026-11-09 once the sync had it, third after the two 30th Celebration sets. It came out on 6 November 2026 and has no sealed prices to compare yet. Its first days are mixed: launch demand was high, with longer queues at shops and a retailer reporting its busiest Pokémon Center day ([House of Carts](https://dashboard.houseofcarts.io/resources/pokemon-delta-reign-release-guide), [Le Chop TCG](https://lechoptcg.com/2026/11/02/pokemon-tcg-market-pulse-delta-reign-retail-countdown-and-octobers-market-split/)), while prices of the singles fell from their pre-release level ([The Collector Nation](https://thecollectornation.com/analysis/delta-reigns-first-day-of-sales-which-cards-are-actually-moving-and)). At the end of the list it would change nothing, because "Populair" puts the newest set right after the list anyway. In its launch week it is the set customers ask for, so it goes near the top. Move it once there are sealed prices.
- **Magic:** TCGplayer's sales from 17 August to 16 September 2026 ([TCGplayer](https://seller.tcgplayer.com/blog/top-selling-magic-the-gathering-sets-right-now-09-16-2026)).
- **Lorcana:** TCGplayer's best-selling sets of 2026 up to the summer: Wilds Unknown, Winterspell and Attack of the Vine!
- **One Piece:** TCGplayer's quarter reports: The World's Strongest Warriors fourth in Q3, Adventure on Kami's Island third and The Time of Battle fifth in Q2, The Azure Sea's Seven among the best sellers of 2026 ([Q3 2026](https://seller.tcgplayer.com/blog/best-selling-trading-card-games-q3-2026)).

### One Piece release dates

- From `en.onepiece-cardgame.com/products/?subcategory=boosters` and `?subcategory=decks`, which list every English set with its date, up to PEB-01 in April 2027. ST-01 to ST-04 had a pre-release in September 2022; the list has the regular date in December.

## Open issues

- **The legal check decides whether the import may be used.** Cardmarket's list and the product names come from them. It is on the go-live list in phase 8.
- **Imported products have no photos.** The tiles show the set logo or the kind of product. The owner uploads photos per product.
- **Upcoming sets come in after the card sync has them.** Cardmarket already lists products of sets that TCGdex doesn't have yet. Until then the set tool or a manual product covers a pre-order.
- **Products without a set aren't imported,** such as premium collections in Cardmarket's loose expansion. The admin adds those by hand.
- **Products of old sets imported before are still in the database,** 2,569 of the 3,109, as sold out without a price. The shop doesn't show them, but the admin table does. They can be removed in one go, or stay so they show up once the owner stocks one. There is no bulk archive yet.
- **The print status and the popular sets are a judgement of 2026-11-08** and need updating: Magic rotates in February 2027, Pokémon and One Piece in spring 2027, Lorcana in the summer. New hot sets need adding to `app.shop.popular-sets`, and new One Piece sets to `OptcgReleaseDates`.
- **The most popular Magic sets have no products,** such as Final Fantasy, Marvel's Spider-Man, Avatar and Teenage Mutant Ninja Turtles. The import doesn't link their Cardmarket expansions to our sets. Worth a look before the shop sells Magic.
- **Some imported names carry Cardmarket's own details,** such as "Booster Box (18 Boosters)" or "Fun Pack (3 Cards)". "Booster Box (18 Boosters)" is now called "Halve Booster Box", see Decisions.

## Notes for later phases

- **Phase 5:** `StockService.reserve`, `release` and `sell` are ready, and `retryingOnStockConflict` in `AdminProductOrchestrator` shows how to handle a lost race. Enable the cart buttons in `ProductTile.vue` and `ProductView.vue`. Use `ProductRules.canBuy` at add-to-cart. Switch the popular sort and bestsellers to order lines, and keep the popular sets and newest sets after them for products without orders.
- **Phase 6:** `StockReason.DROP_ALLOCATION` exists. Add drop slides to the carousel: `HeroCarousel.vue` takes a list of slides after the binder.
- **Phase 7:** `/pre-order` lists pre-orders by release date; the timeline builds on it. The AI slides belong on `/admin/hero`.
- **Phase 8:** add product pages to the sitemap, and give products an Open Graph image. Consider a bulk archive in the admin table.
