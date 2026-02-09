# Chempionat-X Bot Testing Guide

## Current Database State
✅ **Bot Status:** Running successfully  
✅ **Admin User:** @khusniddin_mukhiddinov (ID: 1059249931)  
✅ **Existing Tournaments:** 1 tournament ("111" - League type)

## Test Scenarios

### 1️⃣ Basic User Flow (New User Registration)

**Objective:** Test new user registration and basic navigation

1. **Start Bot** (from a different Telegram account)
   - Send: `/start`
   - ✅ Expected: Welcome message in Uzbek with main menu keyboard
   - ✅ Buttons should appear: 🏆 Turnirlar, 📅 Bugungi o'yinlarim, ⚙️ Profil

2. **View Profile**
   - Click: `⚙️ Profil` button
   - ✅ Expected: User profile with role=USER

3. **View Tournaments**
   - Click: `🏆 Turnirlar` button
   - ✅ Expected: List of available tournaments (should see "111" tournament)
   - Click: Tournament button
   - ✅ Expected: Tournament details with "Qo'shilish" (Join) button

---

### 2️⃣ Tournament Participation Flow

**Objective:** Test joining tournament and viewing matches

4. **Join Tournament**
   - Click: `Qo'shilish` button on tournament
   - ✅ Expected: Success message confirming registration
   - ✅ Check: Cannot join same tournament twice

5. **View My Tournaments**
   - Send: `/mytournaments`
   - ✅ Expected: List showing joined tournament

6. **View Schedule**
   - Click tournament → Click `📅 Jadval` button
   - ✅ Expected: Match schedule (may be empty if tournament not started)

---

### 3️⃣ Organizer Request Flow

**Objective:** Test organizer role request and approval

7. **Request Organizer Role** (as USER)
   - Send: `/requestorganizer`
   - ✅ Expected: Confirmation message saying request submitted
   - ✅ Check admin account: Should receive notification with ✅ Qabul qilish / ❌ Rad etish buttons

8. **Approve Organizer Request** (as ADMIN - @khusniddin_mukhiddinov)
   - Check: Notification message from bot
   - Click: `✅ Qabul qilish` button
   - ✅ Expected: "Role granted" confirmation
   - ✅ Check user account: Should receive approval notification
   - Send `/profile` as user: Role should now be ORGANIZER

---

### 4️⃣ Tournament Creation Flow (Admin/Organizer)

**Objective:** Test tournament creation wizard

9. **Create Tournament** (as ADMIN or ORGANIZER)
   - Send: `/createtournament`
   - ✅ Expected: Prompt for tournament name

10. **Enter Tournament Name**
    - Type: `Test Liga 2026`
    - ✅ Expected: Prompt for description

11. **Enter Description**
    - Type: `Yangi yil turniri`
    - ✅ Expected: Tournament type selection (🏆 Liga / 🏅 Olimpiya)

12. **Select Type**
    - Click: `🏆 Liga`
    - ✅ Expected: Success message with tournament ID and `/starttournament <id>` instruction

---

### 5️⃣ Tournament Start Flow

**Objective:** Test tournament launch and match generation

13. **Get Players to Join** (need at least 2 players)
    - Have 2+ users join the tournament (use `/jointournament <id>`)
    - ✅ Expected: Each user gets "Successfully joined" message

14. **Start Tournament** (as ADMIN or tournament ORGANIZER)
    - Send: `/starttournament <tournament_id>`
    - ✅ Expected: Success message confirming tournament started
    - ✅ Expected: Matches automatically generated based on format
    - Check schedule: Should now show generated matches

---

### 6️⃣ Match Result Submission Flow

**Objective:** Test photo upload and score submission

15. **View Today's Matches** (as player)
    - Click: `📅 Bugungi o'yinlarim` button
    - ✅ Expected: List of matches scheduled for today

16. **Submit Result** (requires started tournament with matches)
    - Send: `/submitresult <match_id>`
    - ✅ Expected: Prompt to upload screenshot

17. **Upload Screenshot**
    - Upload any photo
    - ✅ Expected: Prompt to enter score in format "3:1"

18. **Enter Score**
    - Type: `3:1`
    - ✅ Expected: Success message confirming submission
    - ✅ Check organizer account: Should receive notification with photo + approve/reject buttons

---

### 7️⃣ Result Approval Flow

**Objective:** Test organizer approval workflow

19. **Approve Result** (as tournament ORGANIZER or ADMIN)
    - Check: Notification with match result photo
    - Click: `✅ Tasdiqlash` button
    - ✅ Expected: "Result approved" confirmation
    - ✅ Check submitter account: Should receive approval notification

