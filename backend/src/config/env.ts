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

export const env = {
  port,
  serverPublicIp,
  httpsEnabled,
} as const;
