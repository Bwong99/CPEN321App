# CPEN321_26W1_app

Android app (Kotlin, Jetpack Compose) with a Node.js/TypeScript back-end.
Three independent buttons: server and client details over HTTPS after Google
sign-in, a live 16x16 pixel image relayed from the course WebSocket, and a
timer with a surprise.

## Repository Structure

```shell
├── frontend/            # Frontend code and tests
├── backend/             # Backend code and tests
├── documentation/       # Project documentation and associated images and resources
│   ├── Requirements_and_Design.md
│   ├── Testing.md
│   ├── Code_Review.md
│   ├── Individual_Reflections.md
│   ├── Team_Reflections.md
│   ├── images/
│   └── res/
├── scripts/             # Scripts TAs will use to deploy your frontend and backend code
│   ├── run-frontend.sh
│   ├── run-frontend.ps1
│   ├── run-backend.sh
│   └── run-backend.ps1
├── .github/            # GitHub Actions workflows for CI/CD
│   └── workflows/
├── detekt.yml          # Detekt config read by Codacy (please do not move or modify)
├── .eslintrc.json      # ESLint config read by Codacy (please do not move or modify)
├── docker-compose.yml  # Runs the backend and MongoDB via Docker Compose. Make sure to keep this file up to date
└── README.md
```

## Deployed back-end

Base URL `https://52.35.22.194:3000`, self-signed certificate pinned by the app.

- `GET /api/ip` — `{ "ip": "<IPv4 or IPv6>" }`
- `GET /api/time` — `{ "time": "hh:mm:ss GMT+hh:mm" }`, server local time read at
  the moment of the call. On a UTC host it reads `GMT+00:00`.
- `GET /api/name` — `{ "firstName": "...", "lastName": "..." }`
- `GET /health` — `{ "status": "ok" }`. Kept off the `/api` prefix because the
  deploy script and container health probe poll it directly.
- `ws /ws/pixels` — relays the course pixel stream frame for frame.

## Frontend Setup

Requires Android Studio and a Pixel 9 emulator on API 36 with a **Google Play**
system image. Credential Manager needs Play Services, so an AOSP-only image
cannot sign in.

1. `cp frontend/local.properties.example frontend/local.properties`

2. Fill in:

   - `sdk.dir` — your Android SDK path. Android Studio normally writes this.
   - `API_BASE_URL` — `https://52.35.22.194:3000` for the deployed server, or
     `http://10.0.2.2:3000` for a back-end on your own machine (`10.0.2.2` is
     the emulator's alias for the host).
   - `GOOGLE_CLIENT_ID` — the **Web** OAuth client ID (see below).

   `local.properties` is gitignored, so these never reach the repository. If
   `API_BASE_URL` is unset the build silently falls back to
   `http://10.0.2.2:3000`, which points at the build machine rather than the
   server. Properties format escapes colons, so the deployed value is written
   `API_BASE_URL=https\://52.35.22.194\:3000`.

3. Sign in to a Google account on the emulator (Settings > Passwords &
   accounts). A freshly created emulator has no account, and Credential
   Manager can only offer accounts already on the device — so the app's
   sign-in screen shows an **Add a Google account** button that opens this
   screen for you if none is present.

   While the OAuth consent screen is in testing mode, only accounts on its
   test-user list can sign in; once it is published, any account works.

4. `./scripts/run-frontend.sh`

### Google sign-in

Credential Manager with Google Identity Services directly, not Firebase. Two
OAuth clients in one Google Cloud project:

- An **Android** client registering `com.example.cpen321application` with the
  SHA-1 of the signing key. Its client ID is never used in code.
- A **Web application** client, whose ID goes in `GOOGLE_CLIENT_ID`.

Debug key SHA-1:

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

A release-signed APK uses a different key, whose SHA-1 must also be registered.

### Trusting the certificate

The app pins the server certificate at
`frontend/app/src/main/res/raw/server_cert.pem`, trusted for that host only by
`res/xml/network_security_config.xml`. Regenerating the certificate or moving
to another IP means replacing that file and updating the `<domain>` entry.

## Backend Setup

Requires Docker (Compose v2.24+), or Node.js 22+ to run it directly.

1. `cp backend/.env.example backend/.env`

2. Set the keys this milestone uses:

   - `PORT` — default `3000`.
   - `SERVER_PUBLIC_IP` — **required on a deployed instance.** A cloud VM
     behind NAT cannot discover its own public address, so `GET /api/ip`
     reports this value. Leave blank locally and the server scans its own
     interfaces instead.
   - `ENABLE_HTTPS` — `true` serves TLS from `backend/certs/`.

3. `./scripts/run-backend.sh` — builds and starts the Compose stack, waiting on
   `/health`. Stop with `docker compose down`.

4. `./scripts/run-backend-interface-tests.sh` — mocked, un-mocked and combined
   suites, coverage in `backend/coverage/{mock,no-mock,interface}`.

### HTTPS

`backend/certs/` is gitignored and excluded from the image; Compose mounts it
read-only, so the private key is never committed. To generate a certificate for
a server at `<PUBLIC_IP>`:

```bash
mkdir -p backend/certs
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout backend/certs/server.key \
  -out backend/certs/server.crt \
  -days 365 \
  -subj "/CN=<PUBLIC_IP>" \
  -addext "subjectAltName=IP:<PUBLIC_IP>"
```

The IP must appear in `subjectAltName` or Android rejects the certificate
whatever the trust settings say.

With `ENABLE_HTTPS=true` the server no longer speaks plain HTTP, so point the
script's health check at HTTPS and trust the certificate locally first:

```bash
sudo cp backend/certs/server.crt /usr/local/share/ca-certificates/cpen321.crt
sudo update-ca-certificates
BACKEND_HEALTH_URL="https://localhost:3000/health" ./scripts/run-backend.sh
```

The scripts themselves are used unmodified.

## Additional Setup

### Cloud deployment

The deployed instance runs the compiled back-end under systemd rather than
Docker. It has 1 GB of RAM, and the Compose stack also starts MongoDB, which
this milestone does not use and which gates start-up through `depends_on`.
systemd also restarts the service on crash and on reboot.

- Source at `~/CPEN321App/backend`.
- `backend/.env` and `backend/certs/` are gitignored, so they are copied to the
  server directly rather than through Git.
- Unit file `/etc/systemd/system/cpen321-backend.service` runs
  `node dist/index.js` with `WorkingDirectory` set to the back-end directory —
  `src/index.ts` reads the certificate from the relative `./certs/server.crt`.
- The security group must allow inbound TCP on port 3000.

Deploy or redeploy:

```bash
rsync -az --delete -e "ssh -i <key>.pem" \
  --exclude node_modules --exclude coverage --exclude certs --exclude .env \
  backend/ ubuntu@<public IP>:~/CPEN321App/backend/
scp -i <key>.pem -r backend/certs ubuntu@<public IP>:~/CPEN321App/backend/
ssh -i <key>.pem ubuntu@<public IP> \
  'cd ~/CPEN321App/backend && npm ci && npm run build && sudo systemctl restart cpen321-backend'
```

Check with `systemctl status cpen321-backend` or
`journalctl -u cpen321-backend -f`.
