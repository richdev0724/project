const express = require('express');
const router = express.Router();
const { 
  getWhaleTradesData, 
  getWhaleTradesStats, 
  getTopWhaleTrades,
  testStocksConnection 
} = require('../services/whaleTradesService');
const { whaleTradesStreamer } = require('../services/whaleTradesStreamService');

/**
 * GET /api/whale-trades
 * Get paginated whale trades data from stocks database
 * Query parameters:
 * - page: Page number (default: 1)
 * - pageSize: Records per page (default: 50)
 * - minPremium: Minimum premium threshold (default: 10000)
 * - maxPremium: Maximum premium threshold (optional)
 * - session: Market session filter (Pre-Market, Intraday, After-Hours) (optional)
 * - ticker: Ticker symbol filter (optional)
 */
router.get('/', async (req, res) => {
  try {
    const {
      page = 1,
      pageSize = 50,
      minPremium = 10000,
      maxPremium,
      minSize,
      maxSize,
      minPrice,
      maxPrice,
      session,
      ticker,
      sortBy = 'timestamp'
    } = req.query;

    const options = {
      page: parseInt(page),
      pageSize: Math.min(parseInt(pageSize), 100), // Limit to 100 records max
      minPremium: parseFloat(minPremium),
      maxPremium: maxPremium ? parseFloat(maxPremium) : null,
      minSize: minSize ? parseInt(minSize) : null,
      maxSize: maxSize ? parseInt(maxSize) : null,
      minPrice: minPrice ? parseFloat(minPrice) : null,
      maxPrice: maxPrice ? parseFloat(maxPrice) : null,
      session: session || null,
      ticker: ticker || null,
      sortBy: sortBy || 'timestamp'
    };

    const result = await getWhaleTradesData(options);
    
    res.json({
      success: true,
      ...result
    });

  } catch (error) {
    console.error('Error in whale trades endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch whale trades data',
      message: error.message
    });
  }
});

/**
 * GET /api/whale-trades/stats
 * Get whale trades statistics
 */
router.get('/stats', async (req, res) => {
  try {
    const stats = await getWhaleTradesStats();
    
    res.json({
      success: true,
      data: stats
    });

  } catch (error) {
    console.error('Error in whale trades stats endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch whale trades statistics',
      message: error.message
    });
  }
});

/**
 * GET /api/whale-trades/top
 * Get top whale trades by premium
 * Query parameters:
 * - limit: Number of top trades to return (default: 10, max: 50)
 */
router.get('/top', async (req, res) => {
  try {
    const { limit = 10 } = req.query;
    const topTrades = await getTopWhaleTrades(Math.min(parseInt(limit), 50));
    
    res.json({
      success: true,
      data: topTrades
    });

  } catch (error) {
    console.error('Error in top whale trades endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch top whale trades',
      message: error.message
    });
  }
});

/**
 * GET /api/whale-trades/stream
 * Server-Sent Events stream for real-time whale trades
 */
router.get('/stream', (req, res) => {
  // Set headers for Server-Sent Events
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Cache-Control',
  });

  const connectionId = `whale-${Date.now()}-${Math.random()}`;
  console.log(`🔌 New whale trades stream connection: ${connectionId}`);

  // Add this connection to the streamer
  whaleTradesStreamer.addConnection(connectionId);

  // Send initial connection message
  res.write(`data: ${JSON.stringify({ type: 'connected', connectionId })}\n\n`);

  // Listen for new whale trades
  const handleNewTrade = (trade) => {
    res.write(`data: ${JSON.stringify({ type: 'whaleTrade', data: trade })}\n\n`);
  };

  const handleError = (error) => {
    res.write(`data: ${JSON.stringify({ type: 'error', message: error.message })}\n\n`);
  };

  const handleStreamEnded = () => {
    res.write(`data: ${JSON.stringify({ type: 'streamEnded' })}\n\n`);
  };

  // Register event listeners
  whaleTradesStreamer.on('newWhaleTrade', handleNewTrade);
  whaleTradesStreamer.on('error', handleError);
  whaleTradesStreamer.on('streamEnded', handleStreamEnded);

  // Handle client disconnect
  req.on('close', () => {
    console.log(`🔌 Whale trades stream disconnected: ${connectionId}`);
    
    // Remove event listeners
    whaleTradesStreamer.removeListener('newWhaleTrade', handleNewTrade);
    whaleTradesStreamer.removeListener('error', handleError);
    whaleTradesStreamer.removeListener('streamEnded', handleStreamEnded);
    
    // Remove connection from streamer
    whaleTradesStreamer.removeConnection(connectionId);
  });

  // Send periodic heartbeat
  const heartbeat = setInterval(() => {
    res.write(`data: ${JSON.stringify({ type: 'heartbeat', timestamp: new Date().toISOString() })}\n\n`);
  }, 30000); // Every 30 seconds

  req.on('close', () => {
    clearInterval(heartbeat);
  });
});

/**
 * GET /api/whale-trades/stream-status
 * Get streaming status
 */
router.get('/stream-status', (req, res) => {
  try {
    const status = whaleTradesStreamer.getStatus();
    res.json({
      success: true,
      ...status
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      error: 'Failed to get stream status',
      message: error.message
    });
  }
});

/**
 * GET /api/whale-trades/test
 * Test connection to stocks database
 */
router.get('/test', async (req, res) => {
  try {
    const isConnected = await testStocksConnection();
    
    res.json({
      success: true,
      connected: isConnected,
      message: isConnected ? 'Connection successful' : 'Connection failed'
    });

  } catch (error) {
    console.error('Error testing stocks connection:', error);
    res.status(500).json({
      success: false,
      connected: false,
      error: 'Connection test failed',
      message: error.message
    });
  }
});

module.exports = router;
