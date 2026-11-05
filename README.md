# GMart microservices

The GMart backend, split into independently running Spring Boot services behind an API gateway.
The Angular frontend (`../DemoGmart/frontend`) talks only to the gateway, at the same URL as the
old single backend: `http://localhost:8080/api`.

```
                 Angular (4200)
                       │
              api-gateway :8080        CORS, routing; /api/internal/** is never exposed
     ┌──────────┬──────┴─────┬──────────────┐
 user :8081  catalog :8082  order :8083   payment :8084 ──► Razorpay
 auth, JWT   products,      cart, coupons,   online payments,
 addresses   categories,    orders, live     refunds, webhook
 employees   stock          tracking (Kafka)
 contact     (Redis cache)
```

| Module | Port | Database | Owns |
|---|---|---|---|
| `gmart-common` | – | – | Shared library: `ApiResponse`, JWT validation, `/internal/**` guard, `InternalClient`, Kafka event records |
| `api-gateway` | 8080 | – | Spring Cloud Gateway routes + CORS |
| `user-service` | 8081 | `gmart_user_db` | `/auth`, `/addresses`, `/employees`, `/contact` |
| `catalog-service` | 8082 | `gmart_catalog_db` | `/products`, `/categories`, stock reserve/release |
| `order-service` | 8083 | `gmart_order_db` | `/cart`, `/coupons`, `/orders`, simulated store and rider |
| `payment-service` | 8084 | `gmart_payment_db` | `/payments/razorpay/**` (Razorpay Route transfers to stores) |
| `store-service` | 8085 | `gmart_store_db` | `/stores/**`: applications, approval, store settings, commission, payout account |

## Marketplace roles

| Role | Who | Can |
|---|---|---|
| Super admin | runs GMart | approve/reject/suspend stores, commission and Razorpay linked account per store, categories, all orders and payments, other super admins |
| Store owner | runs one store | the store's products, orders (incl. cancel/refund), staff, settings (open/closed), earnings |
| Staff | works for one store | the store's products and stock, process orders (confirm → delivered) |
| Customer | shops | buy from one store per cart; apply to open a store ("Sell on GMart") |

A customer applies → the super admin approves → the applicant becomes the store owner at their
next sign-in. The JWT carries `role` and `storeId`; every service checks store ownership itself
(`CurrentUser` in gmart-common).

Demo logins (dev profile): `admin`/`12345678` (super admin), `store_owner`, `store_staff`,
`fresh_owner`, `john_doe` (all `password123`).

## How the services talk

- **Login**: user-service signs a JWT that carries the username and role. Every service validates
  it with the shared `JWT_SECRET`, so none of them needs the user database.
- **Service-to-service calls** go to `/internal/**` endpoints with the `X-Internal-Token` header
  (`INTERNAL_TOKEN`). Calls without it get 403, and the gateway doesn't route them at all.
- **Cash on delivery**: order-service asks catalog-service to *reserve* stock (all or nothing),
  then saves the order. If saving fails, it *releases* the stock again.
- **Online payment**: payment-service asks order-service for the cart total, creates a Razorpay
  order, and verifies the result. Then it asks order-service to create the order
  (idempotent per Razorpay order). If that fails, the payment is refunded.
- **Cancelling** a paid order makes order-service call payment-service for the refund, then
  release the stock.
- **Kafka** (`gmart.orders`, `gmart.order-status`) stays inside order-service, as before.

## Run

### Local development

Local runs use the **`dev` profile**: public development secrets plus demo data
(the demo catalogue, `john_doe` / `password123` and `admin` / `12345678`). Docker services
(MySQL, Redis, Kafka) come from `C:\Users\ksham\dev-services`.

```powershell
$env:RAZORPAY_KEY_SECRET = "<test key secret>"   # optional: enables "Pay online"
.\start-all.ps1             # dev profile, with Docker
.\start-all.ps1 -Offline    # dev profile, without Docker (H2 in-memory, data resets on restart)
```

Or run one service from its folder: `mvn spring-boot:run -Dspring-boot.run.profiles=dev` (or `h2` for offline).

Stop the old single backend (`DemoGmart/backend`) first: both use port 8080.

### Production

Run **without** the `dev` or `h2` profile. No demo data is created, and each service refuses
to start unless `JWT_SECRET` and `INTERNAL_TOKEN` are set to strong values. Values that
appear in this repository are rejected. The first admin account is created from
`ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` (at least 12 characters) when no
admin exists yet. Remove `ADMIN_PASSWORD` from the environment after the first start.

Generate secrets with, for example, `openssl rand -hex 32`.

In production orders do **not** move by themselves: staff confirm, pack, send out (with the
delivery partner's name and phone) and mark delivered from **Admin → Orders**
(`PUT /api/orders/{id}/status`). The dev profile keeps a simulated store and rider for testing
(`gmart.fulfilment.simulate=true`).

The full launch checklist (Razorpay activation, hosting, legal pages) is
[GO-LIVE.md](https://github.com/Shamdalkhokad123/DemoGmart/blob/feature/microservices-razorpay/GO-LIVE.md) in the DemoGmart repo.

## Configuration (environment variables)

| Variable | Production | Dev profile default | Used by |
|---|---|---|---|
| `JWT_SECRET` | **required**, 32+ chars, same in all services | public dev value | all |
| `INTERNAL_TOKEN` | **required**, 24+ chars, same in all services | public dev value | all |
| `DB_USER`, `DB_PASSWORD` | **required** (use a per-service DB user, not root) | `root` / `root` | user, catalog, order, payment |
| `DB_HOST` | `localhost` | `localhost` | user, catalog, order, payment |
| `ADMIN_USERNAME`, `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first start only | not needed (demo admin) | user |
| `DELIVERY_PROMISED_MINUTES`, `DELIVERY_RIDE_MINUTES` | `30`, `20` | `10`, `20` | order |
| `ALLOWED_ORIGINS` | only if the site is on another domain | localhost:4200/4000 | gateway |
| `REDIS_HOST` | `localhost` | `localhost` | catalog |
| `KAFKA_SERVERS` | `localhost:9092` | `localhost:9092` | order |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | live keys | test key id, no secret | payment |
| `*_SERVICE_URL` / `*_SERVICE_URI` | your internal hostnames | `http://localhost:808x` | services / gateway |

Razorpay webhook URL (events `payment.captured`, `order.paid`): `https://<public-host>/api/payments/razorpay/webhook`.

## Production with Docker

```bash
cp .env.example .env    # fill in every value (openssl rand -hex 32 for secrets)
docker compose -f docker-compose.prod.yml up -d --build
```

This starts MySQL (one database and one user per service, created by `deploy/mysql-init`),
Redis, Kafka and the five services. Flyway creates the tables on first start. Only the gateway
is published, on `127.0.0.1:8080`, for nginx on the same host. Back up with `deploy/backup-mysql.sh`.
The full checklist is in GO-LIVE.md (DemoGmart repo).

## Tests

`mvn verify` runs the unit and integration tests (in-memory H2, no Docker needed). They cover
pricing and coupons, order status rules, stock reservation and idempotent release, Razorpay
signatures, payment completion, refunds and reconciliation, login hashing and lockout, and the
production secrets check. GitHub Actions (`.github/workflows/ci.yml`) runs them on every push and
builds the Docker images.
