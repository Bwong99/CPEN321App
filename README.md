# CPEN321_26W1_app

Android app (Kotlin, Jetpack Compose) with a Node.js/TypeScript back-end.
Three independent buttons: server and client details over HTTPS after Google
sign-in, a live 16x16 pixel image relayed from the course WebSocket, and a
timer with a surprise.

## Quick start for graders

Everything needed to build and run this project is below. Two files are
gitignored and must be created by hand; their exact contents are given here.

1. Clone the repository and create `frontend/local.properties` with:

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
   before using Button 1. Credentials for the account to use are in
   `M1_Doc.pdf`.

The back-end is already deployed at `https://52.35.22.194:3000` and needs no
setup to exercise the app. To run your own copy, see
[Backend Setup](#backend-setup).

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

The three data endpoints require authentication. Each must carry the Google ID
token obtained at sign-in as `Authorization: Bearer <token>`; without one the
server answers `401` and no data.

- `POST /api/auth/google` — body `{ "idToken": "..." }`. Verifies the token and
  answers `{ "firstName": "...", "lastName": "...", "email": "..." }` for the
  account it belongs to. `400` if the body carries no token, `401` if the token
  does not check out. This is the app's only source for the signed-in user's
  name.
- `GET /api/ip` — `{ "ip": "<IPv4 or IPv6>" }`. Authenticated.
- `GET /api/time` — `{ "time": "hh:mm:ss GMT+hh:mm" }`, server local time read at
  the moment of the call. On a UTC host it reads `GMT+00:00`. Authenticated.
- `GET /api/name` — `{ "firstName": "...", "lastName": "..." }`, the developer's
  own name. Authenticated.
- `GET /health` — `{ "status": "ok" }`. Kept off the `/api` prefix because the
  deploy script and container health probe poll it directly. Open, so the probe
  needs no credentials.
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
   - `GOOGLE_CLIENT_ID` — the **Web** OAuth client ID (see below). For this
     submission that value is
     `294961049928-tqu94trnk3bqmce77bs2armgtht67qpp.apps.googleusercontent.com`.
     It is not a secret; it ships inside every APK.

   `local.properties` is gitignored, so these never reach the repository.
   Neither missing value fails the build: an unset `API_BASE_URL` silently
   falls back to `http://10.0.2.2:3000`, which points at the build machine
   rather than the server, and an unset `GOOGLE_CLIENT_ID` produces an APK that
   cannot sign in. Properties format escapes colons, so the deployed value is
   written `API_BASE_URL=https\://52.35.22.194\:3000`.

3. Sign in to a Google account on the emulator (Settings > Passwords &
   accounts). A freshly created emulator has no account, and Credential
   Manager can only offer accounts already on the device — so the app's
   sign-in screen shows an **Add a Google account** button that opens this
   screen for you if none is present.

   The OAuth consent screen is **published**, so any Google account can sign
   in. A test account is listed in `M1_Doc.pdf` if you would rather not use a
   personal one.

4. `./scripts/run-frontend.sh`

### Google sign-in

Credential Manager with Google Identity Services directly, not Firebase.

The token is verified on the server, not trusted from the client. The flow is:

1. The app asks Credential Manager for a Google ID token, naming the **Web**
   client ID as its server client ID — so Google mints the token with that ID
   as its audience.
2. The app posts the token to `POST /api/auth/google`. It deliberately ignores
   the name Credential Manager also returns: anything read on the device has
   only the client's word behind it.
3. The server calls `verifyIdToken` from `google-auth-library`, which checks the
   RS256 signature against Google's published keys, that `aud` matches
   `GOOGLE_CLIENT_ID`, that `iss` is Google, and that the token has not expired.
   A forged, replayed, or foreign-audience token fails here.
4. The verified `given_name` / `family_name` come back to the app, and that is
   what the Button 1 screen shows as the signed-in user.
5. Every later call to `/api/name`, `/api/time`, and `/api/ip` carries the same
   token, which `requireGoogleAuth` re-verifies per request.

`google-auth-library` is used only to check the token's signature against
Google's keys. The routing, middleware, and request handling around it are our
own — the course staff approved the library on that basis.

Two OAuth clients in one Google Cloud project:

- An **Android** client registering `com.example.cpen321application` with the
  SHA-1 of the signing key. Its client ID is never used in code.
- A **Web application** client, whose ID goes in `GOOGLE_CLIENT_ID`.

Debug key SHA-1:

```bash
keytool -list -v -keystore ~/.android/debug.keystore \
  -alias androiddebugkey -storepass android -keypass android | grep SHA1
```

The release build is signed with this same debug keystore, so that one SHA-1
covers both `installDebug` and the `assembleRelease` APK that gets submitted.
To sign with a real release key instead, set `RELEASE_KEYSTORE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` in
`local.properties` and register that key's SHA-1 too.

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
   - `GOOGLE_CLIENT_ID` — **required.** The Web OAuth client ID that ID tokens
     are verified against. It must match `GOOGLE_CLIENT_ID` in
     `frontend/local.properties`, or every request is rejected as
     wrong-audience. The server refuses to start without it, rather than
     running with authentication that accepts anything. `.env.example` already
     carries this submission's value; it is not a secret.

3. `./scripts/run-backend.sh` — builds and starts the Compose stack, waiting on
   `/health`. Stop with `docker compose down`.

4. `./scripts/run-backend-interface-tests.sh` — runs the interface tests with
   coverage. This milestone has tests in `tests/no-mock/` only, so the script
   reports that it is skipping the mocked suite and writes coverage to
   `backend/coverage/no-mock`. The mocked and combined suites run automatically
   once `tests/mock/` has test files.

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
