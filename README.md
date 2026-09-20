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

   `local.properties` is gitignored, so these values never reach the
   repository and must be supplied by whoever builds the APK. If
   `API_BASE_URL` is missing, the build silently falls back to
   `http://10.0.2.2:3000`, which points at whatever machine the emulator is
   running on rather than at the deployed server — the app then fails to
   reach the back-end with no obvious cause. Set it to the deployed HTTPS URL
   before building an APK for anyone else.

   The file is Java Properties format, which escapes colons. The deployed
   value is written `API_BASE_URL=https\://<SERVER_PUBLIC_IP>\:3000`; Gradle
   strips the backslashes when reading it.

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

### Deploying to the cloud server

The deployed instance runs the compiled backend directly under Node with
systemd, not under Docker. The instance has 1 GB of RAM, and the Compose stack
also starts MongoDB, which this milestone does not use and which blocks
start-up through its `depends_on` health condition. systemd also restarts the
service on crash and on reboot, which the Compose file does not currently do
for the backend service.

Deployed layout:

- Source lives at `~/CPEN321App/backend` on the server.
- `backend/certs/` and `backend/.env` are gitignored, so they are copied to the
  server directly rather than through Git.
- `backend/.env` sets `PORT=3000`, `SERVER_PUBLIC_IP=<public IP>` and
  `ENABLE_HTTPS=true`.
- The unit file is `/etc/systemd/system/cpen321-backend.service`, running
  `node dist/index.js` with `WorkingDirectory` set to the backend directory.
  The working directory matters: `src/index.ts` reads the certificate from the
  relative path `./certs/server.crt`.

To deploy, or to redeploy after a code change:

```bash
# From the repository root, on your own machine
rsync -az --delete -e "ssh -i <key>.pem" \
  --exclude node_modules --exclude coverage --exclude certs --exclude .env \
  backend/ ubuntu@<public IP>:~/CPEN321App/backend/

scp -i <key>.pem -r backend/certs ubuntu@<public IP>:~/CPEN321App/backend/

# On the server
ssh -i <key>.pem ubuntu@<public IP>
cd ~/CPEN321App/backend && npm ci && npm run build
sudo systemctl restart cpen321-backend
```

Checking on it:

- `sudo systemctl status cpen321-backend` — whether it is running.
- `sudo journalctl -u cpen321-backend -f` — live logs.
- `sudo systemctl is-enabled cpen321-backend` — whether it starts on boot.

The security group must allow inbound TCP on port 3000. A blocked port times
out, while an open port with nothing listening refuses the connection
immediately — a quick way to tell the two apart from outside.

## Additional Setup 

_Please specify any other additional setup steps non-specific to either frontend nor backend_

