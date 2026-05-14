# Build Contracts: Gradle Multi-Project Structure

**Phase**: 1 — Design  
**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)  
**Date**: 2026-05-14

This document defines the Gradle build contracts between the three project modules — the dependency graph, plugin versions, and inter-module contracts that must not be violated.

---

## Module Dependency Graph

```
settings.gradle.kts (root)
  ├── :shared       (KMP library — no dependencies on :backend or :frontend)
  ├── :backend      (Spring Boot JVM — depends on :shared[jvm])
  └── :frontend     (Compose Multiplatform — depends on :shared[wasmJs])
```

**Rule**: `:shared` MUST NOT depend on `:backend` or `:frontend`. Circular dependencies are a build error.

---

## Root `settings.gradle.kts`

```kotlin
rootProject.name = "pph"

include(":backend")
include(":frontend")
include(":shared")
```

---

## Root `build.gradle.kts` (version catalog plugin only)

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

---

## `gradle/libs.versions.toml` (Version Catalog)

```toml
[versions]
kotlin = "2.1.0"
compose-multiplatform = "1.7.0"
spring-boot = "3.3.0"
spring-dependency-management = "1.1.4"
ktor = "3.0.0"
kotlinx-serialization = "1.7.0"
kotlinx-coroutines = "1.8.1"
jjwt = "0.12.6"
compose-navigation = "2.8.0"
lifecycle-viewmodel-compose = "2.8.0"
postgresql-driver = "42.7.3"
hibernate-core = "6.4.0.Final"

[libraries]
# Shared
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }

# Backend
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
jjwt-api = { module = "io.jsonwebtoken:jjwt-api", version.ref = "jjwt" }
jjwt-impl = { module = "io.jsonwebtoken:jjwt-impl", version.ref = "jjwt" }
jjwt-jackson = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }
postgresql = { module = "org.postgresql:postgresql", version.ref = "postgresql-driver" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin" }

# Frontend
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-js = { module = "io.ktor:ktor-client-js", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
compose-navigation = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "compose-navigation" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle-viewmodel-compose" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

---

## `shared/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies { /* JVM-specific actual implementations if any */ }
        wasmJsMain.dependencies { /* Wasm-specific actual implementations if any */ }
    }
}
```

---

## `backend/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":shared"))   // shared KMP module (JVM target)

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    runtimeOnly(libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
```

---

## `frontend/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "pph.js"
            }
        }
        binaries.executable()
    }
    // FUTURE: add androidTarget() and iosTarget() here — no commonMain changes needed

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.navigation)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.auth)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)   // wasmJs engine
        }
    }
}
```

---

## Inter-Module Contract Rules

| Rule | Enforcement |
|---|---|
| `:shared` has no Spring, no Compose imports | Gradle dependency isolation — neither plugin is applied to `:shared` |
| `:frontend` has no JPA, no Spring imports | Gradle dependency isolation — spring-boot plugin not applied to `:frontend` |
| All API DTOs in `shared/commonMain` | Code review gate: no `data class` with `@Serializable` in `backend/` or `frontend/` source |
| Kotlin DSL only | `.gradle` files (Groovy) rejected by policy (FR-007) |
| Version catalog is single source of truth | All subprojects use `libs.*` aliases; no inline version strings |

---

## Docker Build Contract

```dockerfile
# backend/Dockerfile
FROM gradle:8-jdk21 AS build
WORKDIR /workspace
COPY --chown=gradle:gradle . .
RUN gradle :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Environment variables** (must be set at runtime — never baked into image):

| Variable | Required | Default | Description |
|---|---|---|---|
| `DATABASE_URL` | ✅ | — | Neon PostgreSQL JDBC URL |
| `JWT_SECRET` | ✅ | — | ≥256-bit random Base64 string |
| `JWT_EXPIRY_HOURS` | No | `1` | JWT access token lifetime |
| `CORS_ALLOWED_ORIGINS` | ✅ | — | Comma-separated frontend origins |
| `MSG91_AUTH_KEY` | ✅ | — | SMS OTP provider |
| `MSG91_TEMPLATE_ID` | ✅ | — | DLT-registered template |
| `TWILIO_ACCOUNT_SID` | No | — | Fallback SMS provider |
| `TWILIO_AUTH_TOKEN` | No | — | Fallback SMS provider |
| `RAZORPAY_KEY_ID` | ✅ | — | Payment gateway |
| `RAZORPAY_KEY_SECRET` | ✅ | — | Payment gateway webhook signing |
| `CLOUDFLARE_R2_BUCKET` | No | — | File attachments (notices, forms) |
| `CLOUDFLARE_R2_ACCESS_KEY` | No | — | |
| `CLOUDFLARE_R2_SECRET_KEY` | No | — | |
