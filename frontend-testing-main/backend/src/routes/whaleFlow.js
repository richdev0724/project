const express = require('express');
const expressWs = require('express-ws');
const { initializeWhaleFlowWebSocket } = require('../services/whaleFlowService');

// Create router with WebSocket support
const router = express.Router();
expressWs(router);

// Initialize the WebSocket server
const whaleFlowWS = initializeWhaleFlowWebSocket();

// WebSocket endpoint for whale flow data
router.ws('/stream', (ws, req) => {
  // Add the new client
  whaleFlowWS.addClient(ws);

  // Handle client disconnection
  ws.on('close', () => {
    whaleFlowWS.removeClient(ws);
  });

  // Handle client errors
  ws.on('error', (error) => {
    console.error('WebSocket error:', error);
    whaleFlowWS.removeClient(ws);
  });
});

// REST endpoint for latest whale flow data
router.get('/latest', async (req, res) => {
  try {
    // Return empty array for now, as we're using WebSocket for real-time data
    res.json({
      status: 'done',
      data: []
    });
  } catch (error) {
    console.error('Error fetching latest whale flow data:', error);
    res.status(500).json({
      status: 'error',
      message: 'Failed to fetch whale flow data'
    });
  }
});

module.exports = router; 