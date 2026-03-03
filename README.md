# E-commerce Backend

A production-style e-commerce REST API built with **Spring Boot**, **MySQL**, and a layered architecture. The backend models users, products, shopping carts, and orders with JPA entities and exposes REST endpoints for catalog browsing, cart management, and order placement.

---

## Tech Stack

| Layer        | Technology                          |
| ------------ | ----------------------------------- |
| Language     | Java 17                             |
| Framework    | Spring Boot 3.x                      |
| Persistence  | Spring Data JPA, Hibernate, MySQL   |
| Security     | Spring Security                     |
| Build        | Maven                               |
| Validation   | Bean Validation (Jakarta)            |

---

## Architecture

- **Domain**: JPA entities for `User`, `Role`, `Product`, `CartItem`, `Order`, `OrderItem`.
- **Data**: JPA repositories with custom queries where needed.
- **Business**: Service layer for product, user, cart, and order operations with transaction boundaries.
- **API**: REST controllers under `/api` with JSON request/response and global exception handling.

---

## API Overview

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/api/products` | List all products (optional `?q=` search) |
| `GET`  | `/api/products/{id}` | Get product by ID |
| `POST` | `/api/products` | Create product |
| `PUT`  | `/api/products/{id}` | Update product |
| `DELETE` | `/api/products/{id}` | Delete product |
| `GET`  | `/api/users` | List users |
| `GET`  | `/api/users/{id}` | Get user by ID |
| `POST` | `/api/users` | Register user |
| `GET`  | `/api/users/{userId}/cart` | Get user's cart |
| `POST` | `/api/users/{userId}/cart?productId=&quantity=` | Add item to cart |
| `DELETE` | `/api/users/{userId}/cart` | Clear cart |
| `GET`  | `/api/users/{userId}/orders` | List user's orders |
| `POST` | `/api/users/{userId}/orders` | Place order from cart |

---

## Project Structure

```
src/main/java/com/example/ecommerce/
├── EcommerceApplication.java
├── config/          # Security, data seeding
├── controller/      # REST endpoints
├── exception/       # Global error handling
├── model/           # JPA entities
├── repository/      # Data access
└── service/         # Business logic
```

---

