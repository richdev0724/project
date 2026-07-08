# Market Mono Backend - Java 25 + Spring Boot 3.5.3

Modern Java backend server for Market Mono financial market data application. Provides options flow analysis, contract details, and real-time market data by proxying the Unusual Whales API.

## 🚀 Tech Stack

- **Java 25** - Virtual Threads, Pattern Matching, Records, Sealed Classes
- **Spring Boot 3.5.3** - Latest stable release
- **PostgreSQL 18 + TimescaleDB 2.25.2** - Host PG18 in production, Docker TimescaleDB for the local `local` profile
- **Caffeine** - Local caching (5-minute TTL)
- **Liquibase** - Database migrations
- **Gradle 8.5+** - Build tool with Kotlin DSL
- **WebClient** - Non-blocking reactive HTTP client
- **Lombok & MapStruct** - Code generation utilities

## 📋 Prerequisites

- Java 25 (JDK 25)
- Docker & Docker Compose
- Unusual Whales API Key

## 🏗️ Project Setup

### 1. Clone and Navigate
```bash
cd backend-server
```

### 2. Configure Environment
```bash
cp .env.example .env
# Edit .env and add your UNUSUAL_WHALES_API_KEY
```

### 3. Start Local TimescaleDB
```bash
docker compose -f docker-compose-local.yml up -d
```

### 4. Build Project
```bash
./gradlew clean build
```

### 5. Run Application
```bash
./run-local.cmd
```

Alternative manual run:

```bash
SPRING_PROFILES_ACTIVE=local DB_PORT=5433 ./gradlew bootRun
```

The application will start on `http://localhost:8080`

## 📊 Database Migrations

Local development still uses Liquibase on startup. Production host PostgreSQL currently **does not**: live app containers keep `SPRING_LIQUIBASE_ENABLED=false` until the legacy app changelog history is reconciled with the new consolidated PG18 baseline.

Manual migration commands (local / controlled maintenance only):

```bash
# Apply migrations
./gradlew update

# Rollback last changeset
./gradlew rollback -PliquibaseCommandValue=1

# Generate SQL for review
./gradlew updateSQL
```

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

## 📖 API Documentation

Once running, access interactive API docs:
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## 🎯 Key Endpoints

- `GET /api/v1/contracts/{symbol}/chain` - Full option chain
- `GET /api/v1/contracts/{symbol}/details` - Contract details for modal
- `GET /api/v1/gamma-exposure/{symbol}` - Gamma exposure matrix
- `GET /api/v1/flow` - Options flow data with advanced filtering (see [.vscode/docs/FLOW_API_DOCUMENTATION.md](.vscode/docs/FLOW_API_DOCUMENTATION.md))
- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - Application metrics

## 🏗️ Project Structure

| Path                                      | Purpose                    |
|-------------------------------------------|----------------------------|
| `src/main/java/com/marketmono`            | Main application package   |
| `src/main/java/com/marketmono/config`     | Configuration classes      |
| `src/main/java/com/marketmono/controller` | REST controllers           |
| `src/main/java/com/marketmono/service`    | Business logic             |
| `src/main/java/com/marketmono/client`     | External API clients       |
| `src/main/java/com/marketmono/model`      | DTOs and entities          |
| `src/main/java/com/marketmono/repository` | JPA repositories           |
| `src/main/java/com/marketmono/mapper`     | MapStruct mappers          |
| `src/main/java/com/marketmono/exception`  | Exception handling         |
| `src/main/resources/application.yml`      | Main configuration         |
| `src/main/resources/application-dev.yml`  | Dev profile                |
| `src/main/resources/application-prod.yml` | Production profile         |
| `src/main/resources/db/changelog`         | Liquibase migrations       |
| `src/test`                                | Unit and integration tests |

## 🔧 Configuration

### Caching Strategy (2-Tier)
1. **Caffeine** (in-memory): 5 minutes, frequently accessed data
2. **PostgreSQL** (persistent): 24 hours, historical fallback

**Future (Phase 3)**: Redis will be added for distributed caching when scaling horizontally.

### Rate Limiting
- **Unusual Whales API**: 120 requests/minute (2 req/sec)
- Guava RateLimiter prevents quota overages
- Automatic backoff on 429 responses

