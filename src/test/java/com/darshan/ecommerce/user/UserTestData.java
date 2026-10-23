package com.darshan.ecommerce.user;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

// A user that is valid for the users table: unique email, a password hash placeholder, no roles.
public final class UserTestData {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Long id;
    private String email = "user" + SEQUENCE.incrementAndGet() + "@example.com";

    private UserTestData() {
    }

    public static UserTestData aUser() {
        return new UserTestData();
    }

    // Only for unit tests; persisted users get their id from the database.
    public UserTestData id(long id) {
        this.id = id;
        return this;
    }

    public UserTestData email(String email) {
        this.email = email;
        return this;
    }

    public User build() {
        User user = new User(email, "{noop}irrelevant", "Test User", Set.of());
        if (id != null) {
            ReflectionTestUtils.setField(user, "id", id);
        }
        return user;
    }
}
