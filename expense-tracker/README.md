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
`templates/index.html`. The home page displays the welcome message and a
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

## Verification

Run `./mvnw verify` (`.\mvnw.cmd verify` on Windows). The repository integration
tests use MySQL from `application.properties`, run Flyway and Hibernate schema
validation, and roll back their test data. They verify isolation between two
users, exact monetary values, scoped deletions and category ownership constraints.
They require an available MySQL server and the `expense_tracker` database.
Use a separate test database by setting `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` when running tests.
