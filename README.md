ChefMate Monorepo

Overview

This monorepo contains:
- backend: Spring Boot 21 API + Telegram bot (future)
- frontend: React admin panel (placeholder)
- ops: Docker Compose for PostgreSQL
- docs: Product spec and delivery plan

Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker + Docker Compose

Quick Start (local dev)

1) Start Postgres

```bash
docker compose -f ops/docker-compose.yml up -d
```

2) Run Backend (Spring Boot)

```bash
cd backend
mvn -q -DskipTests spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/api/health
```

3) Run Frontend (React)

```bash
cd frontend
npm install
npm run dev -- --open
```

The admin app will be available on the URL printed by the dev server (default http://localhost:5173/).

Environment

Backend connects to Postgres from Docker Compose using:
- url: jdbc:postgresql://localhost:5432/chefmate
- user: chef
- password: chefpass

Stop services

```bash
docker compose -f ops/docker-compose.yml down -v
```


