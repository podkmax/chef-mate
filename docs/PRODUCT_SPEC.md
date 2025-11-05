# ChefMate — Telegram Bot + Admin Panel

## Goal
Clients choose dishes and date in Telegram. Bot aggregates ingredients and shows a shopping list to the client. Cook sees full list and confirms. Menu managed in web admin with Excel import/export.

## Roles
- **Client** (Telegram): choose dishes, portions, date, get products list
- **Cook** (Admin): manage menu, confirm orders, import/export Excel
- **Tech Admin**: full access (same as Cook)

## Tech Requirements
- **Backend (Bot + API):** Java 21, Spring Boot < 3.5, Maven, PostgreSQL, Flyway, TelegramBots (rubenlagus)
- **Frontend (Admin Panel):** React
- **Infra:** Docker Compose (PostgreSQL)

## Business Rules
1. Multiple dishes and portions per order
2. Ingredient aggregation across the whole order
3. No rounding — exact grams/ml/pcs
4. Ingredients may be hidden from client’s list (excludeForClient flag)
5. Datepicker only — no time. One order = one date.
6. Clients see full order history; Cook sees all orders
7. Order workflow:
   - CREATED → PENDING_COOK_CONFIRMATION → CONFIRMED → DONE / CANCELLED
8. Client can update order until confirmation
9. Notifications:
   - Cook: on create/update order
   - Daily digest at 08:00

## Excel Import Template
File: `docs/menu_template.xlsx`

Columns:
- `category` string
- `title` string (unique with category)
- `description` string (opt)
- `portion_size` number (opt)
- `ingredients` string — format: `name|qty|unit; name|qty|unit; ...`
- `active` yes/no
- `hide_from_client_list` yes/no
- `notes` string (opt)

Units allowed: `г`, `кг`, `мл`, `л`, `шт`, `уп`, `зуб`, `гол`. Aggregate only same name+unit.

Example:
Свекла|300|г; Капуста|200|г; Соль|5|г

## Entities (minimum)
- User(id, telegramId, name, role[CLIENT|COOK|ADMIN], createdAt)
- Dish(id, category, title, description, active, createdAt, updatedAt)
- DishIngredient(id, dishId, name, qty, unitId → unit(id), excludeForClient[bool])
- Order(id, userId, targetDate, status, createdAt, updatedAt, comment)
- OrderItem(id, orderId, dishId, portions[int], notes)
- OrderIngredientAggregate(orderId, name, totalQty, unit)

Indexes:
- dish(category, title) unique
- order(userId, targetDate)

## API
Prefix: `/api`

### Public (Bot)
- `GET /public/menu`
- `POST /public/order`
- `GET /public/order/{id}`

### Admin (Cook)
- Menu CRUD
- Import/Export Excel
- Orders CRUD + Export CSV

## Telegram Bot
Library: rubenlagus/TelegramBots
- `/start`, choose dishes & portions, choose date, view order, confirm, history
- Cook notifications + daily digest 08:00

## Admin Panel
- Login (dev stub)
- Menu page + dish details + Excel import/export
- Orders page: filter by date, view, confirm/cancel
- Settings: digest time
