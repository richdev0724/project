const express = require('express');
const { getLiveOptionsData, getLiveNetPremiums } = require('../services/liveOptionsService');
const { getLiveOptionsTrades, getLiveNetPremiumsFromTrades } = require('../services/liveOptionsTradesService');

const router = express.Router();

/**
 * GET /api/live-options/:ticker
 * Get live options data for today from Polygon
 */
router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await getLiveOptionsData(ticker);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in live options endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch live options data',
      message: error.message
    });
  }
});

/**
 * GET /api/live-options/:ticker/net-premiums
 * Get live net premiums for today from Polygon (snapshot method)
 */
router.get('/:ticker/net-premiums', async (req, res) => {
  try {
    const { ticker } = req.params;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await getLiveNetPremiums(ticker);

    res.json({
      success: true,
      ticker: data.ticker,
      timeframe: data.timeframe,
      data: data.data,
      totalCalls: data.totalCalls,
      totalPuts: data.totalPuts,
      netPremium: data.netPremium
    });

  } catch (error) {
    console.error('Error in live net premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch live net premiums data',
      message: error.message
    });
  }
});

/**
 * GET /api/live-options/:ticker/trades
 * Get live options trades for today from Polygon
 */
router.get('/:ticker/trades', async (req, res) => {
  try {
    const { ticker } = req.params;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await getLiveOptionsTrades(ticker);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in live options trades endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch live options trades data',
      message: error.message
    });
  }
});

/**
 * GET /api/live-options/:ticker/net-premiums-trades
 * Get live net premiums for today from actual trades
 */
router.get('/:ticker/net-premiums-trades', async (req, res) => {
  try {
    const { ticker } = req.params;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await getLiveNetPremiumsFromTrades(ticker);

    res.json({
      success: true,
      ticker: data.ticker,
      timeframe: data.timeframe,
      data: data.data,
      totalCalls: data.totalCalls,
      totalPuts: data.totalPuts,
      netPremium: data.netPremium
    });

  } catch (error) {
    console.error('Error in live net premiums trades endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch live net premiums from trades data',
      message: error.message
    });
  }
});

module.exports = router;
