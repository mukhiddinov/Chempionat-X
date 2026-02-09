# Tournament Simulation Tests

This directory contains integration tests that simulate complete tournament flows with fake users, demonstrating the entire lifecycle from tournament creation to match result submission and standings calculation.

## Overview

The `TournamentSimulationTest` class provides automated testing for the tournament management system without requiring a real Telegram bot or actual users. It creates fake users, simulates their interactions, and verifies that the tournament logic works correctly.

## Test Scenarios

### 1. **Complete League Tournament Simulation** (`testCompleteLeagueTournamentSimulation`)

Simulates a full league tournament lifecycle:

1. **Admin creates a league tournament**
   - Tournament type: LEAGUE
   - Verifies tournament is created in inactive state

2. **Four players join the tournament**
   - Creates 4 teams: Team Alpha, Team Beta, Team Gamma, Team Delta
   - Each team is associated with a different user

3. **Admin starts the tournament**
   - Activates the tournament
   - Generates round-robin match fixtures (6 matches for 4 teams)

4. **Simulates match results**
   - Match 1: Home team wins 3-1
   - Match 2: Away team wins 0-2
   - Match 3: Draw 2-2
   - All results are submitted and approved automatically

5. **Calculates and verifies standings**
   - Displays league table with points, wins, draws, losses
   - Verifies standings logic (3 points for win, 1 for draw)
   - Checks goal difference calculations

**Expected Output:**
```
=== Starting Complete League Tournament Simulation ===

Step 1: Admin creates tournament...
✓ Tournament created: Test League 2026 (ID: 1)

Step 2: Players join tournament...
✓ 4 teams joined the tournament

Step 3: Admin starts tournament...
✓ Tournament started

Step 4: Verify match generation...
✓ Generated 6 matches

--- Match Fixtures ---
Match #1: Team Alpha vs Team Beta on 2026-01-10T14:10:09
Match #2: Team Alpha vs Team Gamma on 2026-01-10T14:10:09
...

Step 5: Simulate match results...
Simulating Match 1: Team Alpha vs Team Beta
✓ Result: 3-1 (Home win)
...

Step 7: Generate standings...

--- STANDINGS ---
Team                 |     P |     W |     D |     L |    GF |    GA |   Pts
--------------------------------------------------------------------------------
Team Alpha           |     3 |     1 |     1 |     1 |     5 |     5 |     4
Team Gamma           |     1 |     1 |     0 |     0 |     2 |     0 |     3
Team Delta           |     1 |     0 |     1 |     0 |     2 |     2 |     1
Team Beta            |     1 |     0 |     0 |     1 |     1 |     3 |     0

=== Tournament Simulation Completed Successfully ===
```

### 2. **Playoff Tournament Simulation** (`testPlayoffTournamentSimulation`)

Tests playoff/cup tournament format:
- Creates a PLAYOFF type tournament
- 4 teams join
- Tournament is started
- Verifies playoff bracket generation

### 3. **Match Result Rejection Flow** (`testMatchResultRejection`)

Tests the result submission and rejection workflow:
- Creates and starts a tournament
- Player submits a match result
- Admin rejects the result with a reason
- Verifies rejection is saved with comment

### 4. **Duplicate Join Prevention** (`testUserCannotJoinTournamentTwice`)

Tests security logic:
- User joins a tournament successfully
- Same user attempts to join again
- System throws IllegalStateException preventing duplicate join

## Running the Tests

### Run all simulation tests:
```bash
./mvnw test -Dtest=TournamentSimulationTest
```

### Run a specific test:
```bash
# League tournament test
./mvnw test -Dtest=TournamentSimulationTest#testCompleteLeagueTournamentSimulation

# Playoff tournament test
./mvnw test -Dtest=TournamentSimulationTest#testPlayoffTournamentSimulation

# Rejection flow test
./mvnw test -Dtest=TournamentSimulationTest#testMatchResultRejection

# Duplicate prevention test
./mvnw test -Dtest=TournamentSimulationTest#testUserCannotJoinTournamentTwice
```

### Run with detailed output:
```bash
./mvnw test -Dtest=TournamentSimulationTest -X
```

## Test Configuration

The tests use a special "test" profile that:

- **Disables Telegram Bot**: The `TelegramBotConfig` is excluded via `@Profile("!test")` annotation
- **Uses H2 In-Memory Database**: Tests run against H2 instead of PostgreSQL
- **Transaction Rollback**: Each test is `@Transactional` so changes are rolled back automatically
- **Isolated Environment**: Each test starts with a clean database state

### Test Profile Configuration (`application-test.yml`):
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
    
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
        
  flyway:
    enabled: false

telegram:
  bot:
    token: test-token-fake
    username: TestChempionatBot
```

## Fake Users Created

Each test creates the following fake users:

| User      | Telegram ID | Username       | First Name | Last Name | Role  |
|-----------|-------------|----------------|------------|-----------|-------|
| Admin     | 1000001     | admin_user     | Admin      | User      | ADMIN |
| Player 1  | 1000002     | player_one     | Player     | One       | USER  |
| Player 2  | 1000003     | player_two     | Player     | Two       | USER  |
| Player 3  | 1000004     | player_three   | Player     | Three     | USER  |
| Player 4  | 1000005     | player_four    | Player     | Four      | USER  |

## Key Assertions

The tests verify:

✅ Tournaments are created in inactive state  
✅ Tournaments activate only when started  
✅ Round-robin generates correct number of matches (n*(n-1)/2)  
✅ Match results are saved correctly  
✅ Standings calculate points properly (Win=3, Draw=1, Loss=0)  
✅ Goal difference is calculated accurately  
✅ Users cannot join the same tournament twice  
✅ Result rejection stores rejection reason  

## Benefits of Simulation Tests

1. **No External Dependencies**: Tests don't require Telegram API or real users
2. **Fast Execution**: All tests run in ~15 seconds
3. **Reproducible**: Same results every time
4. **Comprehensive**: Tests full workflow from creation to completion
5. **Automated**: Can be run in CI/CD pipelines
6. **Documentation**: Serves as living documentation of how the system works

## Troubleshooting

### Test fails with "Telegram API" error
- Ensure `TelegramBotConfig` has `@Profile("!test")` annotation
- Verify test is using `@ActiveProfiles("test")`

### Database errors
- Check H2 dependency is in `pom.xml` with `<scope>test</scope>`
- Ensure `application-test.yml` is properly configured

### Transaction errors
- Verify `@Transactional` annotation is present on test class
- Check that services are properly annotated with `@Transactional`

## Extending the Tests

To add new test scenarios:

1. Create a new `@Test` method in `TournamentSimulationTest`
2. Use the existing fake users or create new ones
3. Call service methods directly (no bot commands needed)
4. Add assertions to verify expected behavior
5. Use `System.out.println()` for human-readable output

Example:
```java
@Test
void testMyNewScenario() {
    System.out.println("\n=== My New Test Scenario ===\n");
    
    // Create tournament
    Tournament tournament = tournamentService.createTournament(
        "My Tournament", "Description", TournamentType.LEAGUE, admin);
    
    // Test your scenario
    // ...
    
    // Add assertions
    assertNotNull(tournament);
    
    System.out.println("\n=== Test Completed ===\n");
}
```

## See Also

- [TESTING_GUIDE.md](../../../TESTING_GUIDE.md) - Manual testing guide with real Telegram bot
- [README.md](../../../README.md) - Main project documentation
- [IMPLEMENTATION_SUMMARY.md](../../../IMPLEMENTATION_SUMMARY.md) - Architecture details
