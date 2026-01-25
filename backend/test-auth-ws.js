// Test WebSocket with authentication
const WebSocket = require('ws');

console.log('Testing WebSocket connection with authentication...');

const ws = new WebSocket('wss://travel-agency-backend-j6kdth6uzq-el.a.run.app/api/chat/websocket');

ws.on('open', function open() {
  console.log('✅ WebSocket connection opened successfully');

  // Wait a bit for the connection to be fully established
  setTimeout(() => {
    // Send authentication message
    const authMessage = {
      type: 'AUTH',
      payload: { token: 'guest-token-demo-user-123' },
      timestamp: Date.now()
    };

    console.log('📤 Sending authentication message...');
    ws.send(JSON.stringify(authMessage));
  }, 1000); // Wait 1 second
});

ws.on('message', function message(data) {
  console.log('📨 Message received:', data.toString());

  try {
    const msg = JSON.parse(data.toString());
    if (msg.type === 'AUTH' && msg.payload?.success) {
      console.log('✅ Authentication successful!');
      console.log('Connection will stay open for 10 seconds...');

      // Keep connection open for longer to test stability
      setTimeout(() => {
        console.log('⏰ Test completed, closing connection...');
        ws.close(1000, 'Test completed');
      }, 10000);
    } else if (msg.type === 'AUTH' && !msg.payload?.success) {
      console.log('❌ Authentication failed:', msg.payload?.error);
      ws.close();
    }
  } catch (e) {
    console.log('📨 Raw message:', data.toString());
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
