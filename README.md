# CPEN321_26W1_app

Android app (Kotlin, Jetpack Compose) with a Node.js/TypeScript back-end.
Three independent buttons: server and client details over HTTPS after Google
sign-in, a live 16x16 pixel image relayed from the course WebSocket, and a
timer with a surprise.

## Quick start

The back-end is already deployed at `https://52.35.22.194:3000` and needs no
setup to exercise the app. Only the front-end has to be built.

1. Create `frontend/local.properties` (gitignored, absent from a fresh clone):

   ```properties
   sdk.dir=/path/to/your/Android/sdk
   API_BASE_URL=https\://52.35.22.194\:3000
   GOOGLE_CLIENT_ID=294961049928-tqu94trnk3bqmce77bs2armgtht67qpp.apps.googleusercontent.com
   ```

   **The build does not fail if this file is missing.** It falls back to an
   empty client ID and `http://10.0.2.2:3000`, producing an APK that installs
   and runs but cannot sign in or reach the server. Create the file first.

2. `./scripts/run-frontend.sh` — builds, installs, and launches the app on a
   Pixel 9 emulator (API 36, **Google Play** system image; Credential Manager
   needs Play Services, so an AOSP-only image cannot sign in).

3. Add a Google account to the emulator under Settings > Passwords & accounts
   before using Button 1. A fresh emulator has none, and Credential Manager can
   only offer accounts already on the device, so the sign-in screen has an
   **Add a Google account** button that opens that screen. The OAuth consent
   screen is published, so any Google account signs in — no test account needed.

## Repository Structure

```shell
├── frontend/            # Frontend code and tests
├── backend/             # Backend code and tests
├── documentation/       # Project documentation and associated images and resources
├── scripts/             # Scripts TAs will use to deploy your frontend and backend code
├── .github/workflows/   # GitHub Actions workflows for CI/CD
├── detekt.yml           # Detekt config read by Codacy (please do not move or modify)
├── .eslintrc.json       # ESLint config read by Codacy (please do not move or modify)
├── docker-compose.yml   # Runs the backend and MongoDB via Docker Compose
└── README.md
```

## Deployed back-end

Base URL `https://52.35.22.194:3000`, self-signed certificate pinned by the app.
The three data endpoints require the Google ID token from sign-in as
`Authorization: Bearer <token>`; without one the server answers `401`.

- `POST /api/auth/google` — body `{ "idToken": "..." }`. Verifies the token and
  answers `{ "firstName", "lastName", "email" }` for the account it belongs to.
  `400` if the body carries no token, `401` if the token does not check out.
  Open, and the app's only source for the signed-in user's name.
- `GET /api/ip` — `{ "ip": "<IPv4 or IPv6>" }`. Authenticated.
- `GET /api/time` — `{ "time": "hh:mm:ss GMT+hh:mm" }`, server local time read
  at the moment of the call. Authenticated.
- `GET /api/name` — `{ "firstName", "lastName" }`, the developer's own name.
  Authenticated.
- `GET /health` — `{ "status": "ok" }`. Open, so the deploy script and
  container health probe need no credentials.
- `ws /ws/pixels` — relays the course pixel stream frame for frame.

## Frontend Setup

Requires Android Studio, Java 17, and a Pixel 9 emulator on API 36 with a
**Google Play** system image.

1. `cp frontend/local.properties.example frontend/local.properties`
2. Fill in the three keys shown in [Quick start](#quick-start). `sdk.dir` is
   your Android SDK path, which Android Studio normally writes for you. Use
   `http://10.0.2.2:3000` as `API_BASE_URL` for a back-end on your own machine
   (`10.0.2.2` is the emulator's alias for the host). `GOOGLE_CLIENT_ID` is the
   **Web** OAuth client ID; it is not a secret and ships inside every APK.
   Properties format escapes colons, hence `https\://52.35.22.194\:3000`.
3. `./scripts/run-frontend.sh`

Sign-in uses Credential Manager with Google Identity Services, not Firebase.
The app obtains a Google ID token naming the Web client ID as its server client
ID, posts it to `POST /api/auth/google`, and the server verifies the signature,
audience, issuer, and expiry with `google-auth-library` before returning the
name. Names read on the device are ignored, and every later API call re-verifies
the same token.

The Android OAuth client registers `com.example.cpen321application` with the
signing key's SHA-1. The release build is signed with the debug keystore, so one
SHA-1 covers both `installDebug` and the submitted `assembleRelease` APK:

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

To sign with a real release key instead, set `RELEASE_KEYSTORE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` in
`local.properties` and register that key's SHA-1 too.

The server certificate is pinned at `frontend/app/src/main/res/raw/server_cert.pem`,
trusted for that host only by `res/xml/network_security_config.xml`. Regenerating
it or moving to another IP means replacing that file and the `<domain>` entry.

## Backend Setup

Requires Docker (Compose v2.24+), or Node.js 22+ to run it directly.

1. `cp backend/.env.example backend/.env`
2. Set the keys this milestone uses:
   - `PORT` — default `3000`.
   - `SERVER_PUBLIC_IP` — **required on a deployed instance.** A cloud VM behind
     NAT cannot discover its own public address, so `GET /api/ip` reports this
     value. Leave blank locally and the server scans its own interfaces.
   - `ENABLE_HTTPS` — `true` serves TLS from `backend/certs/`.
   - `GOOGLE_CLIENT_ID` — **required.** Must match the front-end's value or
     every request is rejected as wrong-audience. The server refuses to start
     without it. `.env.example` already carries this submission's value.
3. `./scripts/run-backend.sh` — builds and starts the Compose stack, waiting on
   `/health`. Stop with `docker compose down`.
4. `./scripts/run-backend-interface-tests.sh` — interface tests with coverage.
   This milestone has tests in `tests/no-mock/` only, so the script skips the
   mocked suite and writes coverage to `backend/coverage/no-mock`.

### HTTPS

`backend/certs/` is gitignored and excluded from the image; Compose mounts it
read-only, so the private key is never committed. To generate a certificate for
a server at `<PUBLIC_IP>`:

```bash
mkdir -p backend/certs
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout backend/certs/server.key -out backend/certs/server.crt \
  -days 365 -subj "/CN=<PUBLIC_IP>" \
  -addext "subjectAltName=IP:<PUBLIC_IP>"
```

The IP must appear in `subjectAltName` or Android rejects the certificate
whatever the trust settings say. With `ENABLE_HTTPS=true` the server no longer
speaks plain HTTP, so trust the certificate locally and point the health check
at HTTPS:

```bash
sudo cp backend/certs/server.crt /usr/local/share/ca-certificates/cpen321.crt
sudo update-ca-certificates
BACKEND_HEALTH_URL="https://localhost:3000/health" ./scripts/run-backend.sh
```

## Additional Setup

### Cloud deployment

The deployed instance (AWS EC2 t3.micro, Ubuntu, us-west-2, Elastic IP) runs the
compiled back-end under systemd rather than Docker: it has 1 GB of RAM, and the
Compose stack also starts MongoDB, which this milestone does not use. systemd
restarts the service on crash and on reboot.

- Source at `~/CPEN321App/backend`; unit file
  `/etc/systemd/system/cpen321-backend.service` runs `node dist/index.js` with
  `WorkingDirectory` set there, since the certificate path is relative.
- `backend/.env` and `backend/certs/` are gitignored, so they are copied to the
  server directly rather than through Git.
- The security group must allow inbound TCP on port 3000.

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
