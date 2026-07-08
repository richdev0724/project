const express = require('express');
const router = express.Router();
const analystRatingsService = require('../services/analystRatingsService');

router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const ratings = await analystRatingsService.getAnalystRatings(ticker);
    res.json(ratings);
  } catch (error) {
    console.error('Error in analyst ratings route:', error);
    res.status(500).json({ error: 'Failed to fetch analyst ratings' });
  }
});

router.get('/:ticker/price-target', async (req, res) => {
  try {
    const { ticker } = req.params;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const priceTarget = await analystRatingsService.getPriceTarget(ticker);
    res.json(priceTarget);
  } catch (error) {
    console.error('Error in price target route:', error);
    res.status(500).json({ error: 'Failed to fetch price target' });
  }
});

router.get('/:ticker/upgrades-downgrades', async (req, res) => {
  try {
    const { ticker } = req.params;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const upgradesDowngrades = await analystRatingsService.getUpgradesDowngrades(ticker);
    res.json(upgradesDowngrades);
  } catch (error) {
    console.error('Error in upgrades/downgrades route:', error);
    res.status(500).json({ error: 'Failed to fetch upgrades/downgrades' });
  }
});

module.exports = router;

