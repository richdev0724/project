const express = require('express');
const router = express.Router();
const politicianTradesService = require('../services/politicianTradesService');

/**
 * GET /api/politician-trades
 * Get all politician trades with optional pagination
 * Query params: limit, offset
 */
router.get('/', async (req, res) => {
  try {
    const allTrades = await politicianTradesService.getAllTrades();
    
    // Parse pagination params
    const limit = parseInt(req.query.limit) || allTrades.length;
    const offset = parseInt(req.query.offset) || 0;
    
    // Apply pagination
    const paginatedTrades = allTrades.slice(offset, offset + limit);
    
    res.json({
      success: true,
      data: paginatedTrades,
      count: paginatedTrades.length,
      total: allTrades.length,
      hasMore: offset + limit < allTrades.length
    });
  } catch (error) {
    console.error('Error fetching politician trades:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch politician trades'
    });
  }
});

/**
 * GET /api/politician-trades/statistics
 * Get statistics about the trades data
 */
router.get('/statistics', async (req, res) => {
  try {
    const stats = await politicianTradesService.getStatistics();
    res.json({
      success: true,
      data: stats
    });
  } catch (error) {
    console.error('Error fetching statistics:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch statistics'
    });
  }
});

/**
 * GET /api/politician-trades/:politician
 * Get trades for a specific politician
 */
router.get('/:politician', async (req, res) => {
  try {
    const { politician } = req.params;
    const trades = await politicianTradesService.getPoliticianTrades(politician);
    res.json({
      success: true,
      data: trades,
      count: trades.length
    });
  } catch (error) {
    console.error(`Error fetching trades for ${req.params.politician}:`, error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch politician trades'
    });
  }
});

/**
 * GET /api/politician-trades/:politician/portfolio
 * Get portfolio data for a specific politician
 */
router.get('/:politician/portfolio', async (req, res) => {
  try {
    const { politician } = req.params;
    const portfolio = await politicianTradesService.getPoliticianPortfolio(politician);
    res.json({
      success: true,
      data: portfolio
    });
  } catch (error) {
    console.error(`Error fetching portfolio for ${req.params.politician}:`, error);
    res.status(500).json({
      success: false,
      error: 'Failed to fetch politician portfolio'
    });
  }
});

/**
 * POST /api/politician-trades/clear-cache
 * Clear the trades cache and reload from file
 */
router.post('/clear-cache', async (req, res) => {
  try {
    politicianTradesService.clearCache();
    res.json({
      success: true,
      message: 'Cache cleared and data reloaded successfully'
    });
  } catch (error) {
    console.error('Error clearing cache:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to clear cache'
    });
  }
});

/**
 * POST /api/politician-trades/reload
 * Reload data from file
 */
router.post('/reload', async (req, res) => {
  try {
    const result = politicianTradesService.reloadData();
    res.json({
      success: true,
      message: 'Data reloaded successfully',
      ...result
    });
  } catch (error) {
    console.error('Error reloading data:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to reload data'
    });
  }
});

module.exports = router;

