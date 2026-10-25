// Dev-server proxy: /api goes to the Spring Boot backend on port 8080.
//
// Inside WSL2, "localhost" is the Linux VM, not Windows, and the backend may run in either.
// So when the dev server runs in WSL, use whichever of the two has something listening on 8080:
// WSL's own localhost first, then the Windows host (the VM's default gateway). Set API_TARGET
// to skip the detection. The check runs once, when the dev server starts: start the backend first.
import { readFileSync } from 'node:fs';
import { connect } from 'node:net';
import { release } from 'node:os';

const PORT = 8080;

function windowsHostFromWsl() {
  try {
    // /proc/net/route: the default route has destination 00000000; the gateway is little-endian hex.
    for (const line of readFileSync('/proc/net/route', 'utf8').split('\n').slice(1)) {
      const [, destination, gateway] = line.trim().split(/\s+/);
      if (destination === '00000000' && gateway && gateway !== '00000000') {
        return gateway.match(/../g).reverse().map(h => parseInt(h, 16)).join('.');
      }
    }
  } catch {
    // not readable
  }
  return null;
}

/** True if something accepts connections on host:port within the timeout. */
function listening(host, timeoutMs = 700) {
  return new Promise(resolve => {
    const socket = connect({ host, port: PORT });
    const done = ok => { socket.destroy(); resolve(ok); };
    socket.setTimeout(timeoutMs, () => done(false));
    socket.once('connect', () => done(true));
    socket.once('error', () => done(false));
  });
}

async function pickTarget() {
  if (process.env.API_TARGET) {
    return { target: process.env.API_TARGET, why: 'from API_TARGET' };
  }
  if (!/microsoft/i.test(release())) {
    return { target: `http://localhost:${PORT}`, why: 'default' };
  }
  if (await listening('127.0.0.1')) {
    return { target: `http://localhost:${PORT}`, why: 'backend found inside WSL' };
  }
  const host = windowsHostFromWsl();
  if (host && await listening(host)) {
    return { target: `http://${host}:${PORT}`, why: 'backend found on the Windows host' };
  }
  return { target: `http://localhost:${PORT}`, why: 'no backend running yet; start it, then restart npm start' };
}

const { target, why } = await pickTarget();
console.log(`[proxy] /api -> ${target} (${why})`);

export default {
  '/api': {
    target,
    secure: false,
  },
};
