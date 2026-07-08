const express = require('express');
const router = express.Router();
const { getDailyNetPremiums, getMultipleDaysNetPremiums } = require('../services/unusualWhalesNetPremiumsService');

/**
 * GET /api/unusual-whales/net-premiums/:ticker
 * Get daily net premiums for a specific ticker using Unusual Whales API
 * Query params:
 *   - date: YYYY-MM-DD format (optional, defaults to today)
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

    console.log(`📊 Fetching UW daily net premiums for ${ticker}${date ? ` on ${date}` : ' (today)'}`);

    const data = await getDailyNetPremiums(ticker, date);

    res.json({
      success: true,
      data: data
    });

  } catch (error) {
    console.error('Error in UW daily net premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch daily net premiums from Unusual Whales',
      message: error.message
    });
  }
});

/**
 * GET /api/unusual-whales/net-premiums/:ticker/multiple-days
 * Get multiple days of net premiums data
 * Query params:
 *   - days: Number of days to fetch (optional, defaults to 5)
 */
router.get('/:ticker/multiple-days', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days } = req.query;
    const numDays = parseInt(days) || 5;

    if (!ticker) {
      return res.status(400).json({
        success: false,
        error: 'Ticker parameter is required'
      });
    }

    if (numDays < 1 || numDays > 30) {
      return res.status(400).json({
        success: false,
        error: 'Days parameter must be between 1 and 30'
      });
    }

    console.log(`📊 Fetching UW net premiums for ${ticker} over last ${numDays} days`);

    const data = await getMultipleDaysNetPremiums(ticker, numDays);

    res.json({
      success: true,
      data: data,
      days: numDays
    });

  } catch (error) {
    console.error('Error in UW multiple days net premiums endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch multiple days net premiums from Unusual Whales',
      message: error.message
    });
  }
});

module.exports = router;
