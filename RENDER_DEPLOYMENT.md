# Deploy the backend on Render

This backend is configured for a Docker-based Render Web Service connected to Aiven MySQL over required SSL.

## Security step required first

The database password was visible in a shared screenshot. Rotate the `avnadmin` password in Aiven before deploying. Do not reuse or commit the exposed value.

After rotation, store the new password only in Render's Environment settings.

## Database configuration

The production profile uses these values:

| Variable | Value |
|---|---|
| `DB_HOST` | `mysql-36e1599e-gollanavaneeth-0545.d.aivencloud.com` |
| `DB_PORT` | `21271` |
| `DB_NAME` | `nava` |
| `DB_USERNAME` | `avnadmin` |
| `DB_PASSWORD` | Set privately in Render |

Confirm that the `nava` database exists in Aiven. If it does not, create it from the Aiven console or MySQL client before the first Render deployment.

The application constructs this JDBC connection internally:

```text
jdbc:mysql://DB_HOST:DB_PORT/nava?sslMode=REQUIRED&serverTimezone=UTC
```

Do not paste Aiven's `mysql://...` Service URI into `DB_URL`; Spring requires a JDBC URL. The committed production profile already constructs the correct JDBC URL from the individual variables.

## Recommended deployment: Render Blueprint

1. Push the contents of this backend repository to GitHub.
2. Open the Render dashboard.
3. Select **New -> Blueprint**.
4. Connect the backend GitHub repository.
5. Render detects `render.yaml` and creates `anti-impulse-backend`.
6. Enter the rotated Aiven password when Render requests `DB_PASSWORD`.
7. Enter the deployed frontend origin when Render requests `FRONTEND_URL`, for example:

   ```text
   https://your-frontend.onrender.com
   ```

   Do not include a trailing slash. Multiple allowed origins can be supplied as a comma-separated value.
8. Complete the Blueprint deployment.

Render generates `JWT_SECRET` automatically. Never replace it with a short value, and do not change it after users have logged in unless you intentionally want to invalidate all existing tokens.

## Manual Render setup

If you do not use the Blueprint, create a **Web Service** with:

| Setting | Value |
|---|---|
| Runtime | Docker |
| Region | Singapore |
| Dockerfile path | `./Dockerfile` |
| Docker build context | `.` |
| Health check path | `/api/health` |

Add these environment variables in the Render dashboard:

```text
SPRING_PROFILES_ACTIVE=prod
DB_HOST=mysql-36e1599e-gollanavaneeth-0545.d.aivencloud.com
DB_PORT=21271
DB_NAME=nava
DB_USERNAME=avnadmin
DB_PASSWORD=<rotated-aiven-password>
FRONTEND_URL=<deployed-frontend-origin>
JWT_SECRET=<long-random-secret-at-least-32-characters>
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Dfile.encoding=UTF-8
```

Do not add an external AI credential; the project has no external AI integration.

## Health check

After deployment, open:

```text
https://your-backend.onrender.com/api/health
```

Expected response:

```json
{
  "status": "UP",
  "database": "UP",
  "service": "anti-impulse-backend",
  "timestamp": "..."
}
```

This endpoint runs `SELECT 1`, so a successful response confirms both the Spring Boot service and Aiven database connection.

## Connect the frontend

Set the frontend environment variable to the deployed backend API base URL:

```text
REACT_APP_API_URL=https://your-backend.onrender.com/api
```

Rebuild/redeploy the frontend after changing this value.

Also ensure the backend's `FRONTEND_URL` exactly matches the frontend origin. A mismatch causes browser CORS errors even when the backend itself is healthy.

## Expected first deployment behavior

1. Render builds the Maven project inside the Docker build stage.
2. The runtime image installs Tesseract 5 and its English language data for server-side OCR.
3. The final image starts the Spring Boot JAR as a non-root user.
4. Spring reads Render's assigned `PORT` and listens on `0.0.0.0`.
5. The `prod` profile connects to database `nava` through required SSL.
6. Hibernate creates or updates the application tables.
7. Render calls `/api/health` and starts routing traffic after it returns HTTP 200.

The first registered application account becomes the administrator.

## Common deployment errors

### `Unknown database 'nava'`

Create the `nava` database in Aiven or correct `DB_NAME` in Render.

### `Access denied for user 'avnadmin'`

Rotate the Aiven password again and update `DB_PASSWORD` in Render. Confirm that no spaces or quotation marks were copied with it.

### SSL or communications-link failure

Confirm the Aiven service is running, the hostname and port match the Aiven overview, and the connection URL includes `sslMode=REQUIRED`.

### CORS error in the browser

Set `FRONTEND_URL` to the exact frontend origin, without a path or trailing slash. For local and deployed access together, use:

```text
http://localhost:3000,https://your-frontend.onrender.com
```

### Render reports that no port is open

Confirm `SPRING_PROFILES_ACTIVE=prod`. Both the default and production configurations read Render's `PORT`, so the startup log should show the assigned port.

### Health check returns an error

Review the Render logs for the underlying Aiven connection error. The health endpoint intentionally fails when the database cannot execute `SELECT 1`.
