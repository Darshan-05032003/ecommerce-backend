# E-commerce Backend

A REST API for a small online shop built with Spring Boot and MySQL. It models users, products, shopping carts and orders with JPA entities and exposes endpoints for catalog browsing, cart management and order placement, secured with JWT bearer tokens and role-based access control.

## Tech Stack

| Layer        | Technology                                   |
| ------------ | -------------------------------------------- |
| Language     | Java 21                                      |
| Framework    | Spring Boot 4.1                              |
| Persistence  | Spring Data JPA, Hibernate, MySQL            |
| Security     | Spring Security, JWT (jjwt)                  |
| Validation   | Bean Validation (Jakarta)                    |
| API docs     | springdoc-openapi (Swagger UI)               |
| Build        | Maven (wrapper included)                     |

## Build and Test

```
./mvnw clean verify
```

Integration tests run against an in-memory H2 database under the `test` profile. The application itself expects a MySQL database; the connection is configured in `src/main/resources/application.properties` and can be overridden with the standard `SPRING_DATASOURCE_*` environment variables, as `docker-compose.yml` does.

## API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/auth/login` | Authenticate and receive a JWT |
| `GET`  | `/api/products` | List products (optional `?q=` search) |
| `GET`  | `/api/products/{id}` | Get product by ID |
| `POST` | `/api/products` | Create product (admin) |
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
├── product/     # product entity, catalog endpoints
├── cart/        # cart items and cart endpoints
├── order/       # orders, order items and order placement
└── common/
    ├── config/      # seed data
    ├── exception/   # global exception handling
    └── security/    # security filter chain configuration
```
