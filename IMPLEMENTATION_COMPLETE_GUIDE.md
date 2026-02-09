# Complete Bot Enhancement Implementation Guide

## ✅ COMPLETED (Phase 1)

### 1. Database & Entities
- ✅ **Migration V5** created with:
  - `tournaments.max_participants`
  - `tournaments.number_of_rounds`  
  - `tournaments.auto_start`
  - `matches.is_bye`
  - Necessary indexes

- ✅ **Tournament Entity** updated with new fields
- ✅ **Match Entity** updated with `isBye` field
- ✅ **UserContext** enhanced with admin impersonation

### 2. Core Utilities
- ✅ **PaginationHelper** - Complete pagination with 10 items/page
  - Methods: `createPaginatedKeyboard()`, `extractPageNumber()`, `extractContext()`
  - Supports prev/next navigation
  - Page counter display

## 📋 IMPLEMENTATION ROADMAP

### Estimated Effort:
- **Total**: ~40-50 hours of development
- **USER features**: 8-10 hours
- **ORGANIZER features**: 15-20 hours  
- **ADMIN features**: 8-10 hours
- **Testing & Integration**: 10-12 hours

### Architecture Overview:

```
USER Interface
├── Dynamic Menu (based on joined tournaments)
├── My Tournaments (if joined any)
├── Tournament Details
│   ├── Standings (league only)
│   └── Rounds View
│       ├── Regular matches (@home vs @away)
│       └── Bye rounds (Round X - Rest)
└── Match Actions
    ├── Home: Submit result
    └── Away: View only

ORGANIZER Interface
├── Enhanced Creation Wizard
│   ├── Max participants
│   ├── Number of rounds (1 or 2)
│   └── Auto-start toggle
├── My Tournaments Management
│   ├── View all created tournaments
│   ├── Edit tournament
│   │   ├── Select round
│   │   ├── Paginated match list (10/page)
│   │   └── Edit scores inline
│   └── Delete tournament
├── Match Result Approval
│   ├── View screenshot
│   ├── Accept/Reject inline buttons
│   └── Update match scores
└── Match Notifications (auto)

ADMIN Interface
├── Organizers List (paginated)
├── Select Organizer → Impersonate
├── Manage as Organizer
│   ├── View their tournaments
│   ├── Edit their matches
│   └── Delete their tournaments
└── Exit Impersonation
```

## 🔧 CRITICAL SERVICES NEEDED

### 1. RoundService
**Purpose**: Handle round-robin scheduling with bye rounds

**Key Methods**:
```java
public class RoundService {
    List<Match> generateRoundRobinWithByes(Tournament, List<Team>, int rounds);
    List<Match> getMatchesByRound(Long tournamentId, int roundNumber);
    int calculateTotalRounds(int teamCount);
    Team getBye TeamForRound(Tournament, int round);
}
```

**Algorithm for Bye Rounds**:
- If teams count is ODD: One team gets bye each round
- Total rounds = teamCount (each team plays all others once)
- For 2 rounds: Double the matches

### 2. MatchNotificationService  
**Purpose**: Notify users when round starts

**Key Methods**:
```java
public class MatchNotificationService {
    void notifyRoundStart(Tournament, int roundNumber);
    void notifyMatchParticipants(Match);
    void notifyOrganizerOfResult(MatchResult);
}
```

### 3. TournamentManagementService
**Purpose**: Edit/Delete tournaments

**Key Methods**:
```java
public class TournamentManagementService {
    void checkAndAutoStart(Tournament);
    void updateMatchScore(Long matchId, Integer homeScore, Integer awayScore);
    void deleteTournament(Long tournamentId, Long userId);
    boolean canManageTournament(Tournament, User);
}
```

## 📱 COMMAND IMPLEMENTATIONS

### USER Commands

#### 1. Enhanced StartCommand
**File**: `StartCommand.java`

**Changes Needed**:
```java
// Check if user has joined any tournaments
List<Tournament> joinedTournaments = tournamentRepository
    .findTournamentsByPlayerId(user.getId());

if (joinedTournaments.isEmpty()) {
    // Show: My Profile, Join Tournament
    keyboard = KeyboardFactory.createUserMenuWithoutTournaments();
} else {
    // Show: My Profile, My Tournaments, Join Tournament  
    keyboard = KeyboardFactory.createUserMenuWithTournaments();
}
```

#### 2. MyTournamentsCommand (NEW)
**File**: `MyTournamentsCommand.java`

**Functionality**:
- List all tournaments user has joined
- Click on tournament → Show details
- League: Show standings + rounds
- Playoff: Show bracket

