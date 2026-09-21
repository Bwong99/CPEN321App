import type { NextFunction, Request, Response } from 'express';
import { OAuth2Client } from 'google-auth-library';

import { env } from './config/env';

/** The identity the back-end is willing to vouch for, read from a verified token. */
export interface GoogleUser {
  /** The Google account id (`sub`). Stable across name and email changes. */
  id: string;
  firstName: string;
  lastName: string;
  email: string;
}

/**
 * Verifies ID tokens against Google's published signing keys.
 *
 * Constructed once: the client caches Google's key set, so a per-request
 * instance would refetch it on every call. No credentials are passed, because
 * verification only needs the public keys.
 */
const tokenVerifier = new OAuth2Client();

/**
 * Checks a Google ID token and returns who it belongs to.
 *
 * `verifyIdToken` is what makes this trustworthy rather than decorative: it
 * checks the RS256 signature against Google's keys, that `aud` is this
 * project's OAuth client, that `iss` is Google, and that the token has not
 * expired. A token the app invented, replayed from another project, or edited
 * fails here.
 *
 * @throws if the token is invalid, expired, or issued to another audience.
 */
export async function verifyGoogleIdToken(idToken: string): Promise<GoogleUser> {
  const ticket = await tokenVerifier.verifyIdToken({
    idToken,
    audience: env.googleClientId,
  });

  const payload = ticket.getPayload();
  if (payload === undefined) {
    throw new Error('Token carried no payload.');
  }

  // Google only sets this once it has confirmed the address belongs to the
  // account, so an unverified one is not an identity worth trusting.
  if (payload.email_verified !== true) {
    throw new Error('Google account has no verified email address.');
  }

  return {
    id: payload.sub,
    firstName: payload.given_name ?? '',
    lastName: payload.family_name ?? '',
    email: payload.email ?? '',
  };
}

/**
 * Rejects a request unless it carries a valid Google ID token in
 * `Authorization: Bearer <token>`.
 *
 * Applied to the three M1 data endpoints, so the app has to prove who it is
 * before the server answers.
 */
export async function requireGoogleAuth(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  const idToken = readBearerToken(req.get('authorization'));
  if (idToken === undefined) {
    res
      .status(401)
      .json({ error: 'Missing Authorization: Bearer <Google ID token> header' });
    return;
  }

  try {
    await verifyGoogleIdToken(idToken);
    next();
  } catch (cause) {
    // The reason is logged rather than returned: telling a caller exactly why
    // a token was rejected helps them forge a better one.
    console.error(`Rejected ID token: ${describe(cause)}`);
    res.status(401).json({ error: 'Invalid or expired Google ID token' });
  }
}

/** Pulls the token out of an `Authorization` header, if it is a bearer one. */
function readBearerToken(header: string | undefined): string | undefined {
  if (header === undefined) {
    return undefined;
  }

  const [scheme, token] = header.split(' ');
  if (scheme?.toLowerCase() !== 'bearer' || token === undefined) {
    return undefined;
  }

  const trimmed = token.trim();
  return trimmed === '' ? undefined : trimmed;
}

export function describe(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
