# SplitFriend Quick Start Guide

Get SplitFriend up and running in minutes.

## Prerequisites

- **Java 17+** - [Download from Adoptium](https://adoptium.net/)
- **Maven 3.6+** - [Download from Apache](https://maven.apache.org/download.cgi)

Verify your installation:
```bash
java -version   # Should show 17 or higher
mvn -version    # Should show 3.6 or higher
```

## Option 1: Run with Maven (Development)

### Step 1: Clone the Repository
```bash
git clone <repository-url>
cd split_2_friend
```

### Step 2: Start the Application
```bash
mvn spring-boot:run
```

### Step 3: Access the Application
Open your browser and navigate to:
```
http://localhost:8080
```

### Step 4: Log In
Use the default administrator credentials:
- **Email**: `admin@splitfriend.local`
- **Password**: `admin123`

> **Important**: Change these credentials after first login!

---

## Option 2: Run with Docker

### Step 1: Build and Run
```bash
# Using Docker Compose (recommended)
docker-compose up -d

# Or using Docker directly
docker build -t splitfriend .
docker run -d -p 8080:8080 -v splitfriend-data:/data --name splitfriend splitfriend
```

### Step 2: Access the Application
```
http://localhost:8080
```

### Step 3: View Logs
```bash
docker-compose logs -f
# or
docker logs -f splitfriend
```

### Step 4: Stop the Application
```bash
docker-compose down
# or
docker stop splitfriend
```

---

## Option 3: Run as JAR (Production)

### Step 1: Build the JAR
```bash
mvn clean package -DskipTests
```

### Step 2: Run the JAR
```bash
java -jar target/splitfriend-1.0.0.jar
```

### Step 3: Configure for Production
Create an `application-prod.yml` file:
```yaml
spring:
  datasource:
    url: jdbc:h2:file:/var/lib/splitfriend/data

app:
  admin:
    default-email: admin@yourdomain.com
    default-password: your-secure-password-here
```

Run with the production profile:
```bash
java -jar target/splitfriend-1.0.0.jar --spring.profiles.active=prod
```

---

## First Steps After Login

### 1. Change Admin Password
1. Click your username in the top-right corner
2. Select "Profile"
3. Update your password

### 2. Enable Two-Factor Authentication (Recommended)
1. Click your username → "2FA Settings"
2. Scan the QR code with Google Authenticator or similar app
3. Enter the verification code to enable 2FA

### 3. Create a Database Backup
1. Go to Admin → Backup & Restore
2. Click "Create Backup" to save a backup on the server
3. Or click "Create & Download Backup" to download directly
4. Store backups in a safe location

### 4. Create Your First User
1. Go to Admin → Manage Users
2. Click "Create User"
3. Fill in the user details and select their role

### 4. Create a Group
1. From the Dashboard, click "Create Group"
2. Enter a name (e.g., "Roommates", "Trip to Paris")
3. Add a description and select currency
4. Save the group

### 5. Add Group Members
1. Open your group
2. Click "Manage Members"
3. Add members by their email address

### 6. Add Your First Expense
1. In the group view, click "Add Expense"
2. Enter the expense details:
   - Description: "Dinner at restaurant"
   - Amount: 120.00
   - Who paid: Select the payer
   - Split type: Equal (or choose another method)
   - Participants: Select who shares this expense
3. Save the expense

---

## Customizing Your Experience

### Change Color Theme
1. Click the palette icon in the navigation bar
2. Select from 6 available themes:
   - Emerald (green - default)
   - Ocean Blue
   - Royal Purple
   - Sunset Gold
   - Rose
   - Slate Dark

Your selection is saved automatically.

---

## Understanding Split Types

| Type | Use Case | Example |
|------|----------|---------|
| **Equal** | Everyone pays the same | $100 dinner ÷ 4 people = $25 each |
| **Percentage** | Different contribution ratios | Rent: 60% / 40% based on room size |
| **Exact** | Specific amounts per person | Each person orders different items |
| **Shares** | Proportional splitting | Adults pay 2 shares, kids pay 1 share |

---

## Common Tasks

### View Who Owes Whom
- Dashboard shows your overall balance
- Group view shows detailed balances within that group

### Record a Payment
1. Go to the group
2. Click "Record Settlement" or "Settle Up"
3. Select who paid whom and the amount
4. Save to update balances

### Export Data
1. Go to Profile
2. Click "Export My Data" for a CSV download

---

## Troubleshooting

### Application Won't Start

**Port already in use:**
```bash
# Find what's using port 8080
lsof -i :8080
# Kill it or change the port in application.yml
```

**Java version too old:**
```bash
java -version
# Must be 17 or higher
```

### Can't Log In

**Forgot password:**
- Contact an admin to reset your account
- Or delete `data/splitfriend.mv.db` to reset everything (loses all data!)

**2FA not working:**
- Ensure your device time is synchronized
- Contact admin to disable 2FA on your account

### Database Issues

**Reset the database:**
```bash
# Stop the application first
rm -rf data/splitfriend.*
# Restart - a fresh database will be created
```

**Restore from backup:**
1. Log in as admin
2. Go to Admin → Backup & Restore
3. Either select a saved backup to restore, or upload a .sql backup file
4. Confirm the restore (warning: this will delete all current data)

**Access H2 Console:**
1. Log in as admin
2. Navigate to `/h2-console`
3. JDBC URL: `jdbc:h2:file:./data/splitfriend`
4. Username: `sa`, Password: (leave empty)

---

## Getting Help

- Check the [README.md](README.md) for detailed documentation
- Review application logs for error messages
- For bugs and feature requests, use GitHub Issues

---

## Next Steps

- Read the full [README.md](README.md) for advanced configuration
- Set up HTTPS using a reverse proxy (nginx, Caddy)
- Configure automatic backups via Admin → Backup & Restore
- Download backups regularly and store them off-site
- Explore the admin dashboard for user management
