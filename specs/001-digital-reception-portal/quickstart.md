# Quickstart: Digital Reception Portal

**Feature**: Digital Reception Portal  
**Date**: 2026-05-14  
**Stack**: Next.js 14 · TypeScript 5 · PostgreSQL 15 (Neon) · Prisma · NextAuth.js v5

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Node.js | 20 LTS | https://nodejs.org |
| pnpm | 9.x | `npm install -g pnpm` |
| PostgreSQL client | 15 | Only needed for direct DB inspection |
| Git | any | — |

You will also need accounts (free tiers available):
- **Neon** (managed PostgreSQL): https://neon.tech
- **MSG91** (OTP SMS, India): https://msg91.com — register DLT template
- **Razorpay** (payments): https://razorpay.com — test mode for local dev
- **Cloudflare R2** (object storage): https://cloudflare.com/r2

---

## 1. Clone and install dependencies

```bash
git clone https://github.com/<org>/pph.git
cd pph
pnpm install
```

---

## 2. Environment variables

Copy the example file and fill in values:

```bash
cp .env.example .env.local
```

**`.env.example`**:

```env
# Database (Neon connection string)
DATABASE_URL="postgresql://user:password@ep-xxx.us-east-1.aws.neon.tech/pph?sslmode=require"
DIRECT_DATABASE_URL="postgresql://user:password@ep-xxx.us-east-1.aws.neon.tech/pph?sslmode=require"

# NextAuth.js
NEXTAUTH_URL="http://localhost:3000"
NEXTAUTH_SECRET="generate-with: openssl rand -base64 32"

# OTP — MSG91
MSG91_AUTH_KEY="your_msg91_auth_key"
MSG91_TEMPLATE_ID="your_dlt_template_id"

# OTP fallback — Twilio
TWILIO_ACCOUNT_SID="ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
TWILIO_AUTH_TOKEN="your_auth_token"
TWILIO_FROM_NUMBER="+1234567890"

# Payments — Razorpay
RAZORPAY_KEY_ID="rzp_test_xxxxxxxxxx"
RAZORPAY_KEY_SECRET="your_razorpay_secret"
RAZORPAY_WEBHOOK_SECRET="your_webhook_secret"

# Object storage — Cloudflare R2 (S3-compatible)
R2_ACCOUNT_ID="your_cloudflare_account_id"
R2_ACCESS_KEY_ID="your_r2_access_key"
R2_SECRET_ACCESS_KEY="your_r2_secret"
R2_BUCKET_NAME="pph-assets"
R2_PUBLIC_URL="https://pub-xxx.r2.dev"
```

> **Do not commit `.env.local` to version control.** It is already in `.gitignore`.

---

## 3. Database setup

Create and migrate the database:

```bash
# Push schema to your Neon database (development — no migration files)
pnpm prisma db push

# Or apply migrations (staging / production)
pnpm prisma migrate deploy
```

Seed initial data (roles with default permissions, admin account):

```bash
pnpm prisma db seed
```

The seed script creates:
- Three `Role` rows (`resident`, `elected_body`, `admin`) with default permissions
- One admin `User` (mobile `0000000000`, flat `MGMT-01`, `verification_status = approved`)

> Change the admin mobile number in `prisma/seed.ts` before deploying.

---

## 4. Start the development server

```bash
pnpm dev
```

The app is available at [http://localhost:3000](http://localhost:3000).

---

## 5. Project structure orientation

```
src/app/(auth)/login        # OTP login page
src/app/(resident)/         # Resident portal (requires approved session)
src/app/(admin)/            # Admin panel (requires admin role)
src/app/(elected-body)/     # Elected body panel (requires elected_body role)
src/app/api/                # API route handlers
src/lib/auth.ts             # NextAuth.js config — OTP + password providers
src/lib/permissions.ts      # Role-permission middleware
prisma/schema.prisma        # Full database schema
```

---

## 6. Running tests

```bash
# Unit and integration tests (Jest)
pnpm test

# End-to-end tests (Playwright) — requires running dev server
pnpm test:e2e
```

E2E tests cover the P1 user stories: Information Desk self-service, Amenity Booking,
and Resident Approval flow.

---

## 7. Prisma Studio (optional DB browser)

```bash
pnpm prisma studio
```

Opens a visual database browser at [http://localhost:5555](http://localhost:5555).

---

## 8. Production deployment (Vercel)

1. Push the repository to GitHub.
2. Import the project in [Vercel](https://vercel.com/new).
3. Add all environment variables from `.env.example` in the Vercel dashboard.
4. Set `NEXTAUTH_URL` to your production domain.
5. Run `pnpm prisma migrate deploy` against the Neon production database.
6. Vercel auto-deploys on every push to `main`.

---

## 9. Key operational notes

| Topic | Note |
|---|---|
| Resident onboarding | New signups arrive in `verification_status = mobile_matched`; admin approves via `/admin/users` |
| OTP rate limits | 3 OTP requests per mobile per 10 minutes (enforced server-side) |
| Audit archival | Nightly cron at `src/app/api/cron/archive-audit.ts` (Vercel Cron Jobs); exports 12-month-old rows to R2 |
| Live notices | Residents connect to `/api/sse/notices`; fallback polling every 30 s via React hook in `src/components/notices/` |
| Booking conflicts | Handled by `booking-service.ts` using PostgreSQL `SELECT FOR UPDATE` — no application-level locking needed |
| DLT compliance | All transactional SMS sent via MSG91 must use a pre-registered DLT template; update `MSG91_TEMPLATE_ID` for each template |
