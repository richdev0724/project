const express = require('express');
const router = express.Router();
const { getNetPremiumsData } = require('../services/netPremiumsService');

/**
 * GET /api/net-premiums/:ticker
 * Get net premiums data for a ticker over a specified timeframe
 * Query parameters:
 * - days: Number of days to look back (default: 5)
 */
router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 5 } = req.query;
    
    if (!ticker) {
      return res.status(400).json({ 
        success: false, 
        error: 'Ticker parameter is required' 
      });
    }

    const data = await getNetPremiumsData(ticker, parseInt(days));
    
    res.json({
      success: true,
      ticker: data.ticker,
      timeframe: data.timeframe,
      data: data.data, // This is the array
      totalCalls: data.totalCalls,
      totalPuts: data.totalPuts,
      netPremium: data.netPremium
    });

  } catch (error) {
    console.error('Error in net premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch net premiums data',
      message: error.message
    });
  }
});

module.exports = router;
