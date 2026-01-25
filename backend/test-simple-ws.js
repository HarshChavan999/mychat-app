// Very simple WebSocket test - just connect and see if it stays open
const WebSocket = require('ws');

console.log('Testing basic WebSocket connection...');

const ws = new WebSocket('wss://travel-agency-backend-j6kdth6uzq-el.a.run.app/api/chat/websocket');

ws.on('open', function open() {
  console.log('✅ WebSocket connection opened successfully');
  console.log('Connection is stable for 3 seconds...');
});

ws.on('message', function message(data) {
  console.log('📨 Unexpected message received:', data.toString());
});

ws.on('close', function close(code, reason) {
  console.log('🔌 WebSocket connection closed');
  console.log('   Code:', code);
  console.log('   Reason:', reason.toString());
});

ws.on('error', function error(err) {
  console.error('🚨 WebSocket error:', err);
});

// Close after 3 seconds to test stability
setTimeout(() => {
  console.log('⏰ Closing connection after 3 seconds...');
  ws.close();
}, 3000);
