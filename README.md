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
| `payment-service` | 8084 | `gmart_payment_db` | `/payments/razorpay/**` |

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

Docker services (MySQL, Redis, Kafka) come from `C:\Users\ksham\dev-services`. Databases are
created automatically and seeded with the demo users and catalogue on first start.

```powershell
$env:RAZORPAY_KEY_SECRET = "<test key secret>"   # optional: enables "Pay online"
.\start-all.ps1             # with Docker
.\start-all.ps1 -Offline    # without Docker (H2 in-memory, data resets on restart)
```

Or run one service from its folder: `mvn spring-boot:run` (add `-Dspring-boot.run.profiles=h2` for offline).

Stop the old single backend (`DemoGmart/backend`) first: both use port 8080.

Demo logins: `john_doe` / `password123` (customer), `admin` / `12345678` (admin).

## Configuration (environment variables)

| Variable | Default | Used by |
|---|---|---|
| `JWT_SECRET` | dev value | all (must match) |
| `INTERNAL_TOKEN` | `gmart-internal-dev-token` | all (must match). Change in any shared environment |
| `DB_HOST`, `DB_USER`, `DB_PASSWORD` | `localhost`, `root`, `root` | user, catalog, order, payment |
| `REDIS_HOST` | `localhost` | catalog |
| `KAFKA_SERVERS` | `localhost:9092` | order |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | test key id, none, none | payment |
| `*_SERVICE_URL` / `*_SERVICE_URI` | `http://localhost:808x` | services / gateway |

Razorpay webhook URL (events `payment.captured`, `order.paid`): `https://<public-host>/api/payments/razorpay/webhook`.
