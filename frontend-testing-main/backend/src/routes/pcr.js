const express = require('express');
const router = express.Router();
const pcrService = require('../services/pcrService');

/**
 * GET /api/pcr/:ticker
 * Get PCR value for a specific ticker
 * Query parameters:
 * - date: Date string (YYYY-MM-DD)
 * - method: 'volume' or 'oi' (default: 'volume')
 */
router.get('/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date, method = 'volume' } = req.query;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    if (!['volume', 'oi'].includes(method)) {
      return res.status(400).json({ error: 'Method must be "volume" or "oi"' });
    }

    const pcr = await pcrService.getTickerPCR(ticker, date, method);
    
    if (pcr === null) {
      return res.status(404).json({ 
        error: `No ${method} PCR data available for ${ticker}`,
        ticker: ticker.toUpperCase(),
        pcr: null,
        method: method
      });
    }

    res.json({
      ticker: ticker.toUpperCase(),
      pcr: pcr,
      method: method,
      date: date || new Date().toISOString().slice(0, 10),
      cached: pcrService.hasPCRForDate(ticker, date)
    });

  } catch (error) {
    console.error('Error in PCR route:', error);
    res.status(500).json({ error: 'Failed to fetch PCR data' });
  }
});

/**
 * GET /api/pcr/:ticker/both
 * Get both Volume and OI PCR values for a specific ticker
 */
router.get('/:ticker/both', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const result = await pcrService.getTickerPCRBoth(ticker, date);
    
    if (result.volume === null && result.oi === null) {
      return res.status(404).json({ 
        error: `No PCR data available for ${ticker}`,
        ticker: ticker.toUpperCase(),
        volume: null,
        oi: null
      });
    }

    res.json(result);

  } catch (error) {
    console.error('Error in PCR both route:', error);
    res.status(500).json({ error: 'Failed to fetch PCR data' });
  }
});

/**
 * POST /api/pcr/calculate
 * Calculate PCR for multiple tickers
 */
router.post('/calculate', async (req, res) => {
  try {
    const { tickers, date } = req.body;
    
    if (!tickers || !Array.isArray(tickers) || tickers.length === 0) {
      return res.status(400).json({ error: 'Tickers array is required' });
    }

    if (tickers.length > 50) {
      return res.status(400).json({ error: 'Maximum 50 tickers allowed per request' });
    }

    const results = await pcrService.calculateMultiplePCR(tickers, date);
    
    res.json({
      results: results,
      date: date || new Date().toISOString().slice(0, 10),
      count: Object.keys(results).length
    });

  } catch (error) {
    console.error('Error in PCR calculate route:', error);
    res.status(500).json({ error: 'Failed to calculate PCR data' });
  }
});

/**
 * GET /api/pcr/cache/stats
 * Get PCR cache statistics
 */
router.get('/cache/stats', async (req, res) => {
  try {
    const stats = pcrService.getCacheStats();
    res.json(stats);
  } catch (error) {
    console.error('Error getting cache stats:', error);
    res.status(500).json({ error: 'Failed to get cache statistics' });
  }
});

/**
 * DELETE /api/pcr/cache
 * Clear PCR cache
 */
router.delete('/cache', async (req, res) => {
  try {
    const { date } = req.query;
    pcrService.clearPCRCache(date);
    res.json({ 
      message: date ? `Cache cleared for ${date}` : 'All cache cleared',
      date: date || 'all'
    });
  } catch (error) {
    console.error('Error clearing cache:', error);
    res.status(500).json({ error: 'Failed to clear cache' });
  }
});

module.exports = router;
