# E-commerce Backend

A REST API for a small online shop built with Spring Boot and MySQL. It models users, products, shopping carts and orders with JPA entities and exposes versioned endpoints (`/api/v1`) for catalogue browsing, cart management and order placement. Authentication uses short-lived JWT access tokens with rotating refresh tokens; authorization is role based, and every customer resource is addressed through `/api/v1/me/**` so the caller's identity always comes from the token, never from the URL.

## Tech Stack

| Layer        | Technology                                   |
| ------------ | -------------------------------------------- |
| Language     | Java 21                                      |
| Framework    | Spring Boot 4.1                              |
| Persistence  | Spring Data JPA, Hibernate, MySQL, Flyway    |
| Security     | Spring Security, JWT (jjwt), Bucket4j        |
| Validation   | Bean Validation (Jakarta)                    |
| API docs     | springdoc-openapi (Swagger UI)               |
| Testing      | JUnit 5, Mockito, AssertJ, Testcontainers    |
| Build        | Maven (wrapper included), GitHub Actions     |

## Configuration

Settings are read from environment variables with local defaults (see `.env.example` for the full list) and bound to validated `@ConfigurationProperties` records under the `app.*` prefix. Three profiles exist:

| Profile | Purpose |
| ------- | ------- |
| `dev`   | Local development against the `docker-compose.yml` stack. Provides a development-only JWT secret and seeds an admin account and a small catalogue. |
| `test`  | Used by the test suite. The database is a MySQL container started by Testcontainers. |
| `prod`  | No defaults for secrets. The application refuses to start if `JWT_SECRET` is missing or shorter than 32 characters. |

## Authentication

```
POST /api/v1/auth/register   -> 201, the new customer profile
POST /api/v1/auth/login      -> 200, { accessToken, refreshToken, tokenType: "Bearer", expiresIn: 900 }
GET  /api/v1/me              with  Authorization: Bearer <accessToken>
POST /api/v1/auth/refresh    { refreshToken } -> new access token and a new refresh token
POST /api/v1/auth/logout     { refreshToken } -> 204
```

- Passwords must be at least 8 characters with an upper case letter, a lower case letter, a digit and a special character. They are stored as BCrypt hashes.
- Login with an unknown email and login with a wrong password return the same `401` body, so the endpoint cannot be used to find out which addresses have an account.
- Access tokens are HS256 JWTs signed with `JWT_SECRET` and expire after 15 minutes (`JWT_ACCESS_TOKEN_TTL`). They are not stored server side.
- Refresh tokens are random 256-bit strings valid for 7 days (`JWT_REFRESH_TOKEN_TTL`). Only their SHA-256 hash is stored, together with a family id that groups every token descending from one login. Each refresh rotates the token: the presented one is retired and a successor in the same family is returned. Presenting a retired token again is treated as theft and revokes the whole family, so the legitimate client is logged out too and must sign in again. Logout revokes the family.
- Login is rate limited to 5 attempts per minute per client address and email. Further attempts get `429` with a `Retry-After` header.
- Sessions are stateless and CSRF protection is disabled because no cookie carries credentials. Browser origins allowed to call the API are configured with `CORS_ALLOWED_ORIGINS`.
- Missing or invalid tokens produce a `401` and insufficient roles a `403`, both in the same problem detail format as every other error.

## Running Locally

```
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The compose stack publishes MySQL on `localhost:3307`, Redis on `localhost:6380` and MailHog on `localhost:1025` (SMTP) and `http://localhost:8025` (web UI). The application listens on port 8081.

