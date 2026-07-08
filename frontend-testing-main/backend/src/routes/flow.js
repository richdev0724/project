const express = require('express');
const { getOptionsFlowData, testConnection, isMarketOpen, getLastTradingDay } = require('../services/optionsFlowService');

const router = express.Router();

/**
 * GET /api/flow
 * Fetch paginated options flow data from ClickHouse
 */
router.get('/', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const pageSize = parseInt(req.query.pageSize) || 100;
    const tradingDate = req.query.date; // Optional date parameter (YYYY-MM-DD format)
    
    // Extract filter parameters from query string
    const filters = {};
    
    // Extract and parse filter parameters
    if (req.query.tickers) filters.tickers = req.query.tickers;
    if (req.query.ignoreTickers) filters.ignoreTickers = req.query.ignoreTickers;
    if (req.query.marketCap) filters.marketCap = req.query.marketCap.split(',');
    if (req.query.expiration) filters.expiration = req.query.expiration.split(',');
    if (req.query.dteMin !== undefined) filters.dteMin = parseInt(req.query.dteMin);
    if (req.query.dteMax !== undefined) filters.dteMax = parseInt(req.query.dteMax);
    if (req.query.minimumPremium !== undefined) filters.minimumPremium = parseFloat(req.query.minimumPremium);
    if (req.query.maximumPremium !== undefined) filters.maximumPremium = parseFloat(req.query.maximumPremium);
    if (req.query.minimumSize !== undefined) filters.minimumSize = parseInt(req.query.minimumSize);
    if (req.query.maximumSize !== undefined) filters.maximumSize = parseInt(req.query.maximumSize);
    if (req.query.minimumVolume !== undefined) filters.minimumVolume = parseInt(req.query.minimumVolume);
    if (req.query.maximumVolume !== undefined) filters.maximumVolume = parseInt(req.query.maximumVolume);
    
    // Boolean filters (calls, puts, sweeps, etc.)
    ['calls', 'puts', 'sweeps', 'floor', 'cross', 'bid', 'mid', 'ask', 'noSide', 'aboveAsk', 'belowBid',
     'singleLegOnly', 'volume>Oi', 'size>Oi', 'openingTrade', 'excludeDeepItm', 
     'otmOnly', 'itmOnly', 'multilegOnly', 'singlelegOnly', 'aggregatedTrades', 'hideExpired'].forEach(filter => {
      if (req.query[filter] !== undefined) {
        filters[filter] = req.query[filter] === 'true';
      }
    });
    
    // Validate pagination parameters
    if (page < 1 || pageSize < 1 || pageSize > 1000) {
      return res.status(400).json({ 
        error: 'Invalid pagination parameters. Page must be >= 1 and pageSize must be between 1 and 1000.' 
      });
    }
    
    // Validate date format if provided
    if (tradingDate && !/^\d{4}-\d{2}-\d{2}$/.test(tradingDate)) {
      return res.status(400).json({
        error: 'Invalid date format. Use YYYY-MM-DD format.'
      });
    }
    
    console.log(`Fetching flow data: page=${page}, pageSize=${pageSize}, date=${tradingDate || 'today'}`);
    console.log('Filters received:', filters);
    
    const data = await getOptionsFlowData(page, pageSize, tradingDate, filters);
    
    // Temporarily disable prefetching to reduce database load during optimization
    // if (page <= 2) { // Only prefetch for first few pages to avoid unnecessary load
    //   prefetchPages(page, pageSize, tradingDate, 1).catch(error => {
    //     console.log('Background prefetch failed:', error.message);
    //   });
    // }
    
    res.json({
      success: true,
      data: data
    });
    
  } catch (error) {
    console.error('Error in flow route:', error);
    res.status(500).json({ 
      error: 'Failed to fetch flow data',
      details: error.message 
    });
  }
});

// Prefetch endpoint removed for simplification

/**
 * GET /api/flow/market-status
 * Get current market status and suggested trading date
 */
router.get('/market-status', async (req, res) => {
  try {
    const marketInfo = await isMarketOpen();
    const suggestedDate = await getLastTradingDay();
    
    res.json({
      success: true,
      marketStatus: marketInfo,
      suggestedTradingDate: suggestedDate,
      message: marketInfo.isOpen ? 
        'Market is currently open' : 
        `Market is ${marketInfo.marketStatus}, using ${suggestedDate} as trading date`
    });
  } catch (error) {
    console.error('Error getting market status:', error);
    res.status(500).json({
      success: false,
      message: 'Error getting market status',
      details: error.message
    });
  }
});

/**
 * GET /api/flow/test
 * Test ClickHouse connection
 */
router.get('/test', async (req, res) => {
  try {
    const isConnected = await testConnection();
    
    if (isConnected) {
      res.json({ 
        success: true, 
        message: 'ClickHouse connection successful' 
      });
    } else {
      res.status(500).json({ 
        success: false, 
        message: 'ClickHouse connection failed' 
      });
    }
  } catch (error) {
    console.error('Error testing connection:', error);
    res.status(500).json({ 
      success: false, 
      message: 'Error testing connection',
      details: error.message 
    });
  }
});

module.exports = router;
