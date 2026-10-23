package com.darshan.ecommerce.user;

import com.darshan.ecommerce.common.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends BaseEntity {

    // Stored with Spring Security's ROLE_ prefix so hasRole('ADMIN') matches without mapping.
    private static final String PREFIX = "ROLE_";
    public static final String CUSTOMER = PREFIX + "CUSTOMER";
    public static final String ADMIN = PREFIX + "ADMIN";

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    public Role(String name) {
        this.name = name;
    }

    public String shortName() {
        return name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
    }
}
