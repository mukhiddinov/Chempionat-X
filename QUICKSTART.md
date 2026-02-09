# 🚀 Quick Start Guide - Chempionat-X

## Prerequisites
✅ Docker and Docker Compose installed  
✅ Telegram Bot Token from @BotFather  

## 5-Minute Setup

### Step 1: Get Bot Token
```bash
# Open Telegram and message @BotFather
# Send: /newbot
# Follow instructions
# Copy the token provided
```

### Step 2: Configure
```bash
cd /home/mukhiddinov/IdeaProjects/Chempionat-X

# Create environment file
cp .env.example .env

# Edit .env and add your bot token
nano .env
# Set: TELEGRAM_BOT_TOKEN=your_actual_token_here
```

### Step 3: Start
```bash
# Start PostgreSQL + Application
docker-compose up -d

# Check if running
docker-compose ps

# View logs
docker-compose logs -f app
```

### Step 4: Test Bot
```
1. Open your bot in Telegram
2. Send: /start
3. You should see the welcome message with main menu!
```

---

## Common Commands for Development

### View Application Logs:
```bash
docker-compose logs -f app
```

### View Database Logs:
```bash
docker-compose logs -f postgres
```

### Restart Application:
```bash
docker-compose restart app
```

### Stop Everything:
```bash
docker-compose down
```

### Fresh Start (Delete Database):
```bash
docker-compose down -v
docker-compose up -d
```

### Rebuild After Code Changes:
```bash
./mvnw clean package -DskipTests
docker-compose up -d --build
```

---

## Testing Bot Features

### 1. Create Admin User (First time):
```sql
# Connect to PostgreSQL
docker-compose exec postgres psql -U postgres -d chempionat

# Make yourself admin (replace YOUR_TELEGRAM_ID)
UPDATE users SET role = 'ADMIN' WHERE telegram_id = YOUR_TELEGRAM_ID;
```

### 2. Create a Tournament (as Admin):
```
Bot: /createtournament
Bot: "Turnir nomini kiriting:"
You: "Test Liga"
Bot: "Tavsifini kiriting:"
You: "Test tournament"
Bot: Shows format buttons
You: Click "🏆 Liga formati"
Bot: "✅ Turnir yaratildi! ID: 1"
```

### 3. Join Tournament (as Player):
```
Bot: /tournaments
Bot: Shows tournament list
You: Click tournament
Bot: Shows details + "✅ Qo'shilish" button
You: Click join
Bot: "Jamoa nomini kiriting:"
You: "My Team"
Bot: "✅ Qo'shildingiz!"
```

### 4. Start Tournament (as Admin):
```
Bot: /starttournament 1
Bot: "✅ Turnir boshlandi! O'yinlar yaratildi."
```

### 5. Submit Result (as Home Player):
```
Bot: /todaysmatches
Bot: Shows matches with "🏟 Natijani yuborish"
You: Click button
Bot: "📸 Screenshot yuboring:"
You: Send any image
Bot: "Natijani kiriting (X:Y):"
You: "3:1"
Bot: "✅ Yuborildi!"
```

### 6. Approve Result (as Admin):
```
Bot: /pendingresults
Bot: Shows pending result with photo
You: Click "✅ Tasdiqlash"
Bot: "✅ Tasdiqlandi!"
```

### 7. View Standings:
```
Bot: /standings 1
Bot: Shows formatted table with team positions
```

---

## Troubleshooting

### Bot not responding?
```bash
# Check if bot token is correct
cat .env | grep TELEGRAM_BOT_TOKEN

# Check if application is running
docker-compose ps

# Check logs for errors
docker-compose logs app | tail -50
```

### Database connection issues?
```bash
# Check if PostgreSQL is running
docker-compose ps postgres

# Verify database credentials
docker-compose exec postgres psql -U postgres -d chempionat -c '\dt'
```

### Port 8080 already in use?
```bash
# Edit docker-compose.yml and change:
services:
  app:
    ports:
      - "9090:8080"  # Change external port
```

### Application won't start?
```bash
# Check Java version (need 17+)
java -version

# Rebuild from scratch
./mvnw clean install
docker-compose down -v
docker-compose up -d --build
```

---

## Environment Variables Reference

Required:
```properties
TELEGRAM_BOT_TOKEN=your_bot_token_here  # Required!
```

Optional (have defaults):
```properties
TELEGRAM_BOT_USERNAME=ChempionatXBot
DB_HOST=localhost
DB_PORT=5432
DB_NAME=chempionat
DB_USERNAME=postgres
DB_PASSWORD=postgres
LOG_LEVEL=INFO
```

---

## Database Access

### Connect to PostgreSQL:
```bash
docker-compose exec postgres psql -U postgres -d chempionat
```

### Useful Queries:
```sql
-- View all users
SELECT id, telegram_id, username, role FROM users;

-- View all tournaments
SELECT id, name, type, is_active FROM tournaments;

-- View all teams
SELECT t.id, t.name, u.username, tour.name as tournament
FROM teams t
JOIN users u ON t.user_id = u.id
JOIN tournaments tour ON t.tournament_id = tour.id;

-- View all matches
SELECT m.id, 
       ht.name as home_team, 
       at.name as away_team,
       m.home_score,
       m.away_score,
       m.state
FROM matches m
JOIN teams ht ON m.home_team_id = ht.id
JOIN teams at ON m.away_team_id = at.id;
```

---

## Health Check

Application exposes health endpoint:
```bash
curl http://localhost:8080/actuator/health

# Expected response:
{"status":"UP"}
```

---

## Next Steps

1. ✅ Make yourself admin (see step 1 above)
2. ✅ Create a test tournament
3. ✅ Invite friends to join via your bot
4. ✅ Start the tournament
5. ✅ Test result submission
6. ✅ Test approval workflow
7. ✅ View standings

---

## Production Deployment

### On VPS/Cloud Server:
```bash
# 1. Clone repository
git clone <your-repo-url>
cd Chempionat-X

# 2. Set environment variables
cp .env.example .env
nano .env  # Add production values

# 3. Start with docker-compose
docker-compose -f docker-compose.yml up -d

# 4. Setup reverse proxy (optional)
# Nginx/Caddy for HTTPS if needed
```

### Using Railway/Render:
1. Connect your GitHub repository
2. Set environment variables in dashboard
3. Deploy automatically on push

---

## Support

- 📖 Full documentation: `README.md`
- 🎯 Feature summary: `IMPLEMENTATION_SUMMARY.md`
- 🐛 Issues: Check application logs
- 💬 Questions: Review code comments

**Happy Tournament Managing! ⚽🏆**
