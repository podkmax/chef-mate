# Delivery Plan for Codex

## Step-by-step

### ✅ Step 1 — Project scaffold
- Create monorepo:
/backend # Spring Boot 21
/frontend # React
/ops # docker-compose: Postgres
/docs

- Backend minimal Spring Boot app:
- Dependencies: spring-web, validation, jdbc, flyway, postgresql
- `application.properties` from Product Spec
- Frontend React with placeholder pages
- `docker-compose.yml` with Postgres
- `README.md` with local run commands

### Step 2 — DB migrations (Flyway)
- Tables: User, Dish, DishIngredient, Order, OrderItem, OrderIngredientAggregate

### Step 3 — Menu API (CRUD) + Excel import/export

### Step 4 — Orders API + ingredient aggregation

### Step 5 — Telegram Bot basics + /start + menu browsing

### Step 6 — Confirm orders + notifications to Cook

### Step 7 — Daily digest

### Step 8 — Admin panel UI (Menu/Orders)

### Step 9 — Docs: DEPLOY, API docs, test data

> After each step: commit + show diff + smoke check.
