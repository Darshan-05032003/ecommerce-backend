package com.darshan.ecommerce.common.web;

import org.springframework.data.domain.Sort;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses a {@code sort} query parameter of the form {@code property} or {@code property,asc|desc}
 * against a fixed list of properties. Accepting arbitrary property names would let callers sort by
 * any mapped attribute, including ones without an index, and leak the entity model into the API.
 * The id is always appended as a tie breaker so that pages are stable when values repeat.
 */
public final class SortWhitelist {

    private final Set<String> allowed;
    private final String defaultProperty;

    private SortWhitelist(String defaultProperty, List<String> properties) {
        this.defaultProperty = defaultProperty;
        this.allowed = new LinkedHashSet<>();
        this.allowed.add(defaultProperty);
        this.allowed.addAll(properties);
    }

    public static SortWhitelist of(String defaultProperty, String... otherProperties) {
        return new SortWhitelist(defaultProperty, List.of(otherProperties));
    }

    public Sort parse(String value) {
        if (value == null || value.isBlank()) {
            return withTieBreaker(Sort.by(Sort.Direction.ASC, defaultProperty));
        }
        String[] parts = value.split(",", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("Sort must be 'property' or 'property,asc|desc'");
        }
        String property = parts[0].trim();
        if (!allowed.contains(property)) {
            throw new IllegalArgumentException("Unsupported sort property '" + property + "'. Allowed: "
                    + String.join(", ", allowed));
        }
        Sort.Direction direction = parts.length == 1
                ? Sort.Direction.ASC
                : Sort.Direction.fromOptionalString(parts[1].trim())
                        .orElseThrow(() -> new IllegalArgumentException("Sort direction must be asc or desc"));
        return withTieBreaker(Sort.by(direction, property));
    }

    private static Sort withTieBreaker(Sort sort) {
        return sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
