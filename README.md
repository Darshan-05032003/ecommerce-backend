# E-commerce Backend

A REST API for a small online shop built with Spring Boot and MySQL. It models users, products, shopping carts and orders with JPA entities and exposes versioned endpoints (`/api/v1`) for catalogue browsing, cart management and order placement. Authentication uses short-lived JWT access tokens with rotating refresh tokens; authorization is role based, and every customer resource is addressed through `/api/v1/me/**` so the caller's identity always comes from the token, never from the URL.

## Tech Stack

| Layer        | Technology                                   |
| ------------ | -------------------------------------------- |
| Language     | Java 21                                      |
| Framework    | Spring Boot 4.1                              |
| Persistence  | Spring Data JPA, Hibernate, MySQL, Flyway    |
| Caching      | Spring Cache, Redis                          |
| Mapping      | MapStruct                                    |
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

## Product Catalogue

```
GET /api/v1/products?categoryId=2&minPrice=1000&maxPrice=5000&q=key&inStock=true&page=0&size=20&sort=price,desc
```

- Every filter is optional and they are combined with AND. `q` is a case-insensitive search in the product name; `%` and `_` in it are matched literally. `inStock=true` keeps products with stock above zero.
- `size` defaults to 20 and may be at most 100; `page` starts at 0. Larger sizes and negative pages are rejected with `400`.
- `sort` is `property` or `property,asc|desc`, where the property is one of `name`, `price` or `createdAt` (default `name`). Any other property is a `400`. The id is always added as a tie breaker so that pages are stable.
- The response is a page object with `content`, `page`, `size`, `totalElements` and `totalPages`, not Spring's internal `Page` JSON.
- Products are never physically deleted. `DELETE /api/v1/admin/products/{id}` sets `active` to false: the product disappears from `GET /api/v1/products` and `GET /api/v1/products/{id}` returns `404`, while the admin endpoints still list and return it and an update with `active: true` restores it. Order lines that reference the product keep working.
- Each product response carries a `version`. `PUT /api/v1/admin/products/{id}` must send back the version that was read; if another admin saved in between, the request fails with `409` and nothing is changed.
- The product page is loaded with its categories in one joined query plus the count query, whatever the page size. A test counts the statements with Hibernate statistics.

### Caching

`GET /api/v1/products/{id}` and `GET /api/v1/categories` are cached in Redis (cache-aside through Spring Cache). Values are stored as JSON, each cache with its own time to live (`CACHE_TTL_PRODUCTS`, default 10 minutes; `CACHE_TTL_CATEGORIES`, default 1 hour).

- An admin product update replaces the cached entry, or removes it when the product becomes inactive. Soft delete and stock changes remove it. Any category change clears the category list, and a category rename also clears cached products, which embed the category name.
- Cache updates run after the database transaction commits, so a rolled-back change never reaches the cache.
- Product list pages are not cached: each combination of filters, sort and page would be a separate entry with a low hit rate, and a single product change would make an unknown number of them stale.
- The time to live bounds how long a change made outside these endpoints (for example directly in the database) can stay invisible.

## Shopping Cart

```
GET    /api/v1/me/cart
POST   /api/v1/me/cart/items               { "productId": 12, "quantity": 2 }
PATCH  /api/v1/me/cart/items/{productId}   { "quantity": 3 }
DELETE /api/v1/me/cart/items/{productId}   -> 204
DELETE /api/v1/me/cart                     -> 204
```

```json
{
  "lines": [
    { "productId": 12, "sku": "BK-JAVA-001", "name": "Effective Java", "unitPrice": 19.99, "quantity": 2, "lineTotal": 39.98 }
  ],
  "totalQuantity": 2,
  "total": 39.98
}
```

- `GET`, `POST` and `PATCH` return the whole cart. A cart holds one line per product, and lines are addressed by product id.
- A line holds 1 to 10 units. A request quantity outside that range is a `400` validation error. Adding a product that is already in the cart increases that line's quantity; if the result would exceed 10 the request fails with `422` and the line is unchanged.
- The product must exist (`404`) and be active, and the line quantity may not exceed the current stock. Both rules are business rule failures: `422` with an `errorCode` of `PRODUCT_UNAVAILABLE`, `INSUFFICIENT_STOCK` or `CART_LINE_LIMIT_EXCEEDED`. Adding to the cart does not reserve stock; stock is checked again and taken when the order is placed.
- Lines are priced with the product's current price every time the cart is read, so a price change shows up in the cart until an order is placed. Line totals are rounded to two decimals with `HALF_EVEN`, and the cart total is the sum of the rounded lines.
- Removing a product that is not in the cart still returns `204`, so a repeated delete is harmless.
- Two simultaneous adds of the same product (a double click) end in one line with both quantities: the existing line is read with `SELECT ... FOR UPDATE`, and a request that loses the race to insert the first line is retried in a new transaction, where it merges into the line the other request created.
- The cart is loaded with its products in one joined query whatever the number of lines. A test counts the statements with Hibernate statistics.

## Running Locally