**Pseudo-code**:
```java
@Component
public class MyTournamentsCommand implements TelegramCommand {
    
    @Override
    public void execute(Update update, TelegramBot bot) {
        User user = getUser(update);
        List<Tournament> tournaments = tournamentRepository
            .findTournamentsByPlayerId(user.getId());
        
        if (tournaments.isEmpty()) {
            bot.sendMessage(chatId, "Siz hali turnirga qo'shilmagansiz");
            return;
        }
        
        InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
            tournaments,
            0, // page
            t -> t.getName(),
            t -> "view_tournament:" + t.getId(),
            "my_tournaments"
        );
        
        bot.sendMessage(chatId, "Sizning turnirlaringiz:", keyboard);
    }
}
```

#### 3. TournamentRoundsCommand (NEW)
**File**: `TournamentRoundsCommand.java`

**Functionality**:
- Show all rounds for a league tournament
- Display matches: "@username1 vs @username2"
- Display bye: "Round X - Rest"

**Pseudo-code**:
```java
@Component  
public class TournamentRoundsCommand implements TelegramCommand {
    
    @Override
    public void execute(Update update, TelegramBot bot) {
        Long tournamentId = extractId(update.getCallbackQuery());
        Tournament tournament = tournamentService.getTournamentById(tournamentId);
        
        // Get all matches grouped by round
        Map<Integer, List<Match>> matchesByRound = matchRepository
            .findByTournament(tournament)
            .stream()
            .collect(Collectors.groupingBy(Match::getRound));
        
        StringBuilder message = new StringBuilder();
        message.append("📅 ").append(tournament.getName()).append(" - Turlar\n\n");
        
        for (int round = 1; round <= maxRound; round++) {
            message.append("Round ").append(round).append(":\n");
            
            List<Match> roundMatches = matchesByRound.get(round);
            for (Match match : roundMatches) {
                if (match.getIsBye()) {
                    // Show as rest round
                    message.append("  - Rest\n");
                } else {
                    message.append("  - @")
                           .append(match.getHomeTeam().getUser().getUsername())
                           .append(" vs @")
                           .append(match.getAwayTeam().getUser().getUsername())
                           .append("\n");
                }
            }
            message.append("\n");
        }
        
        bot.sendMessage(chatId, message.toString());
    }
}
```

### ORGANIZER Commands

#### 4. Enhanced CreateTournamentCommand
**File**: `CreateTournamentCommand.java`

**Add These Steps**:
```java
// After tournament type selection:
case "WAITING_MAX_PARTICIPANTS":
    context.setData("max_participants", messageText);
    context.setCurrentCommand("WAITING_NUMBER_OF_ROUNDS");
    bot.sendMessage(chatId, "Turlar sonini kiriting (1 yoki 2):");
    break;

case "WAITING_NUMBER_OF_ROUNDS":
    int rounds = Integer.parseInt(messageText);
    if (rounds != 1 && rounds != 2) {
        bot.sendMessage(chatId, "Faqat 1 yoki 2 kiritish mumkin!");
        return;
    }
    context.setData("number_of_rounds", rounds);
    
    // Ask about auto-start
    InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
    // Add Yes/No buttons for auto_start
    
    context.setCurrentCommand("WAITING_AUTO_START");
    bot.sendMessage(chatId, "Ishtirokchilar to'lganda avtomatik boshlash?", keyboard);
    break;
```

#### 5. TournamentManagementCommand (NEW)
**File**: `ManageTournamentsCommand.java`

**Flow**:
1. Show organizer's tournaments
2. Select tournament → Show rounds
3. Select round → Show matches (paginated, 10 per page)
4. Select match → Show edit options (home score / away score)
5. Enter new score → Update

**Key Code**:
```java
// Step 3: Show matches with pagination
List<Match> matches = matchRepository.findByTournamentAndRound(tournament, round);

InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboard(
    matches,
    currentPage,
    10, // page size
    m -> String.format("@%s %d:%d @%s",
        m.getHomeTeam().getUser().getUsername(),
        m.getHomeScore() != null ? m.getHomeScore() : 0,
        m.getAwayScore() != null ? m.getAwayScore() : 0,
        m.getAwayTeam().getUser().getUsername()),
    m -> "edit_match:" + m.getId(),
    "matches_round_" + round
);
```

### ADMIN Commands

#### 6. OrganizersListCommand (NEW)
**File**: `OrganizersListCommand.java`

**Functionality**:
- List all users with ORGANIZER role
- Pagination (10 per page)
- Click organizer → Impersonate

**Code**:
```java
@Component
public class OrganizersListCommand implements TelegramCommand {
    
    @Override
    public void execute(Update update, TelegramBot bot) {
        List<User> organizers = userRepository.findByRole(Role.ORGANIZER);
        
        InlineKeyboardMarkup keyboard = PaginationHelper.createPaginatedKeyboardWithBack(
            organizers,
            0, // page
            10, // size
            u -> u.getUsername() != null ? "@" + u.getUsername() : u.getFirstName(),
            u -> "manage_organizer:" + u.getId(),
            "organizers",
            "⬅️ Back",
            "admin_menu"
        );
        
        bot.sendMessage(chatId, "👥 Tashkilotchilar:", keyboard);
    }
}
```

