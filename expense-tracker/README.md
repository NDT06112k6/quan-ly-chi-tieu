# Expense Tracker

## Persistence

Entities and repositories are grouped into the `user`, `category`, `transaction`
and `budget` packages. Monetary values use `BigDecimal` and map to
`DECIMAL(15,2)`. Budget months use `LocalDate` and must be the first calendar day
of the month. Creation timestamps are initialized before the first insert.

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
`/login?success` after the account is saved. The form validates email (up to
255 characters), display name (up to 100 characters), and matching passwords
(8–72 characters and at most 72 UTF-8 bytes, as required by BCrypt). New
accounts receive role `USER` and are enabled. Roles and account status are
never bound from the registration form.

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
success redirects to `/profile?success`.

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

## Transactions

Authenticated users can list transactions at `GET /transactions?page=0`, open
`GET /transactions/new`, and submit `POST /transactions`. Creation redirects
to `/transactions`. The list contains DTOs, shows category names, and has 20
rows per page, ordered by date descending and then ID descending. The service
also accepts a page size from 1 to 100. Invalid pagination returns HTTP 400.
The table columns are date, category, amount and note; previous/next buttons
follow the Spring Data `Page` state. The creation form posts to `/transactions`
with number/date inputs, a category dropdown and a note field.

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

Authenticated users can open `GET /budgets`, create or update a budget through
`GET /budgets/new` and `POST /budgets`, and navigate there from the dashboard.
Choose the same category and month to update its existing limit; a different
month creates a new budget. Submitted IDs and ownership fields are ignored.
The category must belong to the authenticated user; missing or foreign categories
return HTTP 403 through centralized error handling with `code`, `message` and
`timestamp`. Failed updates return HTTP 409 rather than reporting success.

`BudgetDTO.month` and the repository's String overload accept `yyyy-MM` from
1000-01 through 9999-12. They map to the first day of that month in the existing
MySQL DATE column. Limits use positive `BigDecimal` values with at most 13 integer
digits and 2 decimal digits. Invalid forms retain owned category options and
display `th:errors`. CSRF protection applies to submission.

The list and `getBudgetsByUserId` return DTO pages, defaulting to 20 rows and
capping size at 100, ordered by month descending then ID descending. All reads
and updates include the owner ID. The category dropdown initially loads 100
options and can load more through `GET /budgets/categories?page=1`. Transactions
and budgets share the scoped category view helper and `/js/category-options.js`.
Refresh open forms to load the updated script path; no server cache reset is needed.

Flyway migration `V2__unique_budget_per_category_month.sql` adds a unique key on
`(user_id, category_id, month)`. Existing duplicate keys cause migration to fail
without deleting or merging data; resolve those records explicitly before retrying.
Budget writes lock the owned category within their transaction to serialize
concurrent creation and updates. The database unique key also enforces the rule.
No Maven dependencies or environment variables are added; Hibernate stays in
`validate` mode.

## Dashboard

`GET /` displays total income, total expense and balance for the authenticated
user's current calendar month, plus a link to create a transaction. The month
and year come from `LocalDate.now()` in the application's JVM timezone. Request
parameters cannot override the report period or owner. The dashboard model
contains `DashboardDTO` monetary values, the month and the year; the shared
layout also receives the authenticated user's profile DTO.

The repository aggregates `THU` and `CHI` separately by transaction date,
filtering both transactions and joined categories by the same user ID. A month
with no matching transactions totals zero through SQL `COALESCE`; database
failures are not converted to zeros. The service uses a read-only transaction
for the two sums and `BigDecimal` subtraction for the balance. All displayed
totals retain two decimal places, including zero and negative balances.
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

All eleven pages use the shared Thymeleaf fragment
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
menu controls and reduced-motion support. Browsers need CDN access for Bootstrap
and Chart.js. Refresh open pages to load the updated templates and stylesheet;
there are no new Maven dependencies, environment variables, schema changes or
server cache settings.

Transaction amounts remain positive `BigDecimal` values. The list colors them
red for `CHI` and green for `THU` using the actual category type from the same
user-scoped category query that resolves category names. Color is never inferred
from the amount's sign. Validation, CSRF, ownership checks and pagination remain
active.

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
Run the chart tests with
`node --test --test-isolation=none src/test/js/dashboard-chart.test.cjs`.
