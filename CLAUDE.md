# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

```bash
# Run the application (development)
mvn spring-boot:run

# Run tests
mvn test

# Build production JAR
mvn clean package -DskipTests

# Run production JAR
java -jar target/splitfriend-1.0.0.jar

# Docker
docker-compose up -d
docker build -t splitfriend .
```

## Architecture Overview

SplitFriend is a Spring Boot 3.2 expense splitting application using:
- **Embedded Jetty** (Tomcat excluded in pom.xml)
- **H2 file-based database** at `./data/splitfriend`
- **Thymeleaf** for server-side rendering
- **Spring Security** with optional TOTP-based 2FA
- **Lombok** for boilerplate reduction

### Layer Structure

```
com.splitfriend/
├── config/          # SecurityConfig, JettyConfig, WebConfig
├── controller/      # Web controllers (REST-style endpoints returning HTML views)
│   └── admin/       # Admin-only controllers (AdminController, UserManagementController, BackupController)
├── model/           # JPA entities: User, Group, GroupMember, Expense, ExpenseSplit, Settlement
│   └── enums/       # Role (ADMIN, USER), SplitType (EQUAL, PERCENTAGE, EXACT, SHARES)
├── repository/      # Spring Data JPA repositories
├── service/         # Business logic layer
│   ├── BalanceService          # Calculates who owes whom
│   ├── DebtSimplificationService # Minimizes transactions to settle debts
│   ├── BackupService/Scheduler  # Database backup functionality
│   └── TotpService             # Two-factor authentication
├── security/        # CustomUserDetailsService, SecurityUtils
└── dto/             # Data transfer objects for views
```

### Key Entity Relationships

- **User** → has many **GroupMember** memberships
- **Group** → has many **GroupMember**, **Expense**, **Settlement**
- **Expense** → has one **paidBy** (User), many **ExpenseSplit** (per participant)
- **Settlement** → payment from one User to another within a Group

### Security Model

- No public registration; users created by admins only
- Role-based access: ADMIN has full access, USER limited to their groups
- Optional TOTP 2FA via `dev.samstevens.totp` library
- H2 console at `/h2-console` (admin only, disabled for external access)

### Configuration

Key properties in `application.yml`:
- `app.admin.default-email/password` - Initial admin credentials
- `app.backup.*` - Backup directory, retention, and auto-backup cron schedule
- `spring.datasource.url` - H2 database file location

Environment variables: `SERVER_PORT`, `DB_PASSWORD`, `ADMIN_PASSWORD`
