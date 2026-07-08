const express = require('express');
const { getTopNetImpact } = require('../services/topNetImpactService');

const router = express.Router();

/**
 * GET /api/top-net-impact
 * Get top bullish and bearish net impact tickers from Unusual Whales API
 * Returns top 10 bullish (positive net premium) and top 10 bearish (negative net premium)
 */
router.get('/', async (req, res) => {
  try {
    console.log('📍 Top Net Impact endpoint called');
    const result = await getTopNetImpact();
    
    console.log('📤 Sending response:', {
      success: result.success,
      bullishCount: result.bullish?.length || 0,
      bearishCount: result.bearish?.length || 0
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
      data: {
        bullish: result.bullish,
        bearish: result.bearish
      },
      timestamp: result.timestamp
    });

  } catch (error) {
    console.error('❌ Error in top net impact endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch top net impact data',
      message: error.message
    });
  }
});

module.exports = router;

