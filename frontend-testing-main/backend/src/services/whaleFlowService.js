const { websocketClient } = require('@polygon.io/client-js');
const { format } = require('date-fns');
const { zonedTimeToUtc, utcToZonedTime } = require('date-fns-tz');
require('dotenv').config();

// Configuration
const POLYGON_API_KEY = process.env.POLYGON_API_KEY;
const MIN_PREMIUM = 100000; // Only show trades with premium > $100K
const DELAY_MS = 2000;     // 2 second delay between trades

let connectedClients = new Set();

// Function to format numbers to K, M, B
const formatNumber = (num) => {
  if (num >= 1e9) {
    return (num / 1e9).toFixed(2) + 'B';
  }
  if (num >= 1e6) {
    return (num / 1e6).toFixed(2) + 'M';
  }
  if (num >= 1e3) {
    return (num / 1e3).toFixed(1) + 'K';
  }
  return num.toFixed(2);
};

// Function to format timestamp to Eastern Time
const formatToEasternTime = (timestamp) => {
  const utcDate = new Date(timestamp);
  const estDate = utcToZonedTime(utcDate, 'America/New_York');
  return format(estDate, 'yyyy-MM-dd HH:mm:ss');
};

// Function to create a delay
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

// Function to broadcast data to all connected clients
const broadcastData = (data) => {
  connectedClients.forEach(client => {
    if (client.readyState === 1) { // Check if client is still connected
      client.send(JSON.stringify({
        type: 'whaleFlow',
        data
      }));
    }
  });
};

const initializeWhaleFlowWebSocket = () => {
  // Create a websocket client using the polygon client-js library
  const ws = websocketClient(POLYGON_API_KEY, 'wss://delayed-business.polygon.io').stocks();

  // Register error handler
  ws.onerror = (err) => console.error('Failed to connect to Polygon', err);

  // Register close handler
  ws.onclose = (code, reason) => console.log('Polygon connection closed', code, reason);

  // Register message handler
  ws.onmessage = async (msg) => {
    try {
      const parsedMessage = JSON.parse(msg.data);

      // Handle authentication success
      if (parsedMessage[0].ev === 'status' && parsedMessage[0].status === 'auth_success') {
        console.log('Successfully authenticated with Polygon, subscribing to trades');
        ws.send(JSON.stringify({ "action": "subscribe", "params": "T.*" }));
        return;
      }

      // Process trade messages
      if (parsedMessage[0].ev === 'T') {
        const trade = parsedMessage[0];
        const premium = trade.p * trade.s; // price * size

        // Only process trades above minimum premium threshold
        if (premium >= MIN_PREMIUM) {
          const tradeData = {
            id: trade.i,
            symbol: trade.sym,
            price: trade.p.toFixed(2),
            size: trade.s.toLocaleString(),
            total_premium: formatNumber(premium),
            market_session: getMarketSession(trade.t),
            trade_date: formatToEasternTime(trade.t)
          };

          // Broadcast the trade data to all connected clients
          broadcastData(tradeData);

          // Add delay before processing next message
          await delay(DELAY_MS);
        }
      }
    } catch (error) {
      console.error('Error processing message:', error);
    }
  };

  return {
    addClient: (client) => {
      connectedClients.add(client);
      console.log('Client connected, total clients:', connectedClients.size);
    },
    removeClient: (client) => {
      connectedClients.delete(client);
      console.log('Client disconnected, total clients:', connectedClients.size);
    }
  };
};

// Function to determine market session based on timestamp
function getMarketSession(timestamp) {
  const date = new Date(timestamp);
  const estDate = utcToZonedTime(date, 'America/New_York');
  const hours = estDate.getHours();
  const minutes = estDate.getMinutes();
  const timeInMinutes = hours * 60 + minutes;

  if (timeInMinutes >= 570 && timeInMinutes < 960) { // 9:30 AM - 4:00 PM
    return 'During Market';
  } else if (timeInMinutes >= 240 && timeInMinutes < 570) { // 4:00 AM - 9:30 AM
    return 'Pre Market';
  } else {
    return 'After Market';
  }
}

module.exports = {
  initializeWhaleFlowWebSocket
}; 