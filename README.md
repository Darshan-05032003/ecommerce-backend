# E-commerce Backend

A REST API for a small online shop built with Spring Boot and MySQL. It models users, products, shopping carts and orders with JPA entities and exposes endpoints for catalog browsing, cart management and order placement, secured with JWT bearer tokens and role-based access control.

## Tech Stack

| Layer        | Technology                                   |
| ------------ | -------------------------------------------- |
| Language     | Java 21                                      |
| Framework    | Spring Boot 4.1                              |
| Persistence  | Spring Data JPA, Hibernate, MySQL, Flyway    |
| Security     | Spring Security, JWT (jjwt)                  |
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

## Running Locally

```
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

The compose stack publishes MySQL on `localhost:3307`, Redis on `localhost:6380` and MailHog on `localhost:1025` (SMTP) and `http://localhost:8025` (web UI). The application listens on port 8081.

In the `dev` profile the application creates an admin account on first start. The email and password come from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` (defaults in `.env.example`); the password is BCrypt-hashed at startup and never stored in clear text. Two categories and five products are added when the catalogue is empty. No seed data other than the two roles is created in any other profile.

## Database

The schema is owned by Flyway migrations in `src/main/resources/db/migration` and applied on startup in every profile. `V1` creates the tables with primary and foreign keys, `NOT NULL`, `UNIQUE` (user email, product SKU, one cart line per user and product, order number) and `CHECK` constraints (non-negative price and stock, positive quantities), plus indexes for the query paths the application uses. `V2` inserts the two roles. Hibernate runs with `ddl-auto=validate`, so a mismatch between the entities and the migrated schema fails startup instead of silently altering tables, and `open-in-view` is off, so every lazy association is loaded inside a service transaction.

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
  "instance": "/api/products/999",
  "correlationId": "3c2d754f-6f0f-42d8-8240-5acc50746cd5",
  "timestamp": "2026-10-22T17:53:40.442Z"
}
```

Each request gets a correlation id: the value of the `X-Correlation-Id` request header if one is sent, otherwise a generated UUID. It is returned in the `X-Correlation-Id` response header, included in every error body and printed in every log line written while handling the request.

## API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Authenticate and receive a JWT |
| `GET`  | `/api/products` | List products (optional `?q=` search) |
| `GET`  | `/api/products/{id}` | Get product by ID |
| `POST` | `/api/products` | Create product (admin); requires a unique `sku` |
| `PUT`  | `/api/products/{id}` | Update product (admin) |
| `DELETE` | `/api/products/{id}` | Delete product (admin) |
| `POST` | `/api/users` | Register user |
| `GET`  | `/api/users` | List users (admin) |
| `GET`  | `/api/users/{id}` | Get user by ID |
| `GET`  | `/api/users/{userId}/cart` | Get a user's cart |
| `POST` | `/api/users/{userId}/cart?productId=&quantity=` | Add item to cart |
| `DELETE` | `/api/users/{userId}/cart` | Clear cart |
| `GET`  | `/api/users/{userId}/orders` | List a user's orders |
| `POST` | `/api/users/{userId}/orders` | Place order from cart |

Swagger UI is available at `/swagger-ui.html` when the application is running.

## Project Structure

The code is organised by feature rather than by technical layer.

```
src/main/java/com/darshan/ecommerce/
├── EcommerceApplication.java
├── auth/        # login endpoint, JWT creation and validation, authentication filter
├── user/        # user and role entities, registration, user endpoints
├── category/    # product categories
├── product/     # product entity, catalog endpoints
├── cart/        # cart items and cart endpoints
├── order/       # orders, order items and order placement
└── common/
    ├── config/      # typed settings, clock, JPA auditing, development seed data
    ├── exception/   # problem detail error handling
    ├── persistence/ # base entity and auditor lookup
    ├── security/    # security filter chain configuration
    └── web/         # correlation id filter
```
