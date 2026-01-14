// Simple WebSocket test script
const WebSocket = require('ws');

console.log('Testing WebSocket connection to backend...');
console.log('Make sure the backend server is running on port 8080');
console.log('');

console.log('Testing WebSocket server...');

// Test health endpoint first
const http = require('http');

http.get('http://localhost:8080/health', (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    console.log('Health check response:', JSON.parse(data));
  });
});

// Test WebSocket connection
const ws = new WebSocket('ws://localhost:8080');

ws.on('open', function open() {
  console.log('WebSocket connection opened');

  // Test AUTH message (without token - should fail)
  const authMessage = {
    type: 'AUTH',
    payload: { token: 'invalid-token' },
    timestamp: Date.now()
  };

  console.log('Sending AUTH message...');
  ws.send(JSON.stringify(authMessage));
});

ws.on('message', function message(data) {
  const response = JSON.parse(data.toString());
  console.log('Received:', response);

  if (response.type === 'AUTH' && response.payload.success === false) {
    console.log('✅ AUTH test passed - correctly rejected invalid token');
  }

  // Test PING
  if (response.type !== 'PONG') {
    const pingMessage = {
      type: 'PING',
      timestamp: Date.now()
    };
    console.log('Sending PING message...');
    ws.send(JSON.stringify(pingMessage));
  }
});

ws.on('close', function close(code, reason) {
  console.log('WebSocket connection closed:', code, reason.toString());
  console.log('✅ WebSocket test completed');
});

ws.on('error', function error(err) {
  console.error('WebSocket error:', err);
});

// Close after 5 seconds
setTimeout(() => {
  ws.close();
}, 5000);
