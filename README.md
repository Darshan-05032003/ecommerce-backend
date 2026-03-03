# E-commerce Backend (Spring Boot)

Simple but production-style e-commerce backend built with Spring Boot and MySQL.

## Tech stack

- Java 17
- Spring Boot (Web, Data JPA, Security, Validation)
- MySQL
- Maven

## Domain model

- `User` with roles
- `Product` catalog with price and stock
- `CartItem` for user shopping carts
- `Order` and `OrderItem` for placed orders

## How to run

1. Ensure you have Java 17+, Maven, and MySQL installed.
2. Create a database named `ecommerce_db` and update credentials in `application.properties` if needed.
3. From the project root, run:

```bash
mvn spring-boot:run
```

On startup, the app seeds:

- One admin user `admin@example.com` / `admin123`
- Sample products (`Laptop`, `Wireless Mouse`)

## Example endpoints

- `GET /api/products` – list products
- `POST /api/products` – create product
- `POST /api/users` – register user
- `POST /api/users/{userId}/cart?productId=&quantity=` – add to cart
- `POST /api/users/{userId}/orders` – place order from cart

