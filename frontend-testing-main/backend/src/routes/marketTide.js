const express = require('express');
const { getMarketTide } = require('../services/marketTideService');

const router = express.Router();

/**
 * GET /api/market-tide
 * Get market tide time series data (net call premium, net put premium, net volume)
 * Query parameters:
 * - date: Optional date in YYYY-MM-DD format
 * - endDate: Optional end date in YYYY-MM-DD format
 */
router.get('/', async (req, res) => {
  try {
    const { date, endDate } = req.query;
    console.log('🌊 Market Tide endpoint called', { date, endDate });
    const result = await getMarketTide(date, endDate);
    
    console.log('📤 Sending market tide response:', {
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
    console.error('❌ Error in market tide endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch market tide data',
      message: error.message
    });
  }
});

module.exports = router;

