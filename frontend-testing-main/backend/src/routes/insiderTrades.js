const express = require('express');
const router = express.Router();
const insiderTradesService = require('../services/insiderTradesService');

router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const limit = parseInt(req.query.limit) || 50;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const trades = await insiderTradesService.getInsiderTrades(ticker, limit);
    res.json(trades);
  } catch (error) {
    console.error('Error in insider trades route:', error);
    res.status(500).json({ error: 'Failed to fetch insider trades' });
  }
});

module.exports = router;