```
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The compose stack publishes MySQL on `localhost:3307`, Redis on `localhost:6380` and MailHog on `localhost:1025` (SMTP) and `http://localhost:8025` (web UI). The application listens on port 8081.

In the `dev` profile the application creates an admin account on first start. The email and password come from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` (defaults in `.env.example`); the password is BCrypt-hashed at startup and never stored in clear text. Two categories and five products are added when the catalogue is empty. No seed data other than the two roles is created in any other profile.

## Database

The schema is owned by Flyway migrations in `src/main/resources/db/migration` and applied on startup in every profile. `V1` creates the tables with primary and foreign keys, `NOT NULL`, `UNIQUE` (user email, product SKU, one cart line per user and product, order number) and `CHECK` constraints (non-negative price and stock, positive quantities), plus indexes for the query paths the application uses. `V2` inserts the two roles, `V3` adds the refresh token table and `V4` makes the product category mandatory and indexes active products by category. Hibernate runs with `ddl-auto=validate`, so a mismatch between the entities and the migrated schema fails startup instead of silently altering tables, and `open-in-view` is off, so every lazy association is loaded inside a service transaction.

Every entity extends `BaseEntity`, which carries the id, `createdAt`, `updatedAt`, `createdBy` and `lastModifiedBy`. The timestamps come from the application `Clock` and the auditor is the authenticated user's email, or `system` when no user is authenticated. `Product` and `Order` carry a `@Version` column for optimistic locking. Order numbers have the form `ORD-2026-000123` and are taken from a per-year counter table in its own short transaction.

## Build and Test

```
./mvnw clean verify
```

Repository tests start a MySQL 8.4 container with Testcontainers and run the Flyway migrations against it; full integration tests also start a Redis 7 container for the caches. Docker must be running. The same command runs in GitHub Actions on every push to `master` and on every pull request.

## Errors and Correlation IDs

Every error response is an RFC 9457 problem detail (`application/problem+json`). Validation failures list the rejected fields; business rule failures (`422`) carry a stable `errorCode` that clients can branch on; unexpected errors return a generic message and are logged with their stack trace on the server.

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
| `GET`  | `/api/v1/products` | public | List active products with filters, paging and sorting |
| `GET`  | `/api/v1/products/{id}` | public | Get an active product (cached) |
| `GET`  | `/api/v1/categories` | public | List categories (cached) |
| `GET`  | `/api/v1/categories/{id}` | public | Get a category |
| `GET`  | `/api/v1/me` | CUSTOMER, ADMIN | My profile |
| `GET`  | `/api/v1/me/cart` | CUSTOMER, ADMIN | My cart with line totals and the cart total |
| `POST` | `/api/v1/me/cart/items` | CUSTOMER, ADMIN | Add a product, or increase its quantity if it is already in the cart |
| `PATCH` | `/api/v1/me/cart/items/{productId}` | CUSTOMER, ADMIN | Set the quantity of a product in my cart |
| `DELETE` | `/api/v1/me/cart/items/{productId}` | CUSTOMER, ADMIN | Remove a product from my cart |
| `DELETE` | `/api/v1/me/cart` | CUSTOMER, ADMIN | Clear my cart |
| `GET`  | `/api/v1/me/orders` | CUSTOMER, ADMIN | List my orders |
| `POST` | `/api/v1/me/orders` | CUSTOMER, ADMIN | Place an order from my cart |
| `GET`  | `/api/v1/admin/users` | ADMIN | List users |
| `GET`  | `/api/v1/admin/users/{id}` | ADMIN | Get any user |
| `GET`  | `/api/v1/admin/users/{id}/orders` | ADMIN | List any user's orders |
| `GET`  | `/api/v1/admin/products` | ADMIN | List all products, including inactive ones |
| `GET`  | `/api/v1/admin/products/{id}` | ADMIN | Get any product, including inactive ones |
| `POST` | `/api/v1/admin/products` | ADMIN | Create a product; requires a unique `sku` |
| `PUT`  | `/api/v1/admin/products/{id}` | ADMIN | Update a product; requires the current `version` |
| `DELETE` | `/api/v1/admin/products/{id}` | ADMIN | Soft-delete a product |
| `POST` | `/api/v1/admin/categories` | ADMIN | Create a category |
| `PUT`  | `/api/v1/admin/categories/{id}` | ADMIN | Rename or describe a category |
| `DELETE` | `/api/v1/admin/categories/{id}` | ADMIN | Delete a category that has no products |

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
├── cart/        # cart lines, cart rules and totals, the /me/cart endpoints
├── order/       # orders, order items, /me/orders and the admin order view
└── common/
    ├── config/      # typed settings, clock, JPA auditing, Redis caches, development seed data
    ├── exception/   # problem detail factory and global error handling
    ├── logging/     # masking helpers for log output
    ├── money/       # scale and rounding for amounts
    ├── persistence/ # base entity and auditor lookup
    ├── security/    # filter chain, CORS, principal, 401/403 problem responses
    ├── validation/  # custom Bean Validation constraints
    └── web/         # correlation id filter, paging parameters, sort whitelist, page response
```