In the `dev` profile the application creates an admin account on first start. The email and password come from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` (defaults in `.env.example`); the password is BCrypt-hashed at startup and never stored in clear text. Two categories and five products are added when the catalogue is empty. No seed data other than the two roles is created in any other profile.

## Database

The schema is owned by Flyway migrations in `src/main/resources/db/migration` and applied on startup in every profile. `V1` creates the tables with primary and foreign keys, `NOT NULL`, `UNIQUE` (user email, product SKU, one cart line per user and product, order number) and `CHECK` constraints (non-negative price and stock, positive quantities), plus indexes for the query paths the application uses. `V2` inserts the two roles and `V3` adds the refresh token table. Hibernate runs with `ddl-auto=validate`, so a mismatch between the entities and the migrated schema fails startup instead of silently altering tables, and `open-in-view` is off, so every lazy association is loaded inside a service transaction.

Every entity extends `BaseEntity`, which carries the id, `createdAt`, `updatedAt`, `createdBy` and `lastModifiedBy`. The timestamps come from the application `Clock` and the auditor is the authenticated user's email, or `system` when no user is authenticated. `Product` and `Order` carry a `@Version` column for optimistic locking. Order numbers have the form `ORD-2026-000123` and are taken from a per-year counter table in its own short transaction.

## Build and Test

```
./mvnw clean verify
```

Integration and repository tests start a MySQL 8.4 container with Testcontainers and run the Flyway migrations against it, so Docker must be running. The same command runs in GitHub Actions on every push to `master` and on every pull request.

## Errors and Correlation IDs

Every error response is an RFC 9457 problem detail (`application/problem+json`). Validation failures list the rejected fields; unexpected errors return a generic message and are logged with their stack trace on the server.

```json
{
  "status": 404,
  "title": "Not Found",
  "detail": "Product not found with id 999",
  "instance": "/api/v1/products/999",
  "correlationId": "3c2d754f-6f0f-42d8-8240-5acc50746cd5",
  "timestamp": "2026-10-22T17:53:40.442Z"
}
```

Each request gets a correlation id: the value of the `X-Correlation-Id` request header if one is sent, otherwise a generated UUID. It is returned in the `X-Correlation-Id` response header, included in every error body and printed in every log line written while handling the request.

## API Overview

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/v1/auth/register` | public | Register a customer account |
| `POST` | `/api/v1/auth/login` | public | Exchange credentials for an access and a refresh token |
| `POST` | `/api/v1/auth/refresh` | public | Rotate the refresh token, get a new access token |
| `POST` | `/api/v1/auth/logout` | public | Revoke the refresh token and its family |
| `GET`  | `/api/v1/products` | public | List products (optional `?q=` search) |
| `GET`  | `/api/v1/products/{id}` | public | Get product by id |
| `GET`  | `/api/v1/me` | CUSTOMER, ADMIN | My profile |
| `GET`  | `/api/v1/me/cart` | CUSTOMER, ADMIN | My cart |
| `POST` | `/api/v1/me/cart?productId=&quantity=` | CUSTOMER, ADMIN | Add an item to my cart |
| `DELETE` | `/api/v1/me/cart` | CUSTOMER, ADMIN | Clear my cart |
| `GET`  | `/api/v1/me/orders` | CUSTOMER, ADMIN | List my orders |
| `POST` | `/api/v1/me/orders` | CUSTOMER, ADMIN | Place an order from my cart |
| `GET`  | `/api/v1/admin/users` | ADMIN | List users |
| `GET`  | `/api/v1/admin/users/{id}` | ADMIN | Get any user |
| `GET`  | `/api/v1/admin/users/{id}/orders` | ADMIN | List any user's orders |
| `POST` | `/api/v1/admin/products` | ADMIN | Create a product; requires a unique `sku` |
| `PUT`  | `/api/v1/admin/products/{id}` | ADMIN | Update a product |
| `DELETE` | `/api/v1/admin/products/{id}` | ADMIN | Delete a product |

Every registered account has the CUSTOMER role. Everything under `/api/v1/admin/**` requires ADMIN both in the security filter chain and through `@PreAuthorize` on the controllers.

Swagger UI is available at `/swagger-ui.html` when the application is running.

## Project Structure

The code is organised by feature rather than by technical layer.

```
src/main/java/com/darshan/ecommerce/
├── EcommerceApplication.java
├── auth/        # register, login, refresh and logout; JWT and refresh token services; login rate limiter
├── user/        # user and role entities, profile (/me) and admin user endpoints
├── category/    # product categories
├── product/     # product entity, public catalogue and admin product endpoints
├── cart/        # cart items and the /me/cart endpoints
├── order/       # orders, order items, /me/orders and the admin order view
└── common/
    ├── config/      # typed settings, clock, JPA auditing, development seed data
    ├── exception/   # problem detail factory and global error handling
    ├── logging/     # masking helpers for log output
    ├── persistence/ # base entity and auditor lookup
    ├── security/    # filter chain, CORS, principal, 401/403 problem responses
    ├── validation/  # custom Bean Validation constraints
    └── web/         # correlation id filter
```
