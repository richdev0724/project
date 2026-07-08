const express = require('express');
const router = express.Router();
const gammaExposureService = require('../services/gammaExposureService');

// Get expiration dates for a ticker
router.get('/:ticker/expirations', async (req, res) => {
  try {
    const { ticker } = req.params;
    const dates = await gammaExposureService.getExpirationDates(ticker);
    res.json({ success: true, data: dates });
  } catch (error) {
    console.error('Error fetching expiration dates:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// Get gamma exposure data for a ticker and expiration
router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { expiration } = req.query;
    const data = await gammaExposureService.getGammaExposure(ticker, expiration);
    res.json({ success: true, data });
  } catch (error) {
    console.error('Error fetching gamma exposure:', error);
    res.status(500).json({ success: false, error: error.message });
  }
});

module.exports = router; 