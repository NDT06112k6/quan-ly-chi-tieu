# Expense Tracker

## Persistence

Entities and repositories are grouped into the `user`, `category`, `transaction`
and `budget` packages. Monetary values use `BigDecimal` and map to
`DECIMAL(15,2)`. Budgets store a canonical `period` string and a `BudgetType`
(`MONTHLY` or `WEEKLY`). Creation timestamps are initialized before the first insert.

Category, transaction and budget repositories provide `findAllByUserId`,
`findByIdAndUserId`, `existsByIdAndUserId`, `updateByIdAndUserId` and `deleteByIdAndUserId`.
The service layer must supply the authenticated user's ID; never trust a user ID
from a request. Updates and deletes use explicit queries with both `id` and
`userId` predicates. Use `updateByIdAndUserId` for updates rather than saving a
modified entity. Ownership IDs cannot be changed through setters.
Database foreign keys also require transactions and budgets to reference a
category belonging to the same user.

`JpaRepository` also exposes inherited methods without a user filter, such as
`findAll`, `findById`, `deleteById`, and batch operations. Do not use those methods
for user-owned data. Use inherited `save`/`saveAndFlush` only for new entities
whose ownership was assigned by the service. The repository interfaces alone do not derive the current
user from Spring Security.

## Authentication

`SecurityConfig` permits anonymous access to `/login`, `/register`, `/css/**`
and `/js/**`. All other application requests require authentication. CSRF
protection remains enabled, including for login, registration and logout forms.
`GET /login` and `GET /register` render the Thymeleaf pages in
`templates/user/`. Registration submits to `POST /register` and redirects to
`/login` with a success flash message after the account is saved. The form validates email (up to
255 characters), display name (up to 100 characters), and matching passwords
(8–72 characters and at most 72 UTF-8 bytes, as required by BCrypt). New
accounts receive role `USER` and are enabled. Roles and account status are
never bound from the registration form.

After saving a new account, registration creates six categories owned by its
generated user ID: `CHI` categories `Ăn uống`, `Đi lại`, `Mua sắm`, `Hóa đơn`, and
`THU` categories `Lương`, `Thu nhập khác`. Each account receives separate category
rows. `UserService.registerUser` saves and flushes both the account and its
categories in the same transaction. If any insert or flush fails, the entire
registration rolls back, including any categories already inserted; the error
is propagated without a success notification. Duplicate or invalid registrations
do not initialize categories. This applies to new registrations only; existing
accounts are not backfilled. No schema migration, dependencies, environment
variables or cache changes are required.

Validation and business errors are rendered with `th:errors`. The registration
advice also handles concurrent duplicate email inserts using the existing
`uk_users_email` constraint; unrelated database failures remain errors.
Password fields are never repopulated after a failure. Thymeleaf adds the CSRF
token to both forms. No database migration or cache reset is needed.

Submit login forms to `POST /login` with the email in the `username` field,
the password in the `password` field, and a CSRF token. Authentication loads
the account from `UserRepository.findByEmail`, uses its role as a `ROLE_`
authority, and rejects disabled accounts. Submit logout to `POST /logout`
with a CSRF token; successful logout invalidates the session and redirects
to `/login?logout`.

Authenticated users can open `GET /`, rendered by `HomeController` and
`templates/index.html`. The home page displays the monthly dashboard and a
logout form with a CSRF token. After login, Spring Security returns to a saved
request when present, or to `/` otherwise. Anonymous visitors to `/` are
redirected to `/login`.

As explicitly requested for this configuration, the `PasswordEncoder` bean
uses `BCryptPasswordEncoder` directly, overriding the general
`DelegatingPasswordEncoder` rule in `AGENTS.md`. Store password hashes produced
by this bean in `users.password_hash`. Direct BCrypt expects hashes such as
`$2a$...` without the `{bcrypt}` prefix used by `DelegatingPasswordEncoder`.
Existing prefixed hashes require an explicit migration or password reset before
they can authenticate with this encoder. No existing hashes are changed here.
There are no new environment variables or cache settings for this change.

## User profile

Authenticated users can open `GET /profile` from the dashboard to view their
email and display name, and submit `POST /profile/change-password` with
`oldPassword` and `newPassword`. The account is resolved from authentication;
submitted email, ID, role and display name fields cannot select or modify an
account. Controllers expose `UserProfileDTO`, containing only email and display
name. Missing or disabled accounts are denied.

`UserService.changePassword(email, oldPassword, newPassword)` checks the old
password with the injected `PasswordEncoder.matches`, encodes the new password,
and updates the stored hash. The existing BCrypt encoder configuration remains
in use. New passwords have 8–72 characters and at most 72 UTF-8 bytes, matching
registration. Passwords are not trimmed, logged, placed in URLs, or repopulated
after errors. The form uses `@Valid`, field errors and CSRF protection. Invalid
input or an incorrect old password returns the profile form with HTTP 400;
success redirects to `/profile` with a success flash message.

The update matches both account email and the previous hash, and requires the
account to remain enabled. A concurrent password change cannot overwrite a newer
hash and produces an explicit error instead. Only the password hash changes.
The new password applies to subsequent logins; existing authenticated sessions
remain active. There is no password hash migration, schema change, new dependency,
environment variable or server cache change. Refresh open dashboards to see the
new profile link.

## Categories

Authenticated users can list their categories at `GET /categories`, open the
creation form at `GET /categories/new`, and submit it to `POST /categories`.
The listing shows name and type, with buttons to add a category or return home.
Successful creation redirects to `/categories`; validation errors render the
form with `th:errors`. Names are required and limited to 100 characters; type
is required and must be `CHI` or `THU`. Creation always inserts a new category.

`CurrentUserService` resolves the authenticated email to an enabled account's
ID. The controller never accepts ownership from request parameters, and form
binding accepts only `name` and `type`. Missing or disabled accounts are denied.
All category reads include `user_id` and controllers expose only DTOs.

The requested `findByUserId(Long)` repository method and
`getAllCategoriesByUserId(Long)` service method return lists. The web listing
uses their paginated overloads with `Pageable`: `page` is zero-based, `size`
defaults to 20 and is capped at 100, and results always sort by ascending ID.
The legacy `findAllByUserId` remains available for existing callers. Use the
paginated overloads for web lists. No schema, dependencies, environment
variables or cache settings change.

Categories can be edited at `GET /categories/{id}/edit` and saved with
`POST /categories/{id}/edit`. The shared creation/edit form validates the name
and type, retains its edit action on invalid input (HTTP 400), and ignores
submitted IDs and ownership. The path selects the category; the authenticated
account supplies the owner. Missing or foreign categories return HTTP 403,
including invalid edit submissions. Updates use `updateByIdAndUserId` and must
affect exactly one row. Changing `CHI` to `THU` or vice versa changes how existing
transactions in that category contribute to dashboard totals and charts.

The list includes edit links and CSRF-protected forms posting to
`POST /categories/{id}/delete`. GET requests cannot delete categories. Deletion
locks the owned category, counts its transactions with both category ID and
user ID, and checks for budgets before executing the user-scoped delete query.
Categories referenced by transactions or budgets remain unchanged and raise
`CategoryInUseException`. `GlobalExceptionHandler` redirects to `/categories`
with an error flash displayed by the existing SweetAlert2 toast. Successful
edits and deletions use success flashes; other failures remain errors. No related
transactions or budgets are deleted. No dependencies, schema migrations,
environment variables or server cache changes are required. Refresh the category
list to load the new controls.

## Transactions

Each transaction row includes an eye icon and a link to
`GET /transactions/{id}`. The detail page uses the master layout and displays
the amount formatted in dong, date, category name and escaped note. Amounts
are red for category type `CHI` and green for `THU`; the type is also shown
as text. The service reads the transaction with `findByIdAndUserId` and resolves
the category with the same owner ID. Missing and foreign transactions both
raise `AccessDeniedException` and render the shared HTTP 403 error page.
The owner always comes from authentication; supplied ownership parameters
cannot select a different account.

