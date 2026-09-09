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
├── model/           # JPA entities: User, Group, GroupMember, Expense, ExpenseSplit, Settlement, Budget, BudgetItem
│   └── enums/       # Role (ADMIN, USER), SplitType (EQUAL, PERCENTAGE, EXACT, SHARES),
│                    # BudgetFrequency, BudgetSide (A, B), BudgetItemType (SHARED, ABSORBED)
├── repository/      # Spring Data JPA repositories
├── service/         # Business logic layer
│   ├── BalanceService          # Calculates who owes whom
│   ├── BudgetCalculationService # Normalizes recurring costs, computes the net transfer
│   ├── BudgetService           # Budget CRUD; access control is built into its lookups
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

### Budget Domain

Separate from Groups/Expenses: a **Budget** is a standing arrangement between
exactly **two** users covering recurring household costs (mortgage, internet,
insurance), split by a fixed percentage.

- Participants are two FK columns (`user_a_id`/`user_b_id`), not a child
  collection, so "exactly two" is structural. Only `share_a_percent` is stored;
  B's share is derived as `100 - A`, so the pair cannot drift out of sum-to-100.
- `BudgetItem.paid_by_side` is a `BudgetSide` enum, never a user FK - an item
  cannot be attributed to a non-participant and silently drop out of both totals.
- `BudgetItemType.ABSORBED` marks a cost paid entirely by one person and
  excluded from the split; it is still reported in that person's total outlay.
- Every amount is normalized through an annual basis
  (`amount * from.periodsPerYear / to.periodsPerYear`) into the budget's
  `settlement_period`. Divisions by 26 and 12 are non-terminating, so all
  arithmetic goes through `BudgetMoney.CALC`; a bare `BigDecimal.divide` throws.
- Rounding happens once **per side**, never per item (30 items rounded at
  `x12/26` drift by dimes). Every aggregate - pooled total, outlay, net - is
  then derived from those already-rounded figures, so the breakdown a reader
  adds up by hand matches the totals printed beside it.
- Net transfer: `(paidA * shareB - paidB * shareA) / 100`; positive means B
  pays A. Budget figures never feed group balances, the dashboard, or the
  `/api/v1` payload - a forward recurring transfer is not a settled position.
- Gated on `User.budget_enabled` (nullable; admin-controlled at
  `/admin/users/{id}` -> Features). The flag is re-read per request by
  `BudgetMenuAdvice` rather than taken from the cached principal, since
  remember-me runs 30 days. `BudgetEnabledInterceptor` enforces it on
  `/budgets/**`; hiding the nav link is not access control.
- Two access levels in `BudgetService`: `requireAccess` (read - admins may read
  any budget, and doing so is logged) and `requireWriteAccess` (participants
  only, no admin bypass). Non-participants get a read-only view; the template
  hides every mutating control when `currentSide == null`.

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