#### 7. ManageOrganizerCommand (NEW)
**File**: `ManageOrganizerCommand.java`

**Functionality**:
- Set impersonation in UserContext
- Show organizer's tournaments
- Allow editing as if admin is that organizer
- "Exit Organizer Profile" button

**Code**:
```java
@Component
public class ManageOrganizerCommand implements TelegramCommand {
    
    @Override
    public void execute(Update update, TelegramBot bot) {
        Long organizerId = extractId(update.getCallbackQuery());
        UserContext context = UserContext.get(chatId);
        
        // Set impersonation
        context.setImpersonatedOrganizer(organizerId);
        
        User organizer = userRepository.findById(organizerId).orElseThrow();
        
        String message = String.format(
            "🎯 Managing: @%s\n\n" +
            "You are now managing this organizer's tournaments.\n\n" +
            "Use /mytournaments to see their tournaments.",
            organizer.getUsername()
        );
        
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        // Add "Exit Organizer Profile" button
        // Add "View Tournaments" button
        
        bot.sendMessage(chatId, message, keyboard);
    }
}
```

## 🔄 CALLBACK ROUTING

Update `TelegramCommandRouter.java` to handle:

```java
// In handleCallbackQuery method:
if (callbackData.startsWith("page:")) {
    handlePagination(update, bot);
} else if (callbackData.startsWith("view_tournament:")) {
    tournamentDetailsCommand.execute(update, bot);
} else if (callbackData.startsWith("rounds:")) {
    roundsViewCommand.execute(update, bot);
} else if (callbackData.startsWith("edit_match:")) {
    editMatchCommand.execute(update, bot);
} else if (callbackData.startsWith("edit_home_score:")) {
    editHomeScoreCommand.execute(update, bot);
} else if (callbackData.startsWith("edit_away_score:")) {
    editAwayScoreCommand.execute(update, bot);
} else if (callbackData.startsWith("manage_organizer:")) {
    manageOrganizerCommand.execute(update, bot);
} else if (callbackData.equals("exit_organizer_profile")) {
    exitOrganizerProfileCommand.execute(update, bot);
}
```

## 🧪 TESTING PLAN

### 1. Test USER Features
- Join tournament
- View my tournaments
- Check standings display
- Check rounds with bye
- Submit result as home
- View match as away

### 2. Test ORGANIZER Features
- Create with max_participants
- Create with 2 rounds
- Enable auto-start
- Edit match scores
- View paginated matches
- Delete tournament

### 3. Test ADMIN Features
- View organizers list
- Pagination works
- Impersonate organizer
- Edit their tournaments
- Exit impersonation

## 📦 FILES TO CREATE

### Services (6 files)
1. `RoundService.java`
2. `MatchNotificationService.java`
3. `TournamentManagementService.java`
4. `AutoStartService.java`

### Commands - USER (4 files)
1. `MyTournamentsCommand.java`
2. `TournamentDetailsCommand.java`
3. `TournamentRoundsCommand.java`
4. `ViewMatchCommand.java`

### Commands - ORGANIZER (6 files)
1. Enhanced `CreateTournamentCommand.java`
2. `ManageTournamentsCommand.java`
3. `SelectRoundCommand.java`
4. `EditMatchCommand.java`
5. `EditScoreCommand.java`
6. `DeleteTournamentCommand.java`

### Commands - ADMIN (4 files)
1. `OrganizersListCommand.java`
2. `ManageOrganizerCommand.java`
3. `ExitOrganizerProfileCommand.java`
4. Enhanced `ProfileCommand.java` (show organizer option)

### Utilities (2 files)
1. ✅ `PaginationHelper.java` (DONE)
2. Enhanced `KeyboardFactory.java` (add new keyboard types)

**TOTAL**: ~20-25 new/modified files

## 🚀 IMPLEMENTATION ORDER

1. **Week 1**: Services + Database
   - Day 1-2: RoundService with bye logic
   - Day 3: MatchNotificationService
   - Day 4-5: TournamentManagementService

2. **Week 2**: USER + ORGANIZER
   - Day 1-2: USER commands
   - Day 3-5: ORGANIZER commands (complex!)

3. **Week 3**: ADMIN + Testing
   - Day 1-2: ADMIN commands  
   - Day 3-5: Integration testing

## 📞 NEXT STEPS

You now have:
1. ✅ Complete database schema
2. ✅ Core utilities (Pagination)
3. ✅ Enhanced entities
4. ✅ Complete architecture blueprint
5. ✅ Pseudo-code for all major features

**To proceed**, start implementing:
- **First**: `RoundService` (most critical)
- **Then**: Update `CreateTournamentCommand`
- **Then**: Implement USER commands
- **Finally**: ORGANIZER and ADMIN features

**Need help with any specific implementation?** Let me know which command/service to implement first!