The detail page links back to the list and to `GET /transactions/{id}/edit`,
which reuses the transaction form. Edits submit to
`POST /transactions/{id}/edit`; successful updates return to the detail page.
Invalid forms return HTTP 400 with field errors and retain the edit action
and selected category. The service checks transaction ownership, validates
the owned category and calls the existing user-scoped update query. The
transaction ID comes from the path and cannot be overwritten by form fields.

Deletion submits a CSRF-protected form to `POST /transactions/{id}/delete`
and returns to the list. GET requests never delete data. The delete query
includes both transaction ID and authenticated user ID. Updates and deletes
require exactly one affected row; missing, foreign or concurrently deleted
transactions produce HTTP 403 instead of reporting success. No new dependencies,
schema migrations, environment variables or cache resets are required. Refresh
open transaction pages to load the detail links and reusable edit form.

Authenticated users can list transactions at `GET /transactions?page=0`, open
`GET /transactions/new`, and submit `POST /transactions`. Creation redirects
to `/transactions`. The list contains DTOs, shows category names, and has 20
rows per page, ordered by date descending and then ID descending. The service
also accepts a page size from 1 to 100. Invalid pagination returns HTTP 400.
The table columns are date, category, amount and note; previous/next buttons
follow the Spring Data `Page` state. The creation form posts to `/transactions`
with a formatted amount input, a date input, a category dropdown and a note field.

Both creation and editing use a text amount field with `inputmode="numeric"`
and `/js/transaction-amount.js`. Typing `400000` displays `400.000`. The visible
field has no request name; only the hidden `realAmount` input binds to `amount`.
The script synchronizes that hidden value on input and immediately before
submission, removing thousands dots. Quick buttons select `100.000`, `500.000`,
`1.000.000`, `5.000.000` and `10.000.000`, updating both fields without submitting.
An optional comma marks fractional dong: `1.000,50` submits `1000.50`. Existing
decimal amounts are restored using that convention when editing or returning
from validation errors, without rounding or conversion through JavaScript
`Number`. Caret position is retained while formatting edits within the number.

Blank, zero, negative, nonnumeric or excessive-precision input clears the
hidden amount and blocks submission with a validation message, so stale hidden
values cannot be submitted. Backend `BigDecimal` validation remains authoritative.
The save button is enabled after script initialization when categories exist;
without JavaScript the form explains that it must be enabled to save. Refresh
open transaction forms to load the script. No dependencies, migrations,
environment variables or server cache changes are required.

The owner ID comes from `CurrentUserService`, never from form fields. Before
inserting, the service resolves the category with `findByIdAndUserId` and
rejects missing or foreign categories with HTTP 403. Those errors and invalid
pagination use the shared JSON error shape `code`, `message`, `timestamp` via
`TransactionExceptionHandler`. Other database failures remain failures.

Amounts use `BigDecimal`, must be positive, and accept up to 13 integer digits
and 2 decimal digits to match `DECIMAL(15,2)` without silent rounding. The date
and category are required; notes are optional and limited to 1000 characters.
Form validation errors render with `th:errors`, retaining the user's category
options. Submission includes a CSRF token and never binds transaction ID or
ownership from the request.

The dropdown initially loads at most 100 categories belonging to the user.
Its "Tải thêm danh mục" button calls the paginated DTO endpoint
`GET /transactions/categories?page=1` to append more options without losing
form values. Loading failures are visible and can be retried. The list resolves
category names in one user-scoped, bounded query for the current transaction
page. No new dependencies, schema changes, environment variables or cache
resets are required.

The transaction list also accepts optional `keyword`, `categoryId`, `startDate`
and `endDate` query parameters. A validated `TransactionFilterDTO` binds these
parameters so the controller signature stays short. The Bootstrap GET search
form keeps submitted values, resets pagination on a new search, and preserves
all filters in previous/next links. "Xóa bộ lọc" returns to the unfiltered list.
The owned-category dropdown supports loading more categories and retains a
selected category beyond its initial 100 options.

