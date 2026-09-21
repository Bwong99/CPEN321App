import { networkInterfaces, type NetworkInterfaceInfo } from 'node:os';

import express, { type Express, type Request, type Response } from 'express';

import { env } from './config/env';
import { describe, requireGoogleAuth, verifyGoogleIdToken } from './googleAuth';

// The developer name reported by GET /api/name, as required by M1.
const OWNER_FIRST_NAME = 'Bradley';
const OWNER_LAST_NAME = 'Wong';

// Built once and reused: constructing an Intl formatter is comparatively
// expensive, and the options never vary between requests.
//
// `longOffset` renders the zone as `GMT+hh:mm` / `GMT-hh:mm`, including on a
// UTC host (`GMT+00:00`), which is what a cloud instance will normally report.
const SERVER_TIME_FORMATTER = new Intl.DateTimeFormat('en-GB', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
  timeZoneName: 'longOffset',
});

export function createApp(): Express {
  const app = express();

  // Only the sign-in route has a body; the limit keeps an oversized one from
  // being buffered before it is rejected.
  app.use(express.json({ limit: '4kb' }));

  // Stays off the /api prefix: scripts/run-backend.sh and the Docker health
  // probe both poll /health directly.
  app.get('/health', (_req, res) => {
    res.json({ status: 'ok' });
  });

  // Exchanges the ID token the app gets from Credential Manager for the
  // identity behind it. The app shows the name from this response rather than
  // the one it read out of the token itself, so what reaches the screen is
  // what the server verified.
  app.post('/api/auth/google', handleGoogleSignIn);

  // Everything below needs a valid ID token. Registered per route rather than
  // on the whole /api prefix so /health and the sign-in route above stay open.
  app.get('/api/name', requireGoogleAuth, (_req, res) => {
    res.json({ firstName: OWNER_FIRST_NAME, lastName: OWNER_LAST_NAME });
  });

  app.get('/api/time', requireGoogleAuth, (_req, res) => {
    // Evaluated per request: M1 asks for the server time at the time of call,
    // in 24-hour format relative to GMT.
    res.json({ time: SERVER_TIME_FORMATTER.format(new Date()) });
  });

  app.get('/api/ip', requireGoogleAuth, (_req, res) => {
    try {
      res.json({ ip: resolveServerIp() });
    } catch {
      res
        .status(500)
        .json({ error: 'Unable to determine the server IP address' });
    }
  });

  app.use((_req, res) => {
    res.status(404).json({ error: 'Not Found' });
  });

  return app;
}

/**
 * POST /api/auth/google — verifies `{ idToken }` and answers with the account
 * it belongs to.
 *
 * A 401 here means the token did not check out against Google; a 400 means the
 * request never carried one.
 */
async function handleGoogleSignIn(req: Request, res: Response): Promise<void> {
  const { idToken } = req.body as { idToken?: unknown };

  if (typeof idToken !== 'string' || idToken.trim() === '') {
    res.status(400).json({ error: 'Request body must contain an idToken' });
    return;
  }

  try {
    const user = await verifyGoogleIdToken(idToken);
    res.json({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
    });
  } catch (cause) {
    console.error(`Google sign-in rejected: ${describe(cause)}`);
    res.status(401).json({ error: 'Invalid or expired Google ID token' });
  }
}

/**
 * Resolves the server's IP address for GET /api/ip.
 *
 * SERVER_PUBLIC_IP is authoritative when set, because a cloud instance behind
 * NAT sees only its private address locally. The interface scan below is a
 * development convenience so the endpoint works before anything is deployed.
 *
 * @throws if no address can be determined.
 */
function resolveServerIp(): string {
  if (env.serverPublicIp !== undefined) {
    return env.serverPublicIp;
  }

  const localAddress = findNonInternalAddress();
  if (localAddress === undefined) {
    throw new Error(
      'No non-internal network interface found; set SERVER_PUBLIC_IP.'
    );
  }

  return localAddress;
}

function findNonInternalAddress(): string | undefined {
  const candidates: NetworkInterfaceInfo[] = [];

  // Object.values rather than a keyed lookup: indexing by a computed key trips
  // the security/detect-object-injection rule Codacy enforces.
  for (const addresses of Object.values(networkInterfaces())) {
    for (const candidate of addresses ?? []) {
      if (!candidate.internal && !isLinkLocal(candidate.address)) {
        candidates.push(candidate);
      }
    }
  }

  // IPv4 first: the LAN address is the one a physical Android device on the
  // same Wi-Fi can actually reach during development.
  return (
    candidates.find((candidate) => candidate.family === 'IPv4')?.address ??
    candidates[0]?.address
  );
}

/**
 * Link-local addresses (IPv4 169.254.0.0/16, IPv6 fe80::/10) are self-assigned
 * and not routable, so they are never a useful answer for "where is this
 * server", even though Node reports them as non-internal.
 */
function isLinkLocal(address: string): boolean {
  return (
    address.startsWith('169.254.') || address.toLowerCase().startsWith('fe80')
  );
}
