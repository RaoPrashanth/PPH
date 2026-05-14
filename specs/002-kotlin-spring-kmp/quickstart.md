# Quickstart: Platform Architecture — Kotlin/Spring Boot + Compose Multiplatform

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)  
**Date**: 2026-05-14

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| JDK | 21 (LTS) | [sdkman.io](https://sdkman.io): `sdk install java 21-tem` |
| Gradle | 8.x (via wrapper) | Included — use `./gradlew` |
| Docker | 24+ | [docker.com/get-started](https://www.docker.com/get-started/) |
| Node.js | Not required | Wasm build is pure Gradle/Kotlin |
| Android Studio | Koala+ (optional) | Only needed when adding the Android target |

> **Windows note**: Use `gradlew.bat` instead of `./gradlew` in all commands below.

---

## Clone and Initial Build

```bash
git clone <repo-url>
cd pph

# Verify all subprojects compile
./gradlew :shared:compileKotlinJvm
./gradlew :shared:compileKotlinWasmJs
./gradlew :backend:compileKotlin
./gradlew :frontend:compileKotlinWasmJs
```

Expected: all four tasks succeed with no errors. First run downloads ~300 MB of Gradle/Kotlin toolchain dependencies.

---

## Environment Setup

Copy and fill the backend `.env`:

```bash
cp backend/.env.example backend/.env
```

`backend/.env.example`:

```env
# Database
DATABASE_URL=jdbc:postgresql://<neon-host>/<db>?sslmode=require&user=<user>&password=<password>

# JWT
JWT_SECRET=<generate: openssl rand -base64 64>
JWT_EXPIRY_HOURS=1

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:8081,https://pph.vercel.app

# OTP — MSG91 (primary)
MSG91_AUTH_KEY=<key>
MSG91_TEMPLATE_ID=<DLT-registered template ID>

# OTP — Twilio (fallback, optional)
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
TWILIO_FROM_NUMBER=

# Payments
RAZORPAY_KEY_ID=rzp_test_...
RAZORPAY_KEY_SECRET=<key>

# File storage (optional at dev time)
CLOUDFLARE_R2_BUCKET=
CLOUDFLARE_R2_ACCOUNT_ID=
CLOUDFLARE_R2_ACCESS_KEY=
CLOUDFLARE_R2_SECRET_KEY=
```

Frontend base URL (development):

The Ktor client in `shared/src/commonMain/kotlin/com/pph/shared/constants/ApiConstants.kt` uses:
```kotlin
const val BASE_URL = "http://localhost:8080/api/v1"  // overridden by Gradle property in production
```

For production builds, pass `-PapiBaseUrl=https://your-backend.railway.app/api/v1` to the Gradle wasmJs build.

---

## Running the Backend

```bash
./gradlew :backend:bootRun
```

The Spring Boot server starts on `http://localhost:8080`.

Verify:
```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP"}
```

---

## Database Setup

The backend uses Spring Data JPA with `spring.jpa.hibernate.ddl-auto=update` (dev) / `validate` (prod). For first run:

1. Ensure `DATABASE_URL` points to a valid PostgreSQL 15+ database (Neon free tier works).
2. Run the backend — Hibernate auto-creates tables on first start (dev mode).
3. Seed initial roles (optional helper script):

```bash
./gradlew :backend:bootRun --args="--seed"
```

This creates the default roles: `RESIDENT`, `MANAGEMENT`, `ELECTED_BODY`, `SECURITY`, `ADMIN`.

---

## Running the Frontend (Dev)

```bash
./gradlew :frontend:wasmJsBrowserDevelopmentRun
```

Opens a Webpack dev server at `http://localhost:8081` with hot-reload.

The frontend makes API calls to `http://localhost:8080/api/v1` by default. Ensure the backend is running before testing the login flow.

---

## Running Tests

```bash
# Backend unit + integration tests
./gradlew :backend:test

# Shared module tests (runs on JVM)
./gradlew :shared:jvmTest

# Frontend tests
./gradlew :frontend:wasmJsBrowserTest
```

All tests in a single command:
```bash
./gradlew test
```

---

## Production Build

### Backend — Docker image

```bash
# Build Docker image
docker build -f backend/Dockerfile -t pph-backend:latest .

# Run locally to verify
docker run --env-file backend/.env -p 8080:8080 pph-backend:latest
```

### Frontend — Wasm production bundle

```bash
./gradlew :frontend:wasmJsBrowserDistribution
```

Output: `frontend/composeApp/build/dist/wasmJs/productionExecutable/`

Deploy this directory to Vercel:

```bash
cd frontend
npx vercel --prod
# Vercel automatically detects the dist directory from vercel.json
```

`frontend/vercel.json`:
```json
{
  "outputDirectory": "composeApp/build/dist/wasmJs/productionExecutable",
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
}
```

---

## Deploying to Railway / Render (Backend)

1. Create a new Railway project; select "Deploy from Dockerfile".
2. Point to the repo root; Railway detects `backend/Dockerfile`.
3. Set all environment variables from `backend/.env.example` in the Railway dashboard.
4. Connect the Neon PostgreSQL database via `DATABASE_URL`.
5. Set `CORS_ALLOWED_ORIGINS` to include the Vercel production domain.

The backend listens on `PORT` env var automatically (Spring Boot picks up `SERVER_PORT` or Railway injects `PORT`; add `server.port=${PORT:8080}` to `application.properties`).

---

## Operational Notes

### JWT Token
- Tokens expire in 1 hour (`JWT_EXPIRY_HOURS=1`).
- Stored in-memory by the frontend — users are re-authenticated on page reload.
- No refresh token at MVP; extend `JWT_EXPIRY_HOURS` for a lower-friction experience if needed.

### OTP Rate Limits
- MSG91 rate limit: 5 OTPs per mobile per hour (configurable in `application.properties`).
- Exceeding the limit returns HTTP 429 with `code=OTP_RATE_LIMIT_EXCEEDED`.

### SSE Notices
- The `/api/v1/sse/notices` endpoint uses `SseEmitter` with a 30-second keepalive.
- Bearer token passed as `?token=<jwt>` query parameter (browsers cannot set custom headers for SSE).
- Frontend reconnects automatically on connection drop (Ktor SSE client).

### Booking Lock
- `FacilitySlotEntity` booking uses `@Transactional` + `SELECT FOR UPDATE` to prevent double-booking under concurrent requests.
- If a slot is taken during checkout, HTTP 409 `SLOT_ALREADY_BOOKED` is returned.

### Razorpay Webhook
- Webhook endpoint `/api/v1/summer-camp/payment/webhook` verifies `X-Razorpay-Signature` via HMAC-SHA256 before updating `PaymentStatus`.
- Must be publicly reachable — use ngrok locally: `ngrok http 8080`.

### DLT Compliance (MSG91)
- `MSG91_TEMPLATE_ID` must be a DLT-registered template. Use the exact approved template string in the MSG91 dashboard.
- Non-compliant templates are rejected by Indian telecom carriers silently.

### Adding Mobile Targets (Future)
To add Android:
1. Add `androidTarget()` to `frontend/build.gradle.kts` `kotlin {}` block.
2. Add `androidMain` dependencies (no `commonMain` changes needed).
3. Create `frontend/composeApp/src/androidMain/kotlin/com/pph/frontend/MainActivity.kt`.

That's it — all screens are already in `commonMain`.
