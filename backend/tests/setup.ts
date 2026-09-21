// src/config/env.ts refuses to load without a client ID, because a server that
// cannot verify tokens should not start. The tests do not reach Google, so any
// well-formed value will do here.
process.env.GOOGLE_CLIENT_ID ??= 'test-client-id.apps.googleusercontent.com';
