# Chempionat-X

Production-ready Spring Boot Telegram bot for organizing individual football/eFootball championships.

## Table of Contents
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Running the Application](#running-the-application)
- [Development](#development)
- [Testing](#testing)
- [Docker Support](#docker-support)
- [API Endpoints](#api-endpoints)
- [Project Structure](#project-structure)

## Features

### Core Functionality
- ✅ Telegram bot integration with command routing
- ✅ User management with role-based access
- ✅ Tournament management (League & Playoff formats)
- ✅ Match scheduling with lifecycle states
- ✅ Match result tracking and approval workflow
- ✅ Media upload support for match screenshots
- ✅ League standings with tiebreaker rules (Points → Goal Difference → Goals For)

### Production-Ready Features
- ✅ Clean architecture with domain/application/infrastructure layers
- ✅ Centralized logging with Logback
- ✅ Global exception handling
- ✅ Spring Actuator health endpoints
- ✅ Database migrations with Flyway
- ✅ Docker support with docker-compose
- ✅ Event-driven notifications using Spring Events
- ✅ Strategy pattern for tournament scheduling
- ✅ Factory pattern for tournament creation

### Available Bot Commands
- `/start` - Register and start using the bot
- `/profile` - View your user profile

## Architecture

The project follows clean architecture principles with three main layers:

```
├── domain/           # Business entities and repository interfaces
│   ├── model/       # JPA entities (User, Tournament, Team, Match, etc.)
│   ├── enums/       # Enums (Role, TournamentType, MatchLifecycleState)
│   └── repository/  # JPA repositories
├── application/     # Business logic and use cases
│   ├── service/     # Service layer
│   ├── strategy/    # Tournament scheduling strategies
│   ├── factory/     # Tournament factory and builder
│   └── event/       # Application events and listeners
└── infrastructure/  # External interfaces and configurations
    ├── telegram/    # Telegram bot adapter and commands
    ├── config/      # Spring configuration
    └── exception/   # Global exception handling
```

## Prerequisites

- **Java 17+** (JDK 17 or higher)
- **Maven 3.9+** (or use the included Maven wrapper)
- **PostgreSQL 15+** (or use Docker)
- **Telegram Bot Token** (obtain from [@BotFather](https://t.me/botfather))

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/mukhiddinov/Chempionat-X.git
cd Chempionat-X
```

### 2. Get a Telegram Bot Token

1. Open Telegram and search for [@BotFather](https://t.me/botfather)
2. Send `/newbot` and follow the instructions
3. Copy the bot token provided
4. Save your bot username

### 3. Configure environment variables

```bash
# Copy the example environment file
cp .env.example .env

# Edit .env and set your values
nano .env
```

Required variables:
```properties
TELEGRAM_BOT_TOKEN=your_actual_bot_token_here
TELEGRAM_BOT_USERNAME=YourBotUsername
DB_PASSWORD=your_secure_password
```

### 4. Run with Docker (Recommended)

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop services
docker-compose down
```

The application will be available at `http://localhost:8080`

## Configuration

All configuration is done via environment variables. See `.env.example` for all available options:

### Database Configuration
- `DB_HOST` - Database host (default: `localhost`)
- `DB_PORT` - Database port (default: `5432`)
- `DB_NAME` - Database name (default: `chempionat`)
- `DB_USERNAME` - Database username (default: `postgres`)
- `DB_PASSWORD` - Database password (default: `postgres`)

### Telegram Bot Configuration
- `TELEGRAM_BOT_TOKEN` - Your bot token from BotFather (**required**)
- `TELEGRAM_BOT_USERNAME` - Your bot username (default: `ChempionatXBot`)

### Application Configuration
- `LOG_LEVEL` - Logging level (default: `DEBUG`)
- `SHOW_SQL` - Show SQL queries in logs (default: `false`)

## Running the Application

### Option 1: Using Docker Compose (Recommended)

```bash
# Start PostgreSQL and the application
docker-compose up -d

# Check if services are running
docker-compose ps

# View application logs
docker-compose logs -f app

# Stop all services
docker-compose down

# Stop and remove volumes (fresh start)
docker-compose down -v
```

### Option 2: Using Maven (Local Development)

**Step 1: Start PostgreSQL**

Using Docker:
```bash
docker run -d \
  --name chempionat-postgres \
  -e POSTGRES_DB=chempionat \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine
```

Or install PostgreSQL locally and create a database named `chempionat`.

**Step 2: Set environment variables**

On Linux/macOS:
```bash
export TELEGRAM_BOT_TOKEN="your_bot_token_here"
export TELEGRAM_BOT_USERNAME="YourBotUsername"
export DB_PASSWORD="postgres"
```

On Windows (CMD):
```cmd
set TELEGRAM_BOT_TOKEN=your_bot_token_here
set TELEGRAM_BOT_USERNAME=YourBotUsername
set DB_PASSWORD=postgres
```

On Windows (PowerShell):
```powershell
$env:TELEGRAM_BOT_TOKEN="your_bot_token_here"
$env:TELEGRAM_BOT_USERNAME="YourBotUsername"
$env:DB_PASSWORD="postgres"
```

**Step 3: Build and run**

```bash
# Using Maven wrapper (recommended)
./mvnw clean install
./mvnw spring-boot:run

# Or if you have Maven installed
mvn clean install
mvn spring-boot:run
```

The application will start on port 8080.

## Development

### Building the project

```bash
# Clean and build
./mvnw clean install

# Build without tests
./mvnw clean install -DskipTests

# Package as JAR
./mvnw clean package
```

### Code Structure

The project uses:
- **Lombok** - Reduce boilerplate code
- **JPA/Hibernate** - ORM for database access
- **Flyway** - Database migration management
- **Spring Boot Actuator** - Production monitoring
- **Logback** - Logging framework

### Design Patterns Used

1. **Strategy Pattern** - Tournament scheduling (`LeagueScheduleStrategy`, `PlayoffScheduleStrategy`)
2. **Factory Pattern** - Tournament creation (`TournamentFactory`, `TournamentBuilder`)
3. **Adapter Pattern** - Telegram API integration (`TelegramBot`)
4. **Facade Pattern** - Command routing (`TelegramCommandRouter`)
5. **Observer Pattern** - Event-driven notifications (`UserStartedEvent`, `NotificationEventListener`)
6. **Repository Pattern** - Data access layer

## Testing

### Automated Integration Tests (New! ✨)

Run complete tournament simulations with fake users - no Telegram account needed!

```bash
# Run all simulation tests (4 test scenarios)
./mvnw test -Dtest=TournamentSimulationTest

# Run main league tournament simulation
./mvnw test -Dtest=TournamentSimulationTest#testCompleteLeagueTournamentSimulation
```

**What it does:**
- Creates fake admin + 4 players
- Simulates full tournament lifecycle
- Generates matches, submits results
- Calculates and displays standings
- Runs in ~13 seconds with H2 in-memory database

See [TOURNAMENT_SIMULATION.md](./TOURNAMENT_SIMULATION.md) for quick start or [integration test README](./src/test/java/com/chempionat/bot/integration/README.md) for details.

### Manual Testing with Real Bot

See [TESTING_GUIDE.md](./TESTING_GUIDE.md) for comprehensive manual testing scenarios with real Telegram bot.

### Unit Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=StandingsComparatorTest
```

### Test Coverage

The project includes:
- ✅ **Integration tests**: Full tournament simulation with fake users
- ✅ **Unit tests**: Standings calculation logic
- ✅ **Unit tests**: Tiebreaker rules (Points → Goal Difference → Goals For)
- ✅ **Unit tests**: Team standing calculations

### Test Configuration

Tests use H2 in-memory database for fast execution. See `src/test/resources/application.yml`.

## Docker Support

### Dockerfile

The application uses a multi-stage build approach for optimal image size:
- Base image: `eclipse-temurin:17-jre-alpine`
- Build artifacts are copied from the target directory
- Exposes port 8080

### docker-compose.yml

Services:
1. **postgres** - PostgreSQL 15 database with health checks
2. **app** - Spring Boot application

Volumes:
- `postgres_data` - Persistent database storage

Networks:
- `chempionat-network` - Bridge network for service communication

### Docker Commands

```bash
# Build images
docker-compose build

# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Remove volumes
docker-compose down -v

# Rebuild and restart
docker-compose up -d --build
```

## API Endpoints

### Health & Monitoring

- `GET /actuator/health` - Health check endpoint
- `GET /actuator/info` - Application information
- `GET /actuator/metrics` - Application metrics

Example:
```bash
curl http://localhost:8080/actuator/health
```

Response:
```json
{
  "status": "UP"
}
```

## Project Structure

```
chempionat-x/
├── src/
│   ├── main/
│   │   ├── java/com/chempionat/bot/
│   │   │   ├── ChempionatXApplication.java
│   │   │   ├── domain/
│   │   │   │   ├── model/          # JPA entities
│   │   │   │   ├── enums/          # Enumerations
│   │   │   │   └── repository/     # JPA repositories
│   │   │   ├── application/
│   │   │   │   ├── service/        # Business logic
│   │   │   │   ├── strategy/       # Tournament strategies
│   │   │   │   ├── factory/        # Factories and builders
│   │   │   │   └── event/          # Event handling
│   │   │   └── infrastructure/
│   │   │       ├── telegram/       # Telegram bot integration
│   │   │       ├── config/         # Spring configuration
│   │   │       └── exception/      # Exception handlers
│   │   └── resources/
│   │       ├── application.yml     # Application configuration
│   │       ├── logback-spring.xml  # Logging configuration
│   │       └── db/migration/       # Flyway migrations
│   └── test/
│       ├── java/                   # Unit tests
│       └── resources/              # Test configuration
├── .env.example                    # Example environment variables
├── .gitignore                      # Git ignore rules
├── docker-compose.yml              # Docker Compose configuration
├── Dockerfile                      # Docker image definition
├── pom.xml                         # Maven configuration
└── README.md                       # This file
```

## Database Schema

### Tables

1. **users** - User accounts with Telegram integration
2. **tournaments** - Tournament definitions
3. **teams** - Team/participant registrations
4. **matches** - Match scheduling and tracking
5. **match_results** - Match scores and results
6. **media** - Screenshots and media files

### Match Lifecycle States

- `CREATED` - Match scheduled but not played
- `PLAYED` - Match completed, result submitted
- `PENDING_APPROVAL` - Result awaiting approval
- `APPROVED` - Result approved and finalized
- `REJECTED` - Result rejected, needs resubmission

## Troubleshooting

### Bot not responding

1. Check if the bot token is correct in `.env`
2. Verify the application is running: `docker-compose ps`
3. Check logs: `docker-compose logs -f app`
4. Ensure your bot is not running elsewhere

### Database connection errors

1. Check if PostgreSQL is running: `docker-compose ps`
2. Verify database credentials in `.env`
3. Check database logs: `docker-compose logs -f postgres`

### Port already in use

If port 8080 is in use, modify `docker-compose.yml`:
```yaml
services:
  app:
    ports:
      - "9090:8080"  # Change 8080 to any available port
```

## Future Enhancements

- [ ] Head-to-head tiebreaker implementation
- [ ] Tournament bracket visualization
- [ ] Real-time match notifications
- [ ] Admin panel for tournament management
- [ ] Player statistics and leaderboards
- [ ] Multi-language support
- [ ] Tournament templates and presets

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
- Create an issue on GitHub
- Check existing documentation
- Review closed issues for solutions

---

**Built with ❤️ using Spring Boot and Telegram Bot API**