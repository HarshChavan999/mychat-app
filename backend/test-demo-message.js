// Test script to send a message via WebSocket
const WebSocket = require('ws');

console.log('Testing WebSocket message sending...');
console.log('');

// Test WebSocket connection
const ws = new WebSocket('wss://travel-agency-backend-j6kdth6uzq-el.a.run.app/api/chat/websocket');

ws.on('open', function open() {
  console.log('✅ WebSocket connection opened');

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

      // Now send a test message
      setTimeout(() => {
        const messageData = {
          type: 'MESSAGE',
          payload: {
            to: 'test-recipient',
            content: 'Hello from WebSocket test! Timestamp: ' + Date.now()
          },
          timestamp: Date.now()
        };

        console.log('📤 Sending test message...');
        ws.send(JSON.stringify(messageData));
      }, 1000);

    } else if (response.type === 'MESSAGE') {
      console.log('✅ Message sent successfully!');
      console.log('Message ID:', response.payload.id);
    } else if (response.type === 'ERROR') {
      console.log('❌ Error:', response.payload.error);
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
});

ws.on('error', function error(err) {
  console.error('🚨 WebSocket error:', err);
});

// Close after 10 seconds
setTimeout(() => {
  console.log('⏰ Test timeout, closing connection...');
  ws.close();
}, 10000);
