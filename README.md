# SplitFriend

A modern expense splitting application built with Java and Spring Boot. Track shared expenses, split bills with friends, and settle debts easily.

## Features

### Expense Management
- **Multiple Split Types**: Equal, percentage, exact amounts, or shares-based splitting
- **Group Management**: Create groups for different occasions (trips, roommates, events)
- **Real-time Balances**: See who owes whom at a glance
- **Debt Simplification**: Minimize the number of transactions needed to settle up

### Security
- **User Authentication**: Secure login with Spring Security
- **Two-Factor Authentication (2FA)**: Optional TOTP-based 2FA (Google Authenticator compatible)
- **Role-Based Access**: Admin and User roles with different permissions

### User Experience
- **Responsive Design**: Works on desktop and mobile devices
- **Color Themes**: 6 customizable color themes (Emerald, Ocean Blue, Royal Purple, Sunset Gold, Rose, Slate Dark)
- **Financial Icons**: Intuitive Bootstrap Icons throughout the interface

### Administration
- **User Management**: Create, disable, and manage user accounts
- **System Dashboard**: Overview of users, groups, expenses, and settlements
- **Backup & Restore**: Create database backups, download, and restore from backup files
- **Automatic Backups**: Scheduled daily backups with configurable retention
- **No Public Registration**: Users can only be created by administrators

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 17+ |
| Framework | Spring Boot 3.2 |
| Web Server | Embedded Jetty |
| Database | H2 (file-based) |
| Templates | Thymeleaf |
| CSS Framework | Bootstrap 5 |
| Icons | Bootstrap Icons |
| Build Tool | Maven |
| 2FA | TOTP (dev.samstevens.totp) |

## Project Structure

```
split_2_friend/
├── pom.xml                          # Maven configuration
├── Dockerfile                       # Docker image definition
├── docker-compose.yml               # Docker Compose setup
├── src/main/java/com/splitfriend/
│   ├── SplitFriendApplication.java  # Main application entry
│   ├── config/                      # Security, Jetty, Web configs
│   ├── controller/                  # Web controllers
│   │   └── admin/                   # Admin controllers
│   ├── model/                       # JPA entities
│   ├── repository/                  # Spring Data repositories
│   ├── service/                     # Business logic
│   ├── security/                    # Security components
│   └── dto/                         # Data transfer objects
├── src/main/resources/
│   ├── application.yml              # Application configuration
│   ├── static/
│   │   ├── css/style.css           # Custom styles & themes
│   │   └── js/app.js               # JavaScript functionality
│   └── templates/                   # Thymeleaf templates
│       ├── layout/main.html        # Main layout with navbar
│       ├── auth/                   # Login, 2FA pages
│       ├── dashboard.html          # User dashboard
│       ├── groups/                 # Group management
│       ├── expenses/               # Expense management
│       ├── settlements/            # Settlement pages
│       └── admin/                  # Admin pages
└── data/                           # H2 database files (created at runtime)
```

## Quick Start

See [QUICKSTART.md](QUICKSTART.md) for detailed setup instructions.

### TL;DR

```bash
# Clone and run
git clone <repository-url>
cd split_2_friend
mvn spring-boot:run

# Access at http://localhost:8080
# Default admin: admin@splitfriend.local / admin123
```

## Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
# Server port
server.port: 8080

# Database location
spring.datasource.url: jdbc:h2:file:./data/splitfriend

# Default admin credentials (change in production!)
app.admin.default-email: admin@splitfriend.local
app.admin.default-password: admin123

# Backup settings
app.backup.directory: ./backups
app.backup.max-files: 10
app.backup.auto-backup.enabled: true
app.backup.auto-backup.cron: "0 0 2 * * ?"  # Daily at 2:00 AM
```

### Environment Variables

For Docker deployment, configure via `.env` file:

```env
SPRING_DATASOURCE_URL=jdbc:h2:file:/data/splitfriend
APP_ADMIN_DEFAULT_EMAIL=admin@example.com
APP_ADMIN_DEFAULT_PASSWORD=your-secure-password

# Backup settings
BACKUP_AUTO_ENABLED=true
BACKUP_MAX_FILES=10
BACKUP_CRON=0 0 2 * * ?
```

## Docker Deployment

```bash
# Build and run with Docker Compose
docker-compose up -d

# Or build manually
docker build -t splitfriend .
docker run -p 8080:8080 -v splitfriend-data:/data splitfriend
```

## Usage Guide

### Creating a Group
1. Log in to the dashboard
2. Click "Create Group"
3. Enter group name, description, and currency
4. Invite members by their email addresses

### Adding an Expense
1. Navigate to a group
2. Click "Add Expense"
3. Enter description, amount, and date
4. Select who paid
5. Choose split type and participants
6. Save the expense

### Split Types Explained
- **Equal**: Amount divided equally among selected participants
- **Percentage**: Each participant pays a specified percentage (must total 100%)
- **Exact**: Specify exact amounts for each participant (must total expense amount)
- **Shares**: Weighted split (e.g., 2 shares vs 1 share for proportional splitting)

### Settling Up
1. View balances in a group
2. Click "Settle Up" or record a payment
3. Enter the amount paid between two members
4. The system will update balances automatically

## Color Themes

Users can personalize their experience with 6 color themes:

| Theme | Primary Color | Best For |
|-------|--------------|----------|
| Emerald | Green (#10b981) | Default financial theme |
| Ocean Blue | Blue (#0ea5e9) | Clean, professional look |
| Royal Purple | Purple (#8b5cf6) | Bold, creative style |
| Sunset Gold | Orange (#f59e0b) | Warm, energetic feel |
| Rose | Pink (#f43f5e) | Soft, friendly appearance |
| Slate Dark | Gray (#475569) | Dark mode preference |

Change themes via the palette icon in the navigation bar.

## API Endpoints

### Public
- `GET /login` - Login page
- `POST /login` - Authenticate user

### Authenticated Users
- `GET /dashboard` - User dashboard
- `GET /groups` - List user's groups
- `POST /groups` - Create new group
- `GET /groups/{id}` - View group details
- `POST /expenses/add` - Add expense
- `POST /settlements` - Record settlement

### Admin Only
- `GET /admin` - Admin dashboard
- `GET /admin/users` - User management
- `POST /admin/users/create` - Create new user
- `POST /admin/users/{id}/disable` - Disable user
- `POST /admin/users/{id}/enable` - Enable user
- `GET /admin/backup` - Backup management page
- `POST /admin/backup/create` - Create new backup
- `GET /admin/backup/download` - Download new backup
- `POST /admin/backup/restore` - Restore from uploaded file

## Security Considerations

- Change default admin credentials immediately in production
- Enable 2FA for admin accounts
- Use HTTPS in production (configure via reverse proxy)
- Database file contains sensitive data - secure appropriately
- Session cookies are HTTP-only and secure

## Development

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Running Tests
```bash
mvn test
```

### Building for Production
```bash
mvn clean package -DskipTests
java -jar target/splitfriend-1.0.0.jar
```

### H2 Console (Development)
Access the H2 database console at `/h2-console` when logged in as admin:
- JDBC URL: `jdbc:h2:file:./data/splitfriend`
- Username: `sa`
- Password: (empty)

## License

This project is provided as-is for educational and personal use.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## Support

For issues and feature requests, please use the GitHub issue tracker.
