// Test script for demo user authentication
const WebSocket = require('ws');

console.log('Testing demo user authentication with WebSocket...');
console.log('');

console.log('Testing WebSocket server with demo token...');

// Test health endpoint first
const https = require('https');

https.get('https://travel-agency-backend-387994411670.asia-south1.run.app/api/health', (res) => {
  let data = '';
  res.on('data', (chunk) => data += chunk);
  res.on('end', () => {
    console.log('Health check response:', JSON.parse(data));
  });
});

// Test WebSocket connection with demo token
const ws = new WebSocket('wss://travel-agency-backend-387994411670.asia-south1.run.app/ws');

ws.on('open', function open() {
  console.log('WebSocket connection opened');

  // Test AUTH message with demo token (like Android app sends)
  const demoToken = 'guest-token-demo-user-123';
  const authMessage = {
    type: 'AUTH',
    payload: { token: demoToken },
    timestamp: Date.now()
  };

  console.log('Sending AUTH message with demo token...');
  ws.send(JSON.stringify(authMessage));
});

ws.on('message', function message(data) {
  try {
    const response = JSON.parse(data.toString());
    console.log('📨 Received:', JSON.stringify(response, null, 2));

    if (response.type === 'AUTH' && response.payload && response.payload.success === true) {
      console.log('✅ AUTH test passed - demo user authenticated successfully');
      console.log('User ID:', response.payload.user.id);
      console.log('Display Name:', response.payload.user.displayName);

      // Test sending a message after successful auth
      setTimeout(() => {
        const messagePayload = {
          type: 'MESSAGE',
          payload: {
            to: 'customer-user-id', // Some customer user ID
            content: 'Hello from demo user!'
          },
          id: 'test-message-' + Date.now(),
          timestamp: Date.now()
        };
        console.log('🏓 Sending test message...');
        ws.send(JSON.stringify(messagePayload));
      }, 1000);
    } else if (response.type === 'MESSAGE') {
      console.log('✅ Message sent successfully');
    } else if (response.type === 'AUTH' && response.payload && response.payload.success === false) {
      console.log('❌ AUTH test failed - authentication rejected');
      console.log('Error:', response.payload.error);
      // Don't close immediately, wait for potential error response
    } else if (response.type === 'ERROR') {
      console.log('❌ Server error:', response.payload.error);
      // Close after receiving error
      setTimeout(() => {
        console.log('⏰ Closing test connection after error...');
        ws.close();
      }, 1000);
    } else {
      console.log('⚠️  Unknown message type:', response.type);
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
  console.log('✅ Demo auth test completed');
});

ws.on('error', function error(err) {
  console.error('🚨 WebSocket error:', err);
});

// Close after 10 seconds to allow for message exchange
setTimeout(() => {
  console.log('⏰ Timeout reached, closing connection...');
  ws.close();
}, 10000);
