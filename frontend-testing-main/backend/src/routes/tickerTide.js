const express = require('express');
const { getTickerTide } = require('../services/tickerTideService');

const router = express.Router();

/**
 * GET /api/ticker-tide/:ticker
 * Get ticker-specific tide time series data (net call premium, net put premium, net volume)
 * Query parameters:
 * - date: Optional date in YYYY-MM-DD format
 * - endDate: Optional end date in YYYY-MM-DD format
 */
router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date, endDate } = req.query;
    
    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }
    
    console.log(`🌊 Ticker Tide endpoint called for ${ticker}`, { date, endDate });
    const result = await getTickerTide(ticker, date, endDate);
    
    console.log('📤 Sending ticker tide response:', {
      ticker,
      success: result.success,
      dataPoints: result.data?.length || 0
    });
    
    if (!result.success) {
      console.error('⚠️ Service returned unsuccessful result:', result.error);
      return res.status(result.statusCode || 500).json({
        success: false,
        error: result.error
      });
    }

    res.json({
      success: true,
      data: result.data,
      timestamp: result.timestamp
    });

  } catch (error) {
    console.error(`❌ Error in ticker tide endpoint for ${req.params.ticker}:`, error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch ticker tide data',
      message: error.message
    });
  }
});

module.exports = router;

