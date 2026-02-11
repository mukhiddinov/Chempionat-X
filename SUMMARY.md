# Chempionat-X Project Summary

## Purpose
Chempionat-X is a Spring Boot application designed to operate as a Telegram bot for organizing and managing football or eFootball championships. It supports both League and Playoff formats, handles match scheduling, result tracking, and user roles (Player, Organizer, Admin).

## Architecture
The project follows **Clean Architecture** principles, divided into:
- **Domain Layer**: Contains JPA entities (`User`, `Tournament`, `Team`, `Match`, etc.), repositories, and enums.
- **Application Layer**: Contains business logic, services (`UserService`), factories (`TournamentFactory`), and strategies for tournament scheduling.
- **Infrastructure Layer**: Contains external interfaces, specifically the Telegram bot adapter, command routing, and configuration.

## Current Status
The project is currently in an **incomplete and unbuildable state**.

### Missing Components:
- **Domain Enums**: `MatchStage` is missing, which is used in `Match` entity.
- **Services**: `RoundService` is missing, which is required by `LeagueScheduleStrategy`.
- **Infrastructure**:
    - `KeyboardFactory` is missing, which is used by `StartCommand` to create the UI.
    - `UserContext` is missing, which is used by `TelegramCommandRouter` for conversation state management.
- **Commands**: Most commands referenced in `TelegramCommandRouter` (like `/tournaments`, `/createtournament`, `/standings`, etc.) are missing from `com.chempionat.bot.infrastructure.telegram.commands`.

### Build Status:
- **Compilation**: Fails due to "cannot find symbol" errors for the missing classes mentioned above.
- **Tests**: `TournamentSimulationTest` mentioned in `README.md` is missing.

## Recommendations
To make the project functional, the missing enums, services, and infrastructure classes need to be implemented, and the remaining Telegram commands should be developed.
