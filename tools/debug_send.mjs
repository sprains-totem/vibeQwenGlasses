// vibeADB 动态调试客户端：一键发送调试广播并读取实时回包
import { spawn } from 'node:child_process';
import path from 'node:path';

const PAIRING = process.env.VIBEADB_PAIRING || '';
if (!PAIRING) {
  console.error('Error: VIBEADB_PAIRING environment variable not set.');
  process.exit(1);
}
const mcpDir = path.resolve('D:/Projects/copilot/vibeADB/mcp');

const type = process.argv[2] || 'status';
const arg1 = process.argv[3] || '';
const arg2 = process.argv[4] || '';

let broadcastCmd = '';
switch (type) {
  case 'hex':
    broadcastCmd = `am broadcast -a com.vibeqwen.glasses.DEBUG_SEND_HEX --es hex "${arg1}" ${arg2 ? `--ei channel ${arg2}` : ''}`;
    break;
  case 'gcsp':
    broadcastCmd = `am broadcast -a com.vibeqwen.glasses.DEBUG_SEND_GCSP --es json '${arg1}' ${arg2 ? `--ei cid ${arg2}` : '--ei cid 1'}`;
    break;
  case 'auth':
    broadcastCmd = `am broadcast -a com.vibeqwen.glasses.DEBUG_AUTH --ei productId ${arg1 || 8518} --es randomA "${arg2 || 'auto'}"`;
    break;
  case 'listen':
    broadcastCmd = `am broadcast -a com.vibeqwen.glasses.DEBUG_LISTEN_RFCOMM --es uuid "${arg1 || '00001101-0000-1000-8000-00805F9B34FB'}"`;
    break;
  case 'connect':
    broadcastCmd = `am broadcast -a com.vibeqwen.glasses.DEBUG_CONNECT --es mac "${arg1 || 'AA:BB:CC:DD:EE:FF'}" --ei psm ${arg2 || 130}`;
    break;
  case 'status':
  default:
    broadcastCmd = `am broadcast -a com.vibeqwen.glasses.DEBUG_STATUS`;
    break;
}

const fullCmd = `${broadcastCmd} && sleep 2 && cat /storage/emulated/0/Android/data/com.vibeqwen.glasses/files/logs/latest.log | tail -25`;

const child = spawn('node', ['dist/index.js'], {
  cwd: mcpDir,
  env: { ...process.env, VIBEADB_PAIRING: PAIRING },
  stdio: ['pipe', 'pipe', 'pipe']
});

let buf = '';
child.stdout.on('data', d => { buf += d.toString(); });
child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize', params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 'debug-client', version: '1.0' } } }) + '\n');

setTimeout(() => {
  child.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: 2, method: 'tools/call', params: { name: 'shell', arguments: { command: fullCmd, timeoutSec: 20 } } }) + '\n');
}, 1000);

setTimeout(() => {
  const line = buf.split('\n').find(l => l.includes('"id":2'));
  if (line) {
    try {
      const res = JSON.parse(line).result?.content?.[0]?.text;
      console.log(res);
    } catch {
      console.log(line);
    }
  } else {
    console.log('Timeout');
  }
  child.kill();
  process.exit(0);
}, 25000);