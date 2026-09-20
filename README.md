# CPEN321_26W1_ProjectName

## Frontend Setup 

Requires Android Studio with the Android SDK, and a Pixel 9 emulator running
API 36 with a **Google Play** system image. Credential Manager needs Google
Play Services, so an AOSP-only image cannot sign in.

1. Create `frontend/local.properties` from the template:

   ```bash
   cp frontend/local.properties.example frontend/local.properties
   ```

2. Fill in the three values:

   - `sdk.dir` — path to your Android SDK. Android Studio normally writes
     this for you.
   - `API_BASE_URL` — base URL of the back-end, with no trailing slash. See
     below.
   - `GOOGLE_CLIENT_ID` — the **Web** OAuth client ID (see "Google sign-in").

   `API_BASE_URL` depends on where the back-end runs:

   - Deployed server — `https://<SERVER_PUBLIC_IP>:3000`
   - Local back-end, emulator — `http://10.0.2.2:3000` (`10.0.2.2` is the
     emulator's alias for the host machine)
   - Local back-end, physical device — `http://<your computer's LAN IP>:3000`

3. Sign in to a Google account on the emulator (Settings > Passwords &
   accounts), using an account listed as a test user on the OAuth consent
   screen. Sign-in fails for any other account while the consent screen is in
   testing mode.

4. Build and install:

   ```bash
   ./scripts/run-frontend.sh
   ```

### Google sign-in

Sign-in uses Credential Manager with Google Identity Services directly. Two
OAuth clients are needed in the same Google Cloud project:

- An **Android** client, registering the package name
  `com.example.cpen321application` together with the SHA-1 of the key that
  signs the APK. Its client ID is never used in code.
- A **Web application** client. This one's ID goes in `GOOGLE_CLIENT_ID`.

Get the debug key's SHA-1 with:

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

A release-signed APK uses a different key, whose SHA-1 must also be registered.

### Trusting the back-end certificate

The app pins the deployed server's self-signed certificate in
`frontend/app/src/main/res/raw/server_cert.pem`, trusted only for that host by
`res/xml/network_security_config.xml`. If you regenerate the server
certificate, or deploy to a different IP, replace that file with the new
`server.crt` and update the `<domain>` entry to match.

## Backend Setup 

Requires Docker (with Compose v2.24+) and, to run the tests directly, Node.js 22+.

1. Create the environment file from the template:

   ```bash
   cp backend/.env.example backend/.env
   ```

2. Edit `backend/.env`:

   - `PORT` — the port the server listens on (default `3000`).
   - `SERVER_PUBLIC_IP` — **required for any deployed instance.** A cloud VM
     sits behind NAT and cannot discover its own public address, so `GET
     /api/ip` reports this value. Leave it blank when running locally: the
     server then falls back to scanning its own network interfaces and
     reports its routable LAN IPv4 address.

3. Start the backend:

   ```bash
   ./scripts/run-backend.sh
   ```

   The script builds and starts the Docker Compose stack and waits until
   `http://localhost:<PORT>/health` responds. Stop it with `docker compose down`.

4. Run the backend interface tests:

   ```bash
   ./scripts/run-backend-interface-tests.sh
   ```

   This runs the mocked suite, the un-mocked suite, and both together, writing
   coverage to `backend/coverage/{mock,no-mock,interface}`.

### Backend API

- `GET /health` — `{ "status": "ok" }`
- `GET /api/name` — `{ "firstName": "...", "lastName": "..." }`
- `GET /api/time` — `{ "time": "hh:mm:ss GMT+hh:mm" }`
- `GET /api/ip` — `{ "ip": "<IPv4 or IPv6>" }`

`/health` is deliberately kept off the `/api` prefix because the deployment
script and the container health probe both poll it directly.

`/api/time` reports the server's own local time, read at the moment of the
call, in 24-hour format relative to GMT. On a UTC host it reads `GMT+00:00`.

### HTTPS

The server speaks plain HTTP by default, which is fine for local development.
To serve HTTPS, place a certificate and key at `backend/certs/server.crt` and
`backend/certs/server.key` and set `ENABLE_HTTPS=true` in `backend/.env`.

`backend/certs/` is gitignored and excluded from the Docker image; Compose
mounts it read-only at runtime, so the private key is never committed or baked
into a built artifact.

To generate a self-signed certificate for a server at `<PUBLIC_IP>`:

```bash
mkdir -p backend/certs
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout backend/certs/server.key \
  -out backend/certs/server.crt \
  -days 365 \
  -subj "/CN=<PUBLIC_IP>" \
  -addext "subjectAltName=IP:<PUBLIC_IP>"
```

The IP must appear in `subjectAltName`, or Android rejects the certificate
regardless of trust settings. `127.0.0.1` and `localhost` are included so the
deployment script's health check can validate the certificate locally.

### Running the deploy script with HTTPS enabled

`scripts/run-backend.sh` polls a health endpoint and, by design, lets you
override the URL it uses. With `ENABLE_HTTPS=true` the server no longer speaks
plain HTTP, so point the check at HTTPS and make the machine trust the
certificate first:

```bash
# Trust the certificate locally (Ubuntu)
sudo cp backend/certs/server.crt /usr/local/share/ca-certificates/cpen321.crt
sudo update-ca-certificates

# Run the provided script, pointing its health check at HTTPS
BACKEND_HEALTH_URL="https://localhost:3000/health" ./scripts/run-backend.sh
```

Both steps are ordinary setup; the scripts themselves are used unmodified.

## Additional Setup 

_Please specify any other additional setup steps non-specific to either frontend nor backend_

