import type { Server as HttpServer } from 'node:http';
import type { Server as HttpsServer } from 'node:https';

import { WebSocket, WebSocketServer, type RawData } from 'ws';

/** The course-provided pixel stream. */
const UPSTREAM_URL = 'wss://8.229.22.124';

/** Path the Android app connects to on this server. */
export const PIXEL_RELAY_PATH = '/ws/pixels';

const RECONNECT_DELAY_MS = 2_000;

/**
 * Relays the course pixel stream to connected app clients.
 *
 * The upstream connection is opened when the first app client arrives and
 * closed when the last one leaves, so the server does not hold a socket open
 * while nobody is watching.
 */
export function attachPixelRelay(
  server: HttpServer | HttpsServer
): WebSocketServer {
  const wss = new WebSocketServer({ server, path: PIXEL_RELAY_PATH });

  let upstream: WebSocket | undefined;
  // ReturnType<typeof setTimeout> rather than NodeJS.Timeout: the NodeJS
  // global namespace is not declared under the lint config Codacy runs.
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

  function broadcast(data: RawData, isBinary: boolean): void {
    for (const client of wss.clients) {
      if (client.readyState === WebSocket.OPEN) {
        // Forwarded verbatim. M1 requires each update to be relayed
        // immediately, without batching, delaying, or reformatting.
        client.send(data, { binary: isBinary });
      }
    }
  }

  function openUpstream(): void {
    if (upstream !== undefined) {
      return;
    }

    // The course server presents a self-signed certificate for a bare IP, so
    // chain verification cannot succeed. This is scoped to this one outbound
    // connection and does not affect the certificate this server presents.
    const socket = new WebSocket(UPSTREAM_URL, { rejectUnauthorized: false });
    upstream = socket;

    socket.on('message', broadcast);
    socket.on('close', scheduleReconnect);
    socket.on('error', (cause: Error) => {
      console.error(`Pixel upstream error: ${cause.message}`);
    });
  }

  function scheduleReconnect(): void {
    upstream = undefined;

    // Only worth reconnecting while somebody is still watching.
    if (wss.clients.size === 0 || reconnectTimer !== undefined) {
      return;
    }

    reconnectTimer = setTimeout(() => {
      reconnectTimer = undefined;
      openUpstream();
    }, RECONNECT_DELAY_MS);
  }

  function closeUpstream(): void {
    if (reconnectTimer !== undefined) {
      clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
    }

    const socket = upstream;
    upstream = undefined;

    if (socket !== undefined) {
      // Drop the close handler first, so a deliberate close does not schedule
      // a reconnect.
      socket.removeListener('close', scheduleReconnect);
      socket.close();
    }
  }

  wss.on('connection', (client) => {
    openUpstream();

    client.on('close', () => {
      if (wss.clients.size === 0) {
        closeUpstream();
      }
    });
  });

  return wss;
}
