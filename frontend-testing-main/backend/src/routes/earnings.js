const express = require('express');
const router = express.Router();
const earningsService = require('../services/earningsService');

// DEBUG endpoint to see raw API response
router.get('/debug/raw', async (req, res) => {
  try {
    const date = req.query.date || new Date().toISOString().split('T')[0];
    const premarket = await earningsService.fetchPremarketData(date);
    const afterhours = await earningsService.fetchAfterhoursData(date);
    
    res.json({
      date,
      premarketCount: premarket?.length || 0,
      afterhoursCount: afterhours?.length || 0,
      premarketSample: premarket?.[0] || null,
      afterhoursSample: afterhours?.[0] || null
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// GET /api/earnings/ticker/:symbol - Get earnings history for a specific ticker
router.get('/ticker/:symbol', async (req, res) => {
  try {
    const { symbol } = req.params;
    
    console.log(`🎯 Ticker earnings route hit for symbol: ${symbol}`);
    
    // Validate symbol (basic validation)
    if (!symbol || symbol.length > 10) {
      return res.status(400).json({
        error: 'Invalid ticker symbol'
      });
    }

    const earnings = await earningsService.getTickerEarnings(symbol.toUpperCase());
    
    // Set cache control headers to prevent browser caching
    res.set({
      'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });
    
    res.json(earnings);
  } catch (error) {
    console.error('Error in ticker earnings route:', error);
    res.status(500).json({
      error: 'Failed to fetch ticker earnings data',
      message: error.message
    });
  }
});

// GET /api/earnings/:date
router.get('/:date', async (req, res) => {
  try {
    const { date } = req.params;
    
    console.log(`🎯 Earnings route hit for date: ${date}`);
    
    // Validate date format (YYYY-MM-DD)
    const dateRegex = /^\d{4}-\d{2}-\d{2}$/;
    if (!dateRegex.test(date)) {
      return res.status(400).json({
        error: 'Invalid date format. Expected YYYY-MM-DD'
      });
    }

    const earnings = await earningsService.getEarningsByDate(date);
    
    // Set cache control headers to prevent browser caching
    // Our backend has its own 5-minute cache, so we want fresh requests from the frontend
    res.set({
      'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
      'Pragma': 'no-cache',
      'Expires': '0'
    });
    
    res.json(earnings);
  } catch (error) {
    console.error('Error in earnings route:', error);
    res.status(500).json({
      error: 'Failed to fetch earnings data',
      message: error.message
    });
  }
});

module.exports = router;

