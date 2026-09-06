import { spawn } from 'node:child_process';
import path from 'node:path';

const PAIRING = process.env.VIBEADB_PAIRING || '';
if (!PAIRING) {
  console.error('Error: VIBEADB_PAIRING environment variable not set.');
  process.exit(1);
}
const CMD = process.argv[2] || 'echo ok';
let timeoutSec = 30;
if (process.argv[3] && !isNaN(parseInt(process.argv[3]))) {
  timeoutSec = parseInt(process.argv[3]);
}

const mcpDir = path.resolve('D:/Projects/copilot/vibeADB/mcp');
const child = spawn('node', ['dist/index.js'], {
  cwd: mcpDir,
  env: { ...process.env, VIBEADB_PAIRING: PAIRING },
  stdio: ['pipe', 'pipe', 'pipe']
});

let buf = '';
child.stdout.on('data', d => { buf += d.toString(); });

function send(obj) { child.stdin.write(JSON.stringify(obj) + '\n'); }

send({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'shell-runner', version: '1.0' } } });

setTimeout(() => {
  send({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'shell', arguments: { command: CMD, timeoutSec: timeoutSec } } });
}, 1000);

const waitTime = (timeoutSec + 10) * 1000;
setTimeout(() => {
  const lines = buf.split('\n').filter(l => l.includes('"id":2'));
  if (lines.length) {
    try {
      const r = JSON.parse(lines[0]);
      const txt = r.result?.content?.[0]?.text || JSON.stringify(r);
      console.log(txt);
    } catch {
      console.log(lines[0]);
    }
  } else {
    console.log('(无响应 / 超时)');
  }
  child.kill();
  process.exit(0);
}, waitTime);
