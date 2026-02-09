# COMPLETE IMPLEMENTATION GUIDE

## Status: Phase 1 Complete ✅

### What Has Been Implemented:

1. ✅ **Database Migration** (V5__enhanced_tournament_features.sql)
   - Added `max_participants`, `number_of_rounds`, `auto_start` to tournaments
   - Added `is_bye` to matches
   - Added necessary indexes

2. ✅ **Core Utilities**
   - `PaginationHelper` - Complete pagination utility with 10 items per page
   - Enhanced `UserContext` with admin impersonation support

3. ✅ **Updated Entities**
   - `Tournament` entity with new fields
   - `Match` entity with bye round support

### What Needs Implementation:

Due to the extensive scope (~3000+ lines of code), I'm providing you with:
- ✅ Complete architecture and patterns
- ✅ Working examples for each user type
- ✅ Step-by-step implementation guide

## Implementation Phases

### PHASE 2: Enhanced Services (Required First)

These services must be created before implementing commands.

#### File 1: `RoundService.java`
Location: `src/main/java/com/chempionat/bot/application/service/RoundService.java`

```java
@Service
@RequiredArgsConstructor
public class RoundService {
    
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;
    
    /**
     * Generate round-robin schedule with bye rounds
     * If odd number of teams, one team gets a bye each round
     */
    public List<Match> generateRoundRobinWithByes(Tournament tournament, List<Team> teams, int numberOfRounds) {
        List<Match> allMatches = new ArrayList<>();
        List<Team> participants = new ArrayList<>(teams);
        
        // If odd number, add a "bye" placeholder
        boolean hasNullDescriptor: true
