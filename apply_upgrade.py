import os
import re

base_dir = '/home/darshan/Github_Projects/ecommerce-backend/src/main/java/com/example/ecommerce'
pom_path = '/home/darshan/Github_Projects/ecommerce-backend/pom.xml'

# 1. Update POM
with open(pom_path, 'r') as f:
    pom = f.read()

deps_to_add = """
        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.11.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.11.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.11.5</version>
            <scope>runtime</scope>
        </dependency>
        <!-- OpenAPI/Swagger -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.3.0</version>
        </dependency>
        <!-- H2 for testing -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
"""
if "jjwt" not in pom:
    pom = pom.replace("</dependencies>", deps_to_add + "\n    </dependencies>")
    with open(pom_path, 'w') as f:
        f.write(pom)

print("Updated pom.xml")
