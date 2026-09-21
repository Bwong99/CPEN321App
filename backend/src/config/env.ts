import 'dotenv/config';

const rawPort = process.env.PORT;
const port =
  rawPort === undefined || rawPort === ''
    ? 3000
    : Number.parseInt(rawPort, 10);

if (Number.isNaN(port) || port < 1 || port > 65535) {
  throw new Error(`Invalid PORT: ${rawPort}`);
}

// A cloud instance sits behind NAT and cannot discover its own public address,
// so the deployed server is told what it is. Left unset during local
// development, where the server falls back to scanning network interfaces.
const rawServerPublicIp = process.env.SERVER_PUBLIC_IP?.trim();
const serverPublicIp =
  rawServerPublicIp === undefined || rawServerPublicIp === ''
    ? undefined
    : rawServerPublicIp;

// M1 requires the app to reach the back-end over HTTPS. TLS is switched on by
// placing a certificate and key in backend/certs/ (see README) and setting
// this flag; local development stays on plain HTTP.
const httpsEnabled = process.env.ENABLE_HTTPS?.trim().toLowerCase() === 'true';

// The Web OAuth client ID that Google ID tokens must be issued to. The app
// sends the token it gets from Credential Manager and the server verifies it
// against this audience, so a token minted for some other project is refused.
// Not a secret — it ships inside the APK — but the server cannot verify
// anything without it, so start-up fails rather than silently accepting none.
const googleClientId = process.env.GOOGLE_CLIENT_ID?.trim();

if (googleClientId === undefined || googleClientId === '') {
  throw new Error(
    'GOOGLE_CLIENT_ID is not set. Copy backend/.env.example to backend/.env ' +
      'and set it to the Web OAuth client ID (see README).'
  );
}

export const env = {
  port,
  serverPublicIp,
  httpsEnabled,
  googleClientId,
} as const;
