import { spawn } from 'node:child_process';
import path from 'node:path';

const PAIRING = process.env.VIBEADB_PAIRING || '';
if (!PAIRING) {
  console.error('Error: VIBEADB_PAIRING environment variable not set.');
  process.exit(1);
}
const TOOL = process.argv[2] || 'device_status';
let ARGS = {};
try {
  if (process.argv[3]) {
    ARGS = JSON.parse(process.argv[3]);
  }
} catch {
  ARGS = { command: process.argv[3] };
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

send({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'tool-runner', version: '1.0' } } });

setTimeout(() => {
  send({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: TOOL, arguments: ARGS } });
}, 1000);

setTimeout(() => {
  const lines = buf.split('\n').filter(l => l.includes('"id":2'));
  if (lines.length) {
    try {
      const r = JSON.parse(lines[0]);
      console.log(JSON.stringify(r.result || r));
    } catch {
      console.log(lines[0]);
    }
  } else {
    console.log('{"error": "timeout"}');
  }
  child.kill();
  process.exit(0);
}, 35000);
