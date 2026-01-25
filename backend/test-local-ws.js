// Test script for local WebSocket server
const WebSocket = require('ws');

console.log('Testing local WebSocket server...');
console.log('Connecting to ws://localhost:8080/api/chat/websocket');
console.log('');

// Test WebSocket connection to local server
const ws = new WebSocket('ws://localhost:8080/api/chat/websocket');

ws.on('open', function open() {
  console.log('✅ WebSocket connection opened to local server');

  // Send demo authentication
  const demoToken = 'guest-token-demo-user-123';
  const authMessage = {
    type: 'AUTH',
    payload: { token: demoToken },
    timestamp: Date.now()
  };

  console.log('🔐 Sending AUTH message with demo token...');
  ws.send(JSON.stringify(authMessage));
});

ws.on('message', function message(data) {
  try {
    const response = JSON.parse(data.toString());
    console.log('📨 Received:', JSON.stringify(response, null, 2));

    if (response.type === 'AUTH' && response.payload?.success === true) {
      console.log('✅ Authentication successful!');
      console.log('User ID:', response.payload.user.id);
      console.log('Display Name:', response.payload.user.displayName);

      // Now send a test message
      setTimeout(() => {
        const messageData = {
          type: 'MESSAGE',
          payload: {
            to: 'test-recipient-user',
            content: 'Hello from local WebSocket test! Time: ' + new Date().toISOString()
          },
          timestamp: Date.now()
        };

        console.log('📤 Sending test message...');
        ws.send(JSON.stringify(messageData));
      }, 1000);

    } else if (response.type === 'MESSAGE') {
      console.log('✅ Message sent successfully!');
      console.log('Message ID:', response.payload.id);
      console.log('From:', response.payload.from);
      console.log('To:', response.payload.to);
      console.log('Content:', response.payload.content);

      // Close connection after successful test
      setTimeout(() => {
        console.log('🎉 Test completed successfully!');
        ws.close();
      }, 1000);

    } else if (response.type === 'ERROR') {
      console.log('❌ Error:', response.payload.error);
      ws.close();
    } else if (response.type === 'AUTH' && response.payload?.success === false) {
      console.log('❌ Authentication failed:', response.payload.error);
      ws.close();
    }
  } catch (error) {
    console.error('❌ Error parsing message:', error);
    console.log('Raw data:', data.toString());
  }
});

ws.on('close', function close(code, reason) {
  console.log('🔌 WebSocket connection closed');
  console.log('   Code:', code);
  console.log('   Reason:', reason.toString());
  if (code === 1000) {
    console.log('✅ Connection closed normally');
  } else {
    console.log('⚠️  Connection closed with code:', code);
  }
});

ws.on('error', function error(err) {
  console.error('🚨 WebSocket error:', err);
});

// Timeout after 15 seconds
setTimeout(() => {
  console.log('⏰ Test timeout reached');
  ws.close();
}, 15000);
