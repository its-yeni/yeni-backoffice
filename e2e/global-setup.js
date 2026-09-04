const { spawn, spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');

const HOST = '127.0.0.1';
const PORT = 8081;
const READY_URL = `http://${HOST}:${PORT}/admin/commerce/products`;

function portIsOpen() {
  return new Promise(resolve => {
    const socket = net.createConnection({ host: HOST, port: PORT });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
    socket.setTimeout(500, () => { socket.destroy(); resolve(false); });
  });
}

async function waitUntilReady(child, timeoutMs = 300_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`E2E server exited before it became ready (code ${child.exitCode}).`);
    }
    try {
      const response = await fetch(READY_URL, { signal: AbortSignal.timeout(5_000) });
      if (response.ok && await demoDataIsReady()) return;
    } catch (_) {
      // The server is still starting or demo data is still being initialized.
    }
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  throw new Error(`E2E server did not become ready within ${timeoutMs}ms.`);
}

async function demoDataIsReady() {
  try {
    const response = await fetch(`http://${HOST}:${PORT}/admin/api/settlements`, {
      signal: AbortSignal.timeout(5_000)
    });
    if (!response.ok) return false;
    const statements = await response.json();
    for (const statement of statements.filter(row => row.settlementStatus === 'DRAFT')) {
      const detailResponse = await fetch(`http://${HOST}:${PORT}/admin/api/settlements/${statement.id}`, {
        signal: AbortSignal.timeout(5_000)
      });
      if (detailResponse.ok && (await detailResponse.json()).reconciliation?.confirmable) return true;
    }
  } catch (_) {
    // CommandLineRunner 기반 데모 데이터가 아직 생성 중이다.
  }
  return false;
}

function stopProcessTree(pid) {
  if (process.platform === 'win32') {
    spawnSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
    return;
  }
  try { process.kill(pid, 'SIGTERM'); } catch (_) { /* already stopped */ }
}

module.exports = async () => {
  if (await portIsOpen()) {
    throw new Error(`Port ${PORT} is already in use. Stop the stale E2E server and retry.`);
  }

  const logDirectory = path.join(process.cwd(), 'build');
  fs.mkdirSync(logDirectory, { recursive: true });
  const logPath = path.join(logDirectory, 'e2e-server.log');
  const logFd = fs.openSync(logPath, 'w');
  const child = spawn('java', [
    '-Dspring.devtools.restart.enabled=false',
    '-jar', 'api/build/libs/yeni-backoffice-e2e.jar',
    `--server.port=${PORT}`,
    '--spring.datasource.url=jdbc:h2:mem:yeni-e2e',
    '--spring.jpa.hibernate.ddl-auto=create-drop',
    '--spring.devtools.restart.enabled=false'
  ], {
    cwd: process.cwd(),
    env: { ...process.env, JAVA_TOOL_OPTIONS: '-Dspring.devtools.restart.enabled=false' },
    stdio: ['ignore', logFd, logFd],
    windowsHide: true
  });
  child.once('exit', (code, signal) => {
    fs.appendFileSync(logPath, `\n[E2E SERVER EXIT] code=${code} signal=${signal}\n`);
  });

  try {
    await waitUntilReady(child);
  } catch (error) {
    stopProcessTree(child.pid);
    throw error;
  }

  return async () => {
    stopProcessTree(child.pid);
    fs.closeSync(logFd);
  };
};
