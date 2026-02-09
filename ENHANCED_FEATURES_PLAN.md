# Enhanced Bot Features Implementation Plan

## Database Schema Changes Needed

### Tournament Table
- Add `max_participants` (INTEGER) - Maximum number of participants
- Add `number_of_rounds` (INTEGER) - Number of rounds (1 or 2 for league)
- Add `auto_start` (BOOLEAN) - Auto-start when full

### Match Table  
- Add `is_bye` (BOOLEAN) - Mark if this is a bye round (rest day)
- `round` field already exists

### UserContext Enhancement
- Add `impersonated_organizer_id` for admin managing organizer's profile

## Implementation Phases

### Phase 1: USER Enhancements
1. Dynamic main menu based on user's tournament participation
2. Tournaments section (only if joined)
3. League standings display
4. Rounds display with matches
5. Bye round display

### Phase 2: ORGANIZER Features
1. Enhanced tournament creation wizard
2. Auto-start logic
3. Round-robin with bye rounds
4. Match notifications
5. Tournament management (edit/delete)
6. Match editing with pagination

### Phase 3: ADMIN Features
1. Organizers list with pagination
2. Organizer impersonation
3. Manage organizer's tournaments

## Key Classes to Create/Modify

### New Classes
- `PaginationHelper` - Utility for paginated inline keyboards
- `RoundService` - Round management and bye logic
- `MatchNotificationService` - Notify users about matches
- `TournamentManagementService` - Edit/delete tournaments

### Modified Classes
- `StartCommand` - Dynamic menu
- `KeyboardFactory` - New keyboard types
- `CreateTournamentCommand` - Enhanced wizard
- `TournamentService` - Auto-start logic
- `UserContext` - Admin impersonation

## Callback Data Format

```
tournament:<id>
round:<tournament_id>:<round_number>
match:<match_id>
edit_match:<match_id>
edit_home_score:<match_id>
edit_away_score:<match_id>
organizer:<organizer_id>
exit_organizer_profile
page:<context>:<page_number>
```

## Implementation Order

1. ✅ Database migration for new fields
2. ✅ PaginationHelper utility
3. ✅ Enhanced KeyboardFactory
4. ✅ Dynamic StartCommand
5. ✅ USER: Tournaments section (conditional)
6. ✅ USER: Rounds display
7. ✅ ORGANIZER: Enhanced creation wizard
8. ✅ ORGANIZER: Match editing
9. ✅ ADMIN: Organizer management
