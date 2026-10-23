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
parameters cannot override the report period or owner. The model contains only
`DashboardDTO` monetary values, the month and the year.

The repository aggregates `THU` and `CHI` separately by transaction date,
filtering both transactions and joined categories by the same user ID. A month
with no matching transactions totals zero through SQL `COALESCE`; database
failures are not converted to zeros. The service uses a read-only transaction
for the two sums and `BigDecimal` subtraction for the balance. All displayed
totals retain two decimal places, including zero and negative balances.
The Bootstrap cards stack on small screens. No new dependencies, schema
migrations, environment variables or cache resets are required.

## Interface

All nine Thymeleaf pages use Bootstrap 5.3.8 CSS through jsDelivr, pinned with
the integrity hash from the [official Bootstrap documentation](https://getbootstrap.com/docs/5.3/getting-started/introduction/).
Login and registration use centered responsive cards. Forms, buttons, validation
feedback, bordered tables and pagination use Bootstrap classes. The old local
`auth.css` and `category.css` stylesheets have been removed; browsers need access
to the CDN to load the styles. Refresh open pages to load the updated templates.
The interface adds no Maven dependencies, environment variables or server cache settings.

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
