const express = require('express');
const { getDailyPremiums, storeDailyPremiums, getHistoricalPremiums } = require('../services/dailyPremiumsService');
const { getComprehensiveOptionsData } = require('../services/comprehensiveOptionsService');
const { getCompleteDailyPremiums } = require('../services/completeOptionsTradesService');
const { getSmartDailyPremiums } = require('../services/smartOptionsTradesService');
const { getTotalOptionsPremiums, storeDailyPremiums: storeCorrectDailyPremiums } = require('../services/correctOptionsPremiumsService');
const { getTotalOptionsPremiumsFromAggregates } = require('../services/aggregatesOptionsPremiumsService');
const polygonOptionsTradesService = require('../services/polygonOptionsTradesService');

const router = express.Router();

/**
 * GET /api/daily-premiums/:ticker
 * Get daily premiums for a specific date
 * Query parameters:
 * - date: Date in YYYY-MM-DD format (default: today)
 */
router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    const data = await getDailyPremiums(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch daily premiums data',
      message: error.message
    });
  }
});

/**
 * POST /api/daily-premiums/:ticker/calculate-and-store
 * Calculate and store daily premiums for a specific date
 * Body: { date: 'YYYY-MM-DD' }
 */
router.post('/:ticker/calculate-and-store', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.body;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    
    // Calculate daily premiums
    const premiumsData = await getDailyPremiums(ticker, targetDate);
    
    // Store in ClickHouse
    const stored = await storeDailyPremiums(premiumsData);

    res.json({
      success: true,
      calculated: premiumsData,
      stored: stored,
      message: stored ? 'Data calculated and stored successfully' : 'Data calculated but failed to store'
    });

  } catch (error) {
    console.error('Error in calculate and store endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to calculate and store daily premiums',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/historical
 * Get historical premiums data from ClickHouse
 * Query parameters:
 * - days: Number of days to retrieve (default: 5)
 */
router.get('/:ticker/historical', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 5 } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await getHistoricalPremiums(ticker, parseInt(days));

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
    console.error('Error in historical premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch historical premiums data',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/comprehensive
 * Get comprehensive options data using Options Chain Snapshot API with pagination
 */
router.get('/:ticker/comprehensive', async (req, res) => {
  try {
    const { ticker } = req.params;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await getComprehensiveOptionsData(ticker);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in comprehensive options endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch comprehensive options data',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/complete
 * Get complete daily premiums by fetching all contracts and their trades
 * Query parameters:
 * - date: Date in YYYY-MM-DD format (default: today)
 */
router.get('/:ticker/complete', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    const data = await getCompleteDailyPremiums(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in complete daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch complete daily premiums data',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/smart
 * Get smart daily premiums by finding active contracts first
 * Query parameters:
 * - date: Date in YYYY-MM-DD format (default: today)
 */
router.get('/:ticker/smart', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    const data = await getSmartDailyPremiums(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in smart daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch smart daily premiums data',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/correct
 * Get total options premiums using the correct approach (day.close * day.volume)
 * Query parameters:
 * - date: Date in YYYY-MM-DD format (default: today)
 */
router.get('/:ticker/correct', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    const data = await getTotalOptionsPremiums(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in correct daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch correct daily premiums data',
      message: error.message
    });
  }
});

/**
 * POST /api/daily-premiums/:ticker/calculate-and-store-correct
 * Calculate and store daily premiums using the correct approach
 * Body: { date: 'YYYY-MM-DD' }
 */
router.post('/:ticker/calculate-and-store-correct', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.body;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    
    // Calculate daily premiums using correct approach
    const premiumsData = await getTotalOptionsPremiums(ticker, targetDate);
    
    // Store in ClickHouse
    const stored = await storeCorrectDailyPremiums(premiumsData);

    res.json({
      success: true,
      calculated: premiumsData,
      stored: stored,
      message: stored ? 'Data calculated and stored successfully' : 'Data calculated but failed to store'
    });

  } catch (error) {
    console.error('Error in calculate and store correct endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to calculate and store correct daily premiums',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/aggregates
 * Get total options premiums using aggregates API (VWAP * Volume)
 * Query parameters:
 * - date: Date in YYYY-MM-DD format (default: today)
 */
router.get('/:ticker/aggregates', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    const data = await getTotalOptionsPremiumsFromAggregates(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in aggregates daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch aggregates daily premiums data',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/polygon-client
 * Get total options premiums using Polygon JS client with proper bid/ask analysis
 * Query parameters:
 * - date: Date in YYYY-MM-DD format (default: today)
 */
router.get('/:ticker/polygon-client', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    // Default to today's date for end-of-day calculations (runs after market close)
    const getTodayDate = () => {
      const today = new Date();
      
      // If it's weekend, get the last trading day (Friday)
      if (today.getDay() === 0) { // Sunday
        today.setDate(today.getDate() - 2); // Go back to Friday
      } else if (today.getDay() === 6) { // Saturday
        today.setDate(today.getDate() - 1); // Go back to Friday
      }
      
      return today.toISOString().split('T')[0];
    };
    
    const targetDate = date || getTodayDate();
    const data = await polygonOptionsTradesService.calculateNetPremiums(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in polygon client daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch polygon client daily premiums data',
      message: error.message
    });
  }
});

router.get('/:ticker/polygon-aggregates', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const targetDate = date || new Date().toISOString().split('T')[0];
    const data = await polygonOptionsTradesService.calculateNetPremiumsFromAggregates(ticker, targetDate);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in polygon aggregates daily premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch polygon aggregates daily premiums data',
      message: error.message
    });
  }
});

/**
 * POST /api/daily-premiums/:ticker/calculate-and-store-polygon
 * Calculate and store daily premiums using Polygon JS client with proper bid/ask analysis
 * Body: { date: 'YYYY-MM-DD' } (optional, defaults to today)
 */
router.post('/:ticker/calculate-and-store-polygon', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.body;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    // Calculate and store daily premiums
    const result = await polygonOptionsTradesService.calculateAndStoreDailyPremiums(ticker, date);

    res.json({
      success: true,
      data: result,
      message: result.stored ? 'Data calculated and stored successfully in ClickHouse' : 'Data calculated but failed to store in ClickHouse'
    });

  } catch (error) {
    console.error('Error in calculate and store polygon endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to calculate and store polygon daily premiums',
      message: error.message
    });
  }
});

/**
 * GET /api/daily-premiums/:ticker/historical-polygon
 * Get historical premiums data from ClickHouse using the new schema
 * Query parameters:
 * - days: Number of days to retrieve (default: 5)
 */
router.get('/:ticker/historical-polygon', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 5 } = req.query;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    const data = await polygonOptionsTradesService.getHistoricalPremiums(ticker, parseInt(days));

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
    console.error('Error in historical polygon premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch historical polygon premiums data',
      message: error.message
    });
  }
});

module.exports = router;