20. **View Standings**
    - Send: `/standings <tournament_id>` or click `🏆 Jadval` button
    - ✅ Expected: League table with updated points/goals
    - ✅ Check: Winning team got 3 points, goals counted correctly

---

### 8️⃣ Result Rejection Flow

**Objective:** Test rejection with reason

21. **Submit Another Result** (as player)
    - Follow steps 16-18 again with different match

22. **Reject Result** (as ORGANIZER)
    - Click: `❌ Rad etish` button on notification
    - ✅ Expected: Prompt to enter rejection reason

23. **Enter Rejection Reason**
    - Type: `Rasm aniq emas` (Photo not clear)
    - ✅ Expected: Confirmation message
    - ✅ Check submitter account: Should receive rejection notification with reason

---

## Quick Database Inspection Commands

```bash
# Check users and roles
docker-compose exec -T postgres psql -U postgres -d postgres -c \
  "SELECT telegram_id, username, role FROM users;"

# Check tournaments
docker-compose exec -T postgres psql -U postgres -d postgres -c \
  "SELECT id, name, type, is_active, created_by_user_id FROM tournaments;"

# Check teams (players joined)
docker-compose exec -T postgres psql -U postgres -d postgres -c \
  "SELECT t.id, t.name, u.username FROM teams t JOIN users u ON t.user_id = u.id;"

# Check matches
docker-compose exec -T postgres psql -U postgres -d postgres -c \
  "SELECT id, tournament_id, home_team_id, away_team_id, home_score, away_score, match_date FROM matches;"

# Check match results (pending/approved/rejected)
docker-compose exec -T postgres psql -U postgres -d postgres -c \
  "SELECT id, match_id, submitted_by_user_id, home_score, away_score, status FROM match_results;"

# Check organizer requests
docker-compose exec -T postgres psql -U postgres -d postgres -c \
  "SELECT id, user_id, status, requested_at FROM organizer_requests;"
```

## Monitoring Logs

```bash
# Follow live logs
docker-compose logs --follow app

# Check last 100 lines
docker-compose logs --tail=100 app

# Search for errors
docker-compose logs app | grep -i error

# Search for specific command
docker-compose logs app | grep "createtournament"
```

## Testing Checklist

### Core Features
- [ ] User registration via `/start`
- [ ] Profile viewing
- [ ] Tournament listing
- [ ] Tournament joining
- [ ] Tournament creation (wizard flow)
- [ ] Tournament starting with match generation

### Role System
- [ ] Organizer request submission
- [ ] Admin receives notification
- [ ] Admin approves request
- [ ] User receives approval notification
- [ ] Role updated in database

### Match System
- [ ] Schedule viewing
- [ ] Today's matches filtering
- [ ] Result submission with photo
- [ ] Organizer receives notification with photo
- [ ] Result approval
- [ ] Result rejection with reason
- [ ] Standings update after approval

### UI/UX
- [ ] Main menu keyboard appears
- [ ] Inline keyboards work for tournament actions
- [ ] Callback queries handled correctly
- [ ] Button text mapping (Uzbek → commands)
- [ ] Conversation state management (multi-step flows)

### Authorization
- [ ] Only ADMIN/ORGANIZER can create tournaments
- [ ] Only tournament owner can start their tournament
- [ ] ADMIN can manage all tournaments
- [ ] Only match participants can submit results
- [ ] Only organizer/admin can approve results

## Common Issues & Solutions

### Issue: Bot not responding
```bash
# Check if containers running
docker-compose ps

# Restart bot
docker-compose restart app

# Check logs for errors
docker-compose logs --tail=50 app
```

### Issue: Notifications not received
- Verify recipient has role (ADMIN for organizer requests, ORGANIZER for results)
- Check logs for "notifyAdmins" or "notifyOrganizer" messages
- Ensure user has started bot with `/start` first

### Issue: Callbacks not working
- Check TelegramCommandRouter logs for "Handling callback"
- Verify callback format: "action:id"
- Ensure command registered in router

### Issue: Can't start tournament
- Verify you're the tournament creator (or ADMIN)
- Check if tournament already started (is_active=true)
- Ensure at least 2 players joined

## Test Data Setup

If you want to reset and start fresh:

```bash
# Stop containers
docker-compose down

# Remove database volume
docker volume rm chempionat-x_postgres-data

# Start fresh
docker-compose up -d

# Wait for migrations to run (check logs)
docker-compose logs --follow app
```

## Success Criteria

✅ All 18 commands working  
✅ Multi-step conversations maintain state  
✅ Automatic notifications delivered  
✅ Photos handled correctly  
✅ Inline keyboards functional  
✅ Role-based access enforced  
✅ Database updates correctly  
✅ No errors in logs during normal operation

---

**Ready to test!** Start with Scenario 1 and work your way through. Report any issues you encounter.
