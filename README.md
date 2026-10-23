# E-commerce Backend

A REST API for a small online shop built with Spring Boot and MySQL. It models users, products, shopping carts and orders with JPA entities and exposes versioned endpoints (`/api/v1`) for catalogue browsing, cart management and order placement. Authentication uses short-lived JWT access tokens with rotating refresh tokens; authorization is role based, and every customer resource is addressed through `/api/v1/me/**` so the caller's identity always comes from the token, never from the URL.

## Tech Stack

| Layer        | Technology                                   |
| ------------ | -------------------------------------------- |
| Language     | Java 21                                      |
| Framework    | Spring Boot 4.1                              |
| Persistence  | Spring Data JPA, Hibernate, MySQL, Flyway    |
| Caching      | Spring Cache, Redis                          |
| Messaging    | Spring application events, JavaMail (MailHog in development) |
| Mapping      | MapStruct                                    |
| Security     | Spring Security, JWT (jjwt), Bucket4j        |
| Validation   | Bean Validation (Jakarta)                    |
| API docs     | springdoc-openapi (Swagger UI)               |
| Observability | Spring Boot Actuator, Micrometer, Prometheus format, ECS JSON logs |
| Testing      | JUnit 5, Mockito, AssertJ, Testcontainers    |
| Build        | Maven (wrapper included), GitHub Actions     |

## Configuration

Settings are read from environment variables with local defaults (see `.env.example` for the full list) and bound to validated `@ConfigurationProperties` records under the `app.*` prefix. Three profiles exist:

| Profile | Purpose |
| ------- | ------- |
| `dev`   | Local development against the `docker-compose.yml` stack. Provides a development-only JWT secret and seeds an admin account and a small catalogue. |
| `test`  | Used by the test suite. The database is a MySQL container started by Testcontainers. |
| `prod`  | No defaults for secrets. The application refuses to start if `JWT_SECRET` is missing or shorter than 32 characters. Logs are written as one ECS JSON object per line. |

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

## Orders

```
POST  /api/v1/me/orders                  Idempotency-Key: <client generated, e.g. a UUID>
                                         { "shippingAddress": "221B Baker Street, London" }
GET   /api/v1/me/orders?page=0&size=20&sort=createdAt,desc
GET   /api/v1/me/orders/{id}
POST  /api/v1/me/orders/{id}/cancel
PATCH /api/v1/admin/orders/{id}/status   { "status": "SHIPPED", "reason": "Handed to courier" }
```

- Placing an order turns the whole cart into one order with status `CREATED` and an order number such as `ORD-2026-000123`. Each line keeps the unit price at the moment of ordering and its line total (rounded to two decimals with `HALF_EVEN`); the order total is the sum of the line totals. The ordered lines are removed from the cart.
- Placement is one database transaction. Each line takes its stock with a single conditional update (`stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ? AND active = true`); if any line affects no row the whole order is rolled back, nothing is reserved and the cart stays as it was. The error is `422` with `errorCode` `OUT_OF_STOCK`, or `PRODUCT_UNAVAILABLE` for a product withdrawn after it was added to the cart. An empty cart is `422` `CART_EMPTY`.
- `Idempotency-Key` is required (`400` `IDEMPOTENCY_KEY_MISSING`, or `IDEMPOTENCY_KEY_INVALID` for anything other than 1 to 100 letters, digits and `.`, `_`, `:`, `-`). Keys are scoped to the user. The key, a SHA-256 hash of the request body and the response are stored with the order. Repeating the request with the same key returns the stored response (same status and body) with an `Idempotent-Replayed: true` header and places nothing. The same key with a different body is `422` `IDEMPOTENCY_KEY_REUSED`. Concurrent requests with the same key create exactly one order: the key row is inserted first in the order transaction, so the other requests wait on its unique index and then replay the stored response. A request that fails stores nothing, so its key can be used again. Keys older than `ORDER_IDEMPOTENCY_KEY_RETENTION` (default 24 hours) are deleted by a scheduled job (`ORDER_IDEMPOTENCY_CLEANUP_CRON`, default hourly at minute 15).
- Status lifecycle: `CREATED` to `PAID` to `SHIPPED` to `DELIVERED`, and `CANCELLED` from `CREATED` or `PAID` only. Any other change is `409`. Every change, including the creation, is written to `order_status_history` with the previous and new status, who made it, when, and an optional reason. The order detail endpoints return this history.
- A customer can cancel their own order before it ships; cancelling (by the customer or an admin) returns the stock of every line in the same transaction. Orders carry a version column, so two concurrent changes of the same order cannot both succeed.
- Another user's order answers `404`, the same as an order that does not exist.
- Order lists are paged (`size` up to 100, `sort` by `createdAt` or `totalAmount`, newest first by default) and load their lines in a fixed number of queries: a page of order ids, the count, then the orders with their lines and products for those ids. A test counts the statements with Hibernate statistics. Admins can list all orders, optionally filtered by `status`.