The service uses a paginated, parameterized repository query that always filters
by authenticated `userId`. Optional criteria are combined with AND. The keyword
is trimmed and matched as a literal substring of `note` using `LOCATE`, including
characters such as `%` and `_`; matching follows the MySQL column collation.
An omitted or blank keyword includes transactions whose note is null. Dates use
`yyyy-MM-dd`, are inclusive, and can be supplied independently. Dates must be in
MySQL's 1000–9999 year range and start cannot exceed end. Invalid filters return
HTTP 400 with the existing structured error response; a foreign category filter
returns no transactions and never exposes that category's name. The default
20-row page and date/ID descending ordering remain unchanged. There are no new
dependencies, schema migrations, environment variables or server cache settings.
Refresh open transaction lists to load the search form.

`GET /transactions/export` downloads `transactions.csv` using
`Content-Disposition: attachment; filename=transactions.csv` and
`Content-Type: text/csv;charset=UTF-8`. The transaction list includes an
"Xuất tất cả CSV" link. The export always contains all transactions belonging
to the authenticated user, including income and expenses across all dates;
list filters, page numbers and supplied owner IDs do not limit or change it.

The CSV columns are `Ngày`, `Danh mục`, `Số tiền`, `Ghi chú`. Dates use
`yyyy-MM-dd`, monetary values use `BigDecimal.toPlainString()`, and null notes
become empty fields. Records use CRLF; commas, quotes and embedded line breaks
are escaped following [RFC 4180](https://www.rfc-editor.org/rfc/rfc4180.html).
A UTF-8 BOM helps spreadsheet applications recognize Vietnamese text. Text
starting with formula-like characters (including after leading whitespace) is
prefixed with an apostrophe and quoted, following the text-escaping approach
described by [OWASP](https://owasp.org/www-community/attacks/CSV_Injection).
This affects only the downloaded representation, not the stored data.

The service reads DTO projections in slices of 100 rows within a read-only
transaction, ordered by date descending then ID descending. Both joined tables
are filtered by `user_id`; no unpaginated entity list or per-row category lookup
is used. The final CSV is assembled in memory before the response is sent, so
its memory use grows with the exported file size. Database failures remain errors
instead of producing a successful empty file. Responses use `Cache-Control:
no-store`. No dependencies, schema, environment or server cache settings change;
refresh the transaction list to see the export link.

## Budgets

Authenticated users open `GET /budgets` and create/update a limit through
`GET /budgets/new` and `POST /budgets`. Requests bind `categoryId`, `period`,
`type` and `limitAmount`. The form selects monthly or weekly budgets and explains
the corresponding period format. Reusing the same category, period and type
updates its existing limit; monthly and weekly budgets can coexist for a category.
Submitted ownership and IDs are ignored. All queries include the authenticated
user ID; foreign or missing categories return HTTP 403, and failed updates return
HTTP 409 rather than success.

`BudgetDTO.period` is `yyyy-MM` for `MONTHLY` (for example `2026-10`) or
`yyyy-Www` for `WEEKLY` (for example `2026-W43`). Years are 1000–9999.
`BudgetPeriod` validates the type/period pair, including whether ISO week 53
actually exists, before persistence. `@ValidBudgetPeriod` attaches input errors
to the period field. Limits remain positive `BigDecimal` values with at most
13 integer and 2 fractional digits. Invalid forms retain values and owned
category options. CSRF protection applies to submission.

The type dropdown switches between native `input type="month"` and
`input type="week"` controls using `/js/budget-period.js`. Only the active
control is visible, enabled and required, so exactly one canonical `period`
is submitted. Switching back preserves each control's previous selection.
The server also renders the correct active picker after validation errors.
The budget form contains no quick price suggestion buttons. Listings show
type labels `Tháng`/`Tuần` and periods such as `Tháng 10, 2026` or
`Tuần 43, 2026`; the DTO's `periodLabel` is presentation only and the stored
period stays `2026-10`/`2026-W43`. Refresh open budget pages to load this UI.
There are no schema, dependency, environment variable or server cache changes.

Weeks follow Java's [WeekFields.ISO](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/temporal/WeekFields.html)
and the [OpenJDK implementation](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/temporal/WeekFields.java):
Monday through Sunday, with at least four days in the first week of the ISO
week-based year. This is deliberately independent of locale. The week-based
year can differ from the calendar year: `2021-01-01` belongs to `2020-W53`,
covering `2020-12-28` through `2021-01-03`. Weekly ranges spanning months are
not restricted to either calendar month.

Flyway already has `V2__unique_budget_per_category_month.sql`, so the extension
uses `V3__update_budget_for_weekly.sql` rather than creating a duplicate V2.
The existing `month` is a DATE, not a VARCHAR. V3 converts it to a VARCHAR
`period`, preserves its `yyyy-MM` value and limit, assigns existing rows
`type=MONTHLY`, and replaces the old first-day check/unique index with a type
check and unique key on `(user_id, category_id, period, type)`. V1 and V2 remain
unchanged. Restart the application with the new code so Flyway applies V3;
old code using the removed `month` column is incompatible with the new schema.

`BudgetRepository.findByUserIdAndCategoryIdAndPeriodAndType` looks up one exact
budget. Budget writes lock the owned category to serialize creation and updates
with transaction changes. Listings remain paginated DTOs (20 rows by default,
maximum 100), sorted by canonical period descending then ID descending. The
category dropdown supports loading more options via `GET /budgets/categories`.

Creating, editing and deleting transactions check both monthly and weekly
budgets after the successful write. `checkBudgetAlert(userId, categoryId, occurredOn)`
uses the transaction's date, not the server date. Only owned `CHI` transactions
in the same category and inclusive period range contribute to each total.
Queries still use explicit `LocalDate startDate/endDate` bounds, without
`MONTH()` or `YEAR()` on the transaction column. At MySQL's storage boundaries,
the query is clipped to 1000-01-01/9999-12-31 when the ISO week extends outside
the representable transaction dates; the ISO period itself remains unchanged.

An edit checks the new periods and any different old category/type/period
combinations. Moving between weeks of the same month checks both weeks and the
month once; moving between months in the same ISO week checks both months and
the week once. Deletion retains the old category/date before removing the row
and checks the remaining expenses. Category locks use ascending ID order.

All three mutation service methods return an `Optional<String>` warning.
Income categories, missing budgets and totals at or below their limits do not
warn. Each exceeded limit contributes a message with the category, type,
period, exact total, limit and excess using `BigDecimal`. Multiple messages are
combined and passed to the existing warning toast along with the success flash.
Warnings preserve successful writes; actual database failures propagate and
roll back without a success flash. No new Maven dependencies, environment
variables or server cache changes are required. Refresh open budget forms to
load the new period/type controls.

## Dashboard

`GET /` displays total income, total expense and balance for the authenticated
user's current calendar month, plus a link to create a transaction. The month
and year come from `LocalDate.now()` in the application's JVM timezone. Request
parameters cannot override the report period or owner. The dashboard model
contains `DashboardDTO` monetary values, the month and the year; the shared
layout also receives the authenticated user's profile DTO.

The repository aggregates `THU` and `CHI` separately by transaction date,
filtering both transactions and joined categories by the same user ID.
All transaction aggregation queries accept explicit `LocalDate startDate` and
`LocalDate endDate` parameters and filter with `occurredOn >= :startDate` and
`occurredOn <= :endDate`. This includes dashboard income/expense totals,
category expense groups and their pagination count query, and budget alerts.
`TransactionService` computes the month's first and last date before calling
the repository: the dashboard uses the reporting month/year supplied
by its controller, and alerts use the transaction's `occurredOn` month. Repository
method names end in `DateRange`; callers must supply both inclusive bounds.
No `MONTH()` or `YEAR()` function wraps `occurred_on`, allowing the existing
`idx_transactions_user_occurred_on (user_id, occurred_on)` index to be considered
for date range access. This refactor requires no migration, dependency,
environment variable or cache changes.

A month with no matching transactions totals zero through SQL `COALESCE`; database
failures are not converted to zeros. The service uses a read-only transaction
for the two sums and `BigDecimal` subtraction for the balance. Stored totals
retain two decimal places; the HTML displays whole dong with comma grouping
and a `₫` suffix, including zero and negative balances.
The Bootstrap cards stack on small screens. No new dependencies, schema
migrations, environment variables or cache resets are required.

The dashboard also displays a category expense pie chart using
[Chart.js 4.5.1](https://github.com/chartjs/Chart.js/releases/tag/v4.5.1), loaded
from jsDelivr as a UMD script. The browser must be able to reach this CDN.
`dashboard-chart.js` fetches `GET /api/dashboard/chart` with the logged-in session.
Each JSON item contains `categoryName` and `totalAmount`. Only `CHI` transactions
in the server's current calendar month are included; both the transactions and
joined categories are filtered by the authenticated user ID. Request parameters
cannot change the owner or reporting month. Categories with the same name are
grouped separately by category ID.

The endpoint uses zero-based `page` and `size` (default and maximum 100), returning
a JSON array and an `X-Has-Next` response header. The chart fetches all pages before
drawing, so categories beyond the first page are included in the proportions.
SQL aggregation and Java DTOs use `BigDecimal`; JavaScript numbers are used only
to draw the chart and format its tooltips. The chart shows amounts and percentages
when a segment is hovered or tapped. Report responses use `Cache-Control: no-store`.
Invalid pagination and unavailable accounts return the shared JSON error shape;
database errors return HTTP 500 rather than empty results.

A successful empty report displays a message that the month has no expenses.
API, session, or chart library failures display a separate error with a retry
button; partial paginated results are never drawn. No Maven dependencies, schema
migrations or environment variables are added. Refresh an open dashboard to load
the new scripts; no server cache reset is required.

## Global exception handling

`common.exception.GlobalExceptionHandler` handles `IllegalArgumentException`
and Spring Security's `AccessDeniedException` raised during MVC request handling.
They render `templates/error/error.html` with HTTP 400 and 403 respectively.
The model uses the shared `ErrorResponse` structure (`code`, `message`, `timestamp`);
the Bootstrap page displays only a fixed friendly message and navigation links.
Raw exception messages and stack traces are not added to the page model.

Existing registration validation and feature-specific JSON error handlers have
explicit priority over the global advice, including matches on nested causes.
Security filter failures, such as missing CSRF tokens, continue through
Spring Security's existing handlers; anonymous protected requests still redirect
to login. The advice does not convert unexpected server failures to client errors.
No database migrations, dependencies, environment variables or server cache resets
are required. Refresh open pages to load the new error template when an error occurs.

## Interface

`CurrencyModelAdvice` makes a shared `VietnameseCurrencyFormatter` available
as `vnCurrency` in Thymeleaf views. Use
`th:text="${vnCurrency.format(amount)}"` for monetary text. It delegates to
Thymeleaf's `Numbers.formatDecimal(amount, 1, "COMMA", 0, "POINT")` and appends
` ₫`, for example `50,000 ₫`. Dashboard totals, transaction lists/details and
budget limits all use this helper. Explicit separators make the format
independent of the browser's requested locale. Missing monetary values remain
errors instead of being displayed as zero.

This is display rounding only: amounts remain `BigDecimal`, forms retain
their decimal number values, and CSV/API data retain their original precision.
The chart tooltip uses the same grouping, suffix and half-even rounding.
Refresh open pages to load the updated templates and chart script. No new
dependencies, database migrations, environment variables or server cache
changes are required.

All pages use the shared Thymeleaf fragment
`templates/layout/main-layout.html :: layout(pageTitle, activePage, content)`.
Child templates contain only their content fragment and call the layout with
`th:replace`; page-specific scripts stay with their content. Add global styles,
scripts and navigation in the layout rather than duplicating document markup.
The active page keys are `dashboard`, `transactions`, `categories`, `budgets`
and `profile`; forms keep their feature's navigation item active.

The authenticated layout has a fixed left sidebar on desktop, a Bootstrap
offcanvas menu below 992px, and a content area on the right. The sidebar contains
the logo, display name, five navigation links and the CSRF-protected POST logout
form at the bottom. `LayoutModelAdvice` resolves the profile through `UserService`
using the authenticated principal; the profile page uses its existing profile
DTO. Anonymous requests do not perform a profile lookup. Profile lookup failures
remain failures. Login, registration and errors share the document resources;
anonymous visitors see a centered card without authenticated navigation.

Bootstrap 5.3.8 CSS and its JavaScript bundle load through jsDelivr with integrity
hashes. The bundle powers the mobile menu. Local `/css/app.css` supplies the shared
green palette, spacing, forms, tables, dashboard cards and responsive rules.
The interface includes a keyboard skip link, visible focus states, accessible
menu controls and reduced-motion support. Browsers need CDN access for Bootstrap,
Chart.js and SweetAlert2. Refresh open pages to load the updated templates and stylesheet;
there are no new Maven dependencies, environment variables, schema changes or
server cache settings.

Transaction amounts remain positive `BigDecimal` values. The list colors them
red for `CHI` and green for `THU` using the actual category type from the same
user-scoped category query that resolves category names. Color is never inferred
from the amount's sign. Validation, CSRF, ownership checks and pagination remain
active.

## Toast notifications

The master layout loads [SweetAlert2 11](https://sweetalert2.github.io/) from
jsDelivr and `/js/toast-notifications.js` with deferred scripts in dependency
order. Notifications use `toast: true`, `position: "top-end"`, a 3000 ms timer
and a progress bar, without a confirmation button or backdrop. A close button
allows early dismissal. Multiple messages display sequentially.

Successful registration, password changes, category creation, transaction
creation/update/deletion and budget saves use `RedirectAttributes` success
flash attributes through `FlashMessageUtil`. Registration now redirects to
`/login` and password changes to `/profile`, without a `?success` marker.
Flash text is consumed on the next matching request and is absent after a
refresh. Validation failures keep their form and field errors; failed service
calls retain existing exception handling and never add a success flash.

The layout uses `th:if` to support `success`, string-valued `warning` and `error` flash
attributes, plus `?success=message` and `?error=message` URL parameters. Empty
URL flags have friendly defaults, including the existing login, password-change
and `?logout` notifications. Flash text takes priority over URL text of the same
type. URL parameters remain in the address bar and therefore display again on
refresh; use flash attributes for notifications following application actions.

Messages are escaped with `th:text` and passed to SweetAlert2 as `titleText`,
never HTML or executable inline JavaScript. Structured error-page DTOs are not
treated as error flash text. Browsers need access to the SweetAlert2 CDN.
Budget alerts use SweetAlert2's yellow `warning` icon and appear before the
success toast. Warning flashes are consumed once and disappear after refresh.
Refresh open pages to load the shared script; no Maven/npm dependencies,
database migrations, environment variables or server cache resets are added.

## Verification

Run `./mvnw verify` (`.\mvnw.cmd verify` on Windows). The repository integration
tests use MySQL from `application.properties`, run Flyway and Hibernate schema
validation, and roll back their test data. They verify isolation between two
users, exact monetary values, scoped deletions and category ownership constraints.
They require an available MySQL server and the `expense_tracker` database.
Use a separate test database by setting `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` when running tests.

The transaction dropdown's JavaScript tests use Node's built-in test runner,
without npm dependencies. With Node 24, run
`node --test --test-isolation=none src/test/js/transaction-form.test.cjs`.
Run the formatted amount input tests with
`node --test --test-isolation=none src/test/js/transaction-amount.test.cjs`.
Run the budget month/week picker tests with
`node --test --test-isolation=none src/test/js/budget-period.test.cjs`.
Run the chart tests with
`node --test --test-isolation=none src/test/js/dashboard-chart.test.cjs`.
Run notification tests with
`node --test --test-isolation=none src/test/js/toast-notifications.test.cjs`.