### Database Schema
- `cached_api_responses` - JSONB flexible cache
- `option_contracts` - Structured contract data
- `api_rate_limits` - Rate limit tracking

See `backend-server/.vscode/docs/plans/2026-03-13-host-postgres-timescaledb-migration/EXECUTION_PLAN.md` for the host-PG18 migration record and schema notes.

## 🐳 Docker Commands

```bash
# Start local TimescaleDB only
docker compose -f docker-compose-local.yml up -d

# View local DB logs
docker compose -f docker-compose-local.yml logs -f timescaledb

# Stop local DB without destroying data
docker compose -f docker-compose-local.yml stop timescaledb

# Reset LOCAL dev DB only (⚠️ destroys local Docker data)
docker compose -f docker-compose-local.yml down -v
docker compose -f docker-compose-local.yml up -d
```

Do **not** use these commands against the remote production compose stack.

## 🔍 Monitoring

- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:8080/actuator/prometheus

## 📚 Documentation

All project documentation is in `.vscode/docs/`:
- `TECH_STACK.md` - Technology decisions and rationale
- `EXECUTION_PLAN.md` - 6-week implementation roadmap
- `PROJECT_SUMMARY.md` - Executive overview

## 🛠️ Development

### Hot Reload
Spring Boot DevTools is included for automatic restarts:
```bash
./gradlew bootRun
```

### Code Style
Follow coding standards in `.github/copilot-instructions.md`:
- Constructor injection only
- Records for DTOs
- Java 25 features (Virtual Threads, Pattern Matching)
- Lombok annotations
- Comprehensive logging

### Common Commands
```bash
# Clean build
./gradlew clean build

# Run tests
./gradlew test

# Generate coverage report
./gradlew jacocoTestReport

# Check dependencies
./gradlew dependencies

# Update Gradle wrapper
./gradlew wrapper --gradle-version 8.11
```

## 🚀 Deployment

### Environment Variables Required
```bash
UNUSUAL_WHALES_API_KEY=<your_key>
DB_HOST=<postgres_host>
DB_USERNAME=<db_user>
DB_PASSWORD=<db_password>
SPRING_PROFILES_ACTIVE=prod
APP_FRONTEND_URL=https://marketmono.com
MAIL_HOST=smtp.gmail.com
MAIL_PORT=465
MAIL_USERNAME=<smtp_username>
MAIL_PASSWORD=<smtp_app_password>
MAIL_FROM_EMAIL=info@marketmono.com
MAIL_FROM_NAME=Market Mono
MAIL_SMTP_STARTTLS_ENABLE=false
MAIL_SMTP_SSL_ENABLE=true
```

For Gmail app passwords, copy `MAIL_PASSWORD` exactly as issued. If Google
shows the app password as four groups separated by spaces, keep those spaces in
the local `.env` file and in the GitHub `MAIL_PASSWORD` repository secret.

### Current Production Database Notes

- Production runtime DB is self-hosted PostgreSQL `18.3` + TimescaleDB `2.25.2` on the `pg18-tsdb-*` database droplets.
- `pg18-tsdb-a` is the primary / writer; `pg18-tsdb-b` is the async physical streaming standby / read replica.
- App containers should use the approved private DB endpoint for `pg18-tsdb-a` unless a workload is explicitly designed for the read replica.
- Manual operator access should use the approved self-hosted DB access / tunnel workflow from the operations docs, not retired managed-DB or legacy OVH shortcuts.
- `marketmono-postgres` is currently a stopped rollback asset, **not** the active production database.
- Keep `SPRING_LIQUIBASE_ENABLED=false` on production app containers until changelog reconciliation is explicitly completed.

### Build Production JAR
```bash
./gradlew clean build -Pprod
java -jar build/libs/market-mono-backend-0.0.1-SNAPSHOT.jar
```

### Docker Build (Coming Soon)
```bash
docker build -t market-mono-backend .
docker run -p 8080:8080 --env-file .env market-mono-backend
```

## 🤝 Contributing

See `.github/copilot-instructions.md` for coding standards and best practices.

## 📄 License

Proprietary - Market Mono

---

Refer to documentation in `backend-server/.vscode/docs/`.
# Updated: 11/12/2025 11:09:45