## Payments

```
POST /api/v1/me/orders/{id}/payments   { "method": "CARD", "paymentToken": "tok_visa" }
                                       { "method": "UPI", "upiId": "name@okbank" }
                                       { "method": "COD" }
```

- Only the owner can pay for an order (another user's order is `404`), and only while it is `CREATED` (otherwise `409`). A successful payment returns `201` with the payment, marks the order `PAID` and adds a status history row.
- Each method is a `PaymentProcessor` implementation (card, UPI, cash on delivery). A registry built from all processor beans picks the one for the requested method; the application refuses to start if two processors claim the same method. Card and UPI payments go through a `PaymentGateway`. Cash on delivery confirms the order without calling the gateway: the cash is collected by the courier at delivery, so there is nothing to charge or refund before shipping.
- The API never receives card numbers. A card payment sends a token from the provider's client SDK (`tok_...`), a UPI payment the customer's UPI address. A missing or malformed value is `400` (`PAYMENT_TOKEN_INVALID`, `UPI_ID_INVALID`).
- Every attempt is stored in `payments` with status `INITIATED`, then `SUCCEEDED` or `FAILED` with the provider reference or failure reason. The attempt is written in its own transaction, so a failed attempt stays recorded even though the payment request's own transaction rolls back. A unique index allows any number of failed attempts but only one attempt in flight or succeeded per order, so a double-clicked "pay" charges once; the other request gets `409`.
- A declined payment is `422` with `errorCode` `PAYMENT_DECLINED`. A provider that does not answer in time is `504` with `PAYMENT_TIMEOUT`. In both cases the order stays `CREATED` and can be paid again.
- Cancelling a `PAID` order (by the customer or an admin) refunds the payment through the same processor, in the same transaction as the cancellation. If the provider rejects the refund the request fails with `502` `PAYMENT_REFUND_FAILED` (or `504` `PAYMENT_TIMEOUT`) and nothing changes: the order stays `PAID` and its stock stays reserved, so the cancellation can be retried.
- Development and tests use a simulated gateway (`PAYMENT_GATEWAY=simulated`) that decides the outcome from the token or UPI address, like a provider's test cards: a value containing `decline` is declined, `timeout` times out, `refundfail` is charged but its refund is rejected, and anything else is approved. No real money is moved.

## Events and Email

The services publish `OrderPlacedEvent`, `OrderPaidEvent` and `OrderCancelledEvent` with Spring's `ApplicationEventPublisher`. A listener sends the customer an email for each through `JavaMailSender` (MailHog in development, UI at `http://localhost:8025`).

- The listener runs only after the transaction that published the event commits (`@TransactionalEventListener(phase = AFTER_COMMIT)`), so a rolled-back order or payment never produces an email.
- It runs asynchronously (`@Async`), so the SMTP call does not delay the HTTP response.
- Delivery is at most once: if the application stops between the commit and the send, or the mail server refuses the message, that email is not retried. The failure is logged.
- Recipient addresses are masked in the logs.

### Asynchronous execution

`@Async` work runs on a dedicated `ThreadPoolTaskExecutor` configured under `app.async` (`ASYNC_CORE_SIZE`, `ASYNC_MAX_SIZE`, `ASYNC_QUEUE_CAPACITY`, `ASYNC_REJECTION_POLICY`), validated at startup. The queue is bounded; when it is full and every thread is busy, the rejection policy applies (`CALLER_RUNS` by default: the submitting thread sends the email itself). Threads are named `async-N`. A task decorator copies the correlation id and the caller's security context into the worker thread and removes them afterwards, so log lines written by async work carry the correlation id of the request that caused it. Exceptions from `void` async methods are logged by an `AsyncUncaughtExceptionHandler`. On shutdown the executor waits up to 30 seconds for queued tasks.

## Running Locally

```
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The compose stack publishes MySQL on `localhost:3307`, Redis on `localhost:6380` and MailHog on `localhost:1025` (SMTP) and `http://localhost:8025` (web UI). The application listens on port 8081.

In the `dev` profile the application creates an admin account on first start. The email and password come from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` (defaults in `.env.example`); the password is BCrypt-hashed at startup and never stored in clear text. Two categories and five products are added when the catalogue is empty. No seed data other than the two roles is created in any other profile.

## Database

The schema is owned by Flyway migrations in `src/main/resources/db/migration` and applied on startup in every profile. `V1` creates the tables with primary and foreign keys, `NOT NULL`, `UNIQUE` (user email, product SKU, one cart line per user and product, order number) and `CHECK` constraints (non-negative price and stock, positive quantities), plus indexes for the query paths the application uses. `V2` inserts the two roles, `V3` adds the refresh token table, `V4` makes the product category mandatory and indexes active products by category, `V5` adds the order status history, shipping addresses, stored line totals and a check on the order status, `V6` adds the idempotency key table, and `V7` adds the payments table. Hibernate runs with `ddl-auto=validate`, so a mismatch between the entities and the migrated schema fails startup instead of silently altering tables, and `open-in-view` is off, so every lazy association is loaded inside a service transaction.

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

## Observability

### Actuator endpoints

Only four endpoints are exposed over HTTP. Everything else Actuator offers (`env`, `beans`, `configprops`, `loggers`, `heapdump`, `threaddump`, ...) is not exposed at all and returns `404`, even to admins.

| Endpoint | Access | Content |
|----------|--------|---------|
| `GET /actuator/health` | public | `{"status":"UP"}` for anonymous callers and customers; admins also see the `db` and `redis` components with their details |
| `GET /actuator/health/liveness` | public | Whether the process is alive; does not check the database or Redis |
| `GET /actuator/health/readiness` | public | Whether the instance can take traffic: readiness state, MySQL and Redis |
| `GET /actuator/info` | ADMIN | Build information (artifact, version, build time) and the git commit when the build had a `.git` directory |
| `GET /actuator/metrics` | ADMIN | Meter names; `/actuator/metrics/{name}` shows one meter with its tags |
| `GET /actuator/prometheus` | ADMIN | All meters in the Prometheus text format |
| `GET /actuator` | ADMIN | Links to the exposed endpoints |

The mail server is deliberately not part of health: order emails are sent asynchronously after commit, and a MailHog or SMTP outage should not take the instance out of the load balancer.

### Business metrics

Besides the JVM, HTTP, Hikari and cache meters that Spring Boot registers, the application records:

| Meter | Type | Tags | Meaning |
|-------|------|------|---------|
| `orders.placed` (`orders_placed_total`) | counter | `application` | Orders created and committed. Replayed requests are not counted. |
| `orders.placement` (`orders_placement_seconds`) | timer | `outcome`: `created`, `replayed`, `rejected`, `failed` | Time to handle `POST /api/v1/me/orders`, including the commit |
| `payments.failed` (`payments_failed_total`) | counter | `method`: `CARD`, `UPI`, `COD`; `reason`: `declined`, `timeout`, `error` | Payment attempts that did not succeed |

All of them are registered at startup, so they appear with the value 0 before the first order. Tag values come from fixed enums; free text such as a decline reason never becomes a tag.

### Scraping with Prometheus

`/actuator/prometheus` requires an admin bearer token:

```bash
TOKEN=$(curl -s -X POST localhost:8081/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"Admin123!"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8081/actuator/prometheus | grep -E '^(orders|payments)_'
```

A Prometheus job reads the token from a file:

```yaml
scrape_configs:
  - job_name: ecommerce-backend
    metrics_path: /actuator/prometheus
    authorization:
      type: Bearer
      credentials_file: /etc/prometheus/ecommerce-token
    static_configs:
      - targets: ["host.docker.internal:8081"]
```

Access tokens expire after 15 minutes, so this setup suits local use only. In a real deployment the management endpoints would be moved to a separate port (`management.server.port`) that is reachable only from inside the cluster network.

### Slow call logging

Methods of beans annotated with `@LogExecutionTime` (order placement, payments and the product search) are measured by an aspect. A call that takes longer than `SLOW_METHOD_THRESHOLD` (default `500ms`) is logged at WARN with the class, method and duration, never with the arguments.

### Logs

In `dev` and `test` the console shows Spring Boot's readable pattern with the correlation id in brackets. In `prod` every line is an ECS JSON document that log shippers can index without parsing:

```json
{"@timestamp":"2026-10-23T11:56:00.937853556Z","log":{"level":"INFO","logger":"com.darshan.ecommerce.payment.PaymentService"},"process":{"thread":{"name":"http-nio-8081-exec-1"}},"service":{"name":"ecommerce-backend","environment":"prod","node":{}},"message":"Payment 7 for order 42 declined","correlationId":"3c2d754f-6f0f-42d8-8240-5acc50746cd5","ecs":{"version":"8.11"}}
```

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
| `GET`  | `/api/v1/me/orders` | CUSTOMER, ADMIN | List my orders (paged) |
| `POST` | `/api/v1/me/orders` | CUSTOMER, ADMIN | Place an order from my cart; requires `Idempotency-Key` |
| `GET`  | `/api/v1/me/orders/{id}` | CUSTOMER, ADMIN | One of my orders with its status history |
| `POST` | `/api/v1/me/orders/{id}/cancel` | CUSTOMER, ADMIN | Cancel one of my orders that has not shipped; a paid order is refunded |
| `POST` | `/api/v1/me/orders/{id}/payments` | CUSTOMER, ADMIN | Pay for one of my orders with CARD, UPI or COD |
| `GET`  | `/api/v1/admin/users` | ADMIN | List users |
| `GET`  | `/api/v1/admin/users/{id}` | ADMIN | Get any user |
| `GET`  | `/api/v1/admin/users/{id}/orders` | ADMIN | List any user's orders (paged) |
| `GET`  | `/api/v1/admin/orders` | ADMIN | List all orders, optionally by `status` (paged) |
| `GET`  | `/api/v1/admin/orders/{id}` | ADMIN | Any order with its status history |
| `PATCH` | `/api/v1/admin/orders/{id}/status` | ADMIN | Move an order to another status |
| `GET`  | `/api/v1/admin/products` | ADMIN | List all products, including inactive ones |
| `GET`  | `/api/v1/admin/products/{id}` | ADMIN | Get any product, including inactive ones |
| `POST` | `/api/v1/admin/products` | ADMIN | Create a product; requires a unique `sku` |
| `PUT`  | `/api/v1/admin/products/{id}` | ADMIN | Update a product; requires the current `version` |
| `DELETE` | `/api/v1/admin/products/{id}` | ADMIN | Soft-delete a product |
| `POST` | `/api/v1/admin/categories` | ADMIN | Create a category |
| `PUT`  | `/api/v1/admin/categories/{id}` | ADMIN | Rename or describe a category |
| `DELETE` | `/api/v1/admin/categories/{id}` | ADMIN | Delete a category that has no products |
| `GET`  | `/actuator/health` | public | Health status; details for ADMIN (see Observability) |
| `GET`  | `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus` | ADMIN | Build info and metrics |

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
├── order/       # orders and lines, status lifecycle and history, idempotent placement, order events, /me/orders and admin order endpoints
├── payment/     # payment attempts, processors per method, gateway, refunds, /me/orders/{id}/payments
├── notification/ # order emails sent after commit
└── common/
    ├── async/       # @Async executor, context-copying task decorator, uncaught exception handler
    ├── config/      # typed settings, clock, JPA auditing, Redis caches, scheduling, development seed data
    ├── crypto/      # SHA-256 helper
    ├── exception/   # problem detail factory and global error handling
    ├── logging/     # masking helpers for log output
    ├── money/       # scale and rounding for amounts
    ├── observability/ # @LogExecutionTime and the slow call aspect
    ├── persistence/ # base entity and auditor lookup
    ├── security/    # filter chain, CORS, principal, 401/403 problem responses
    ├── validation/  # custom Bean Validation constraints
    └── web/         # correlation id filter, paging parameters, sort whitelist, page response
```
