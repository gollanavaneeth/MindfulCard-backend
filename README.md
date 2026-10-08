# MindfulCart Backend

Spring Boot REST API for the **Anti-Impulse Shopping Assistant**. It provides JWT authentication, BCrypt password hashing, purchase evaluation, budget and savings tools, product comparison, reports, administration, OCR, and a grounded AI-advisor API.

## Technology

- Java 17
- Spring Boot 3.3.5
- Spring Web and Bean Validation
- Spring Security with JWT
- Spring Data JPA with Hibernate
- MySQL 8 for development and production
- H2 for automated tests
- Tess4J/Tesseract for local OCR
- Maven

## Requirements

Install the following before starting the backend:

- JDK 17 or newer
- Maven 3.9 or newer
- MySQL 8

Verify Java and Maven:

```powershell
java -version
mvn -version
```

## Database

Default development configuration:

| Setting | Default |
|---|---|
| Database | `anti_impulse_assistant` |
| Host | `localhost:3306` |
| Username | `root` |
| Password | `password` |

The database is created automatically when the configured MySQL account has permission to create databases.

To configure different credentials in PowerShell:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your-mysql-password"
```

You can also override the complete connection URL:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/anti_impulse_assistant?useSSL=false&serverTimezone=Asia/Kolkata"
```

## Run the backend

From this `backend` directory:

```powershell
mvn spring-boot:run
```

The REST API starts at:

```text
http://localhost:8080/api
```

The React development frontend is allowed from `http://localhost:3000` by default.

## Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | MySQL JDBC connection | Local `anti_impulse_assistant` database |
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | `password` |
| `JWT_SECRET` | Key used to sign JWTs | Development-only key |
| `FRONTEND_URL` | Allowed CORS origin | `http://localhost:3000` |
| `OPENAI_API_KEY` | Enables generated advisor responses | Empty |
| `OPENAI_MODEL` | AI model used by the advisor | `gpt-4.1-mini` |
| `OCR_LANGUAGE` | Tesseract OCR language | `eng` |
| `OCR_PAGE_SEGMENTATION_MODE` | Tesseract page layout mode | `6` |

Set a strong `JWT_SECRET` outside source control before deployment:

```powershell
$env:JWT_SECRET = "replace-with-a-long-random-production-secret"
```

## Authentication flow

1. React sends registration or login details to `/api/auth`.
2. Registration hashes the password using BCrypt before saving the user.
3. Login uses Spring Security to compare the entered password with the stored hash.
4. The backend signs and returns a JWT.
5. React sends the token as `Authorization: Bearer <token>`.
6. `JwtFilter` validates the token before protected controllers run.

Passwords are hashed, not encrypted, and cannot be read back from the database.

The first registered account receives the `ADMIN` role. If an existing database has users but no administrator, the oldest account is promoted automatically.

## Main API routes

| Area | Method and route |
|---|---|
| Register | `POST /api/auth/register` |
| Login | `POST /api/auth/login` |
| Forgot password | `POST /api/auth/forgot-password` |
| Reset password | `POST /api/auth/reset-password` |
| Change password | `PUT /api/account/password` |
| Profile | `GET`, `PUT /api/users/profile` |
| Evaluations | `GET`, `POST /api/evaluations` |
| Evaluation decision | `PUT /api/evaluations/{id}/decision` |
| Product comparison | `POST /api/comparisons` |
| Budget | `GET`, `POST`, `PUT /api/budget` |
| Savings goals | `GET`, `POST`, `PUT`, `DELETE /api/goals` |
| Cooling-off list | `GET`, `POST`, `PUT`, `DELETE /api/wishlist` |
| Owned items | `GET`, `POST`, `PUT`, `DELETE /api/owned-items` |
| Dashboard analytics | `GET /api/dashboard` |
| Price watch | `/api/price-watches/**` |
| Reports | `/api/reports/**` |
| Notifications | `/api/notifications/**` |
| Administration | `/api/admin/**` |
| Grounded advisor | `/api/coach/**` |
| OCR and text extraction | `/api/tools/**` |

All routes except `/api/auth/**` require a valid JWT. Administration routes additionally require the `ADMIN` role.

## Product-comparison model

`POST /api/comparisons` compares two products using:

- Need fit
- Quality and public-rating confidence
- Expected lifetime usage
- Discount, shipping, maintenance, and resale value
- Warranty and return protection
- Expected longevity
- Available monthly-budget impact

The response includes checkout price, total ownership cost, cost per use, a six-part score breakdown, strengths, watchouts, score gap, and winner confidence.

## Grounded advisor / RAG

The advisor retrieves the authenticated user's recent purchase evaluations from MySQL, formats the relevant facts as context, and augments the generation prompt. If `OPENAI_API_KEY` is unavailable or the external request fails, it returns a deterministic local response.

This is relational application-data RAG. It does not claim to use embeddings or a vector database.

## OCR

Receipt and screenshot extraction runs locally using Tess4J/Tesseract. Uploaded content is validated, processed, and returned as a reviewable purchase draft. Confirm OCR results before saving them.

## Tests

Run the complete backend test suite:

```powershell
mvn test
```

The tests use an in-memory H2 database and do not modify the MySQL development database.

Run only the product-comparison tests:

```powershell
mvn test -Dtest=ComparisonControllerTest
```

## Build

Create the executable Spring Boot JAR:

```powershell
mvn clean package
```

The generated JAR is written to `target/`, which is intentionally excluded by `.gitignore`.

## Important source folders

```text
src/main/java/com/mindfulcart/assistant/
  config/       Security, CORS, admin bootstrap
  controller/   REST endpoints
  dto/          Request and response records
  model/        JPA entities
  repository/   Spring Data repositories
  security/     JWT and user authentication
  service/      Business, analytics, OCR, and AI logic

src/main/resources/
  application.properties

src/test/java/
  Integration and unit tests
```

## Production notes

- Never commit database passwords, API keys, or production JWT secrets.
- Restrict `FRONTEND_URL` to the deployed frontend origin.
- Use a dedicated MySQL user instead of the root account.
- Replace the development JWT secret.
- Use HTTPS for frontend and backend traffic.
- Review the schema migration strategy before replacing `ddl-auto=update` in production.
