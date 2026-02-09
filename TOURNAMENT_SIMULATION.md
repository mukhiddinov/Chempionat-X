# Quick Start: Running Tournament Simulation

## What is this?

Automated integration tests that simulate a complete tournament with fake users - no need for real Telegram accounts or manual testing!

## Run the Simulation

```bash
# Run all 4 simulation tests
./mvnw test -Dtest=TournamentSimulationTest

# Run just the main league tournament simulation
./mvnw test -Dtest=TournamentSimulationTest#testCompleteLeagueTournamentSimulation
```

## What it does

1. ✅ Creates fake users (admin + 4 players)
2. ✅ Creates a tournament
3. ✅ Players join the tournament
4. ✅ Admin starts the tournament
5. ✅ Generates 6 matches (round-robin for 4 teams)
6. ✅ Simulates match results (wins, losses, draws)
7. ✅ Calculates and displays standings table
8. ✅ Verifies all logic is working correctly

## Sample Output

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
Match #1: Team Alpha vs Team Beta
Match #2: Team Alpha vs Team Gamma
Match #3: Team Alpha vs Team Delta
Match #4: Team Beta vs Team Gamma
Match #5: Team Beta vs Team Delta
Match #6: Team Gamma vs Team Delta

Step 5: Simulate match results...
Simulating Match 1: Team Alpha vs Team Beta
✓ Result: 3-1 (Home win)

Simulating Match 2: Team Alpha vs Team Gamma
✓ Result: 0-2 (Away win)

Simulating Match 3: Team Alpha vs Team Delta
✓ Result: 2-2 (Draw)

Step 7: Generate standings...

--- STANDINGS ---
Team                 |     P |     W |     D |     L |    GF |    GA |   Pts
--------------------------------------------------------------------------------
Team Alpha           |     3 |     1 |     1 |     1 |     5 |     5 |     4
Team Gamma           |     1 |     1 |     0 |     0 |     2 |     0 |     3
Team Delta           |     1 |     0 |     1 |     0 |     2 |     2 |     1
Team Beta            |     1 |     0 |     0 |     1 |     1 |     3 |     0

✓ Standings calculations are correct

=== Tournament Simulation Completed Successfully ===

[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Why use this?

- ⚡ Fast: Runs in ~15 seconds
- 🔒 Safe: Uses in-memory H2 database, no real data affected
- 🤖 Automated: No manual clicking or typing needed
- ✅ Complete: Tests entire tournament lifecycle
- 📝 Documented: Code shows exactly how the system works

## All Available Tests

| Test Name | What it Tests |
|-----------|---------------|
| `testCompleteLeagueTournamentSimulation` | Full league tournament from creation to standings |
| `testPlayoffTournamentSimulation` | Playoff/cup format tournament |
| `testMatchResultRejection` | Result submission and rejection flow |
| `testUserCannotJoinTournamentTwice` | Duplicate join prevention |

## For More Details

See [src/test/java/com/chempionat/bot/integration/README.md](./src/test/java/com/chempionat/bot/integration/README.md) for complete documentation.

## Manual Testing

For testing with a real Telegram bot and actual users, see [TESTING_GUIDE.md](./TESTING_GUIDE.md).
