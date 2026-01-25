// Simple WebSocket test script
const WebSocket = require('ws');

console.log('Testing WebSocket connection to remote backend...');
console.log('');

console.log('Testing WebSocket server...');

// Test health endpoint first
const https = require('https');

https.get('https://travel-agency-backend-j6kdth6uzq-el.a.run.app/api/health', (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    console.log('Health check response:', JSON.parse(data));
  });
});

// Test WebSocket connection
const ws = new WebSocket('wss://travel-agency-backend-j6kdth6uzq-el.a.run.app/api/chat/websocket');

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
  try {
    const response = JSON.parse(data.toString());
    console.log('📨 Received:', JSON.stringify(response, null, 2));

    if (response.type === 'AUTH' && response.payload && response.payload.success === false) {
      console.log('✅ AUTH test passed - correctly rejected invalid token');
    } else if (response.type === 'ERROR') {
      console.log('❌ Server error:', response.payload.error);
    } else if (response.type === 'PONG') {
      console.log('✅ PING test passed - received PONG');
    }

    // Test PING after receiving AUTH response
    if (response.type === 'AUTH') {
      setTimeout(() => {
        const pingMessage = {
          type: 'PING',
          timestamp: Date.now()
        };
        console.log('🏓 Sending PING message...');
        ws.send(JSON.stringify(pingMessage));
      }, 1000);
    }
  } catch (error) {
    console.error('❌ Error parsing message:', error);
    console.log('Raw message:', data.toString());
  }
});

ws.on('close', function close(code, reason) {
  console.log('🔌 WebSocket connection closed');
  console.log('   Code:', code);
  console.log('   Reason:', reason.toString());
  console.log('✅ WebSocket test completed');
});

ws.on('error', function error(err) {
  console.error('🚨 WebSocket error:', err);
});

// Close after 10 seconds to allow for message exchange
setTimeout(() => {
  console.log('⏰ Timeout reached, closing connection...');
  ws.close();
}, 10000);
