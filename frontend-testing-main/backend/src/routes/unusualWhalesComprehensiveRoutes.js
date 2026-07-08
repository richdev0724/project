const express = require('express');
const { getComprehensivePremiumData, getLastNTradingSessionsComprehensive } = require('../services/unusualWhalesComprehensiveService');
const comprehensivePremiumsStorageService = require('../services/comprehensivePremiumsStorageService');

const router = express.Router();

/**
 * GET /api/unusual-whales/comprehensive-premiums/:ticker
 * Get comprehensive premium data for a specific ticker and date
 */
router.get('/comprehensive-premiums/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;
    
    if (!date) {
      return res.status(400).json({
        success: false,
        error: 'Date parameter is required (YYYY-MM-DD format)'
      });
    }
    
    console.log(`📊 API Request: Comprehensive premiums for ${ticker} on ${date}`);
    
    const data = await getComprehensivePremiumData(ticker.toUpperCase(), date);
    
    if (data.success) {
      res.json({
        success: true,
        data: {
          ticker: data.ticker,
          date: data.date,
          totalCallPremium: data.totalCallPremium,
          totalPutPremium: data.totalPutPremium,
          netCallPremium: data.netCallPremium,
          netPutPremium: data.netPutPremium,
          calculatedNetPremium: data.calculatedNetPremium,
          netPremiumFromTopImpact: data.netPremiumFromTopImpact,
          totalTicks: data.totalTicks
        }
      });
    } else {
      res.status(404).json({
        success: false,
        error: data.error || 'No data available for the specified date'
      });
    }
    
  } catch (error) {
    console.error('❌ Error in comprehensive-premiums endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

/**
 * GET /api/unusual-whales/comprehensive-premiums/:ticker/multiple-days
 * Get comprehensive premium data for the last N trading sessions
 */
router.get('/comprehensive-premiums/:ticker/multiple-days', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 15 } = req.query;
    
    const daysNum = parseInt(days);
    if (isNaN(daysNum) || daysNum < 1 || daysNum > 30) {
      return res.status(400).json({
        success: false,
        error: 'Days parameter must be a number between 1 and 30'
      });
    }
    
    console.log(`📊 API Request: Comprehensive premiums for ${ticker} - Last ${daysNum} trading sessions`);
    
    const results = await getLastNTradingSessionsComprehensive(ticker.toUpperCase(), daysNum);
    
    if (results.length > 0) {
      res.json({
        success: true,
        data: results.map(result => ({
          ticker: result.ticker,
          date: result.date,
          totalCallPremium: result.totalCallPremium,
          totalPutPremium: result.totalPutPremium,
          netCallPremium: result.netCallPremium,
          netPutPremium: result.netPutPremium,
          calculatedNetPremium: result.calculatedNetPremium,
          netPremiumFromTopImpact: result.netPremiumFromTopImpact,
          totalTicks: result.totalTicks,
          success: result.success
        }))
      });
    } else {
      res.status(404).json({
        success: false,
        error: 'No data available for the specified period'
      });
    }
    
  } catch (error) {
    console.error('❌ Error in comprehensive-premiums multiple-days endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

/**
 * POST /api/unusual-whales/comprehensive-premiums/:ticker/populate
 * Populate ClickHouse with comprehensive premium data for the last N trading sessions
 */
router.post('/:ticker/populate', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 15 } = req.body;
    
    console.log(`📊 API Request: Populate comprehensive premiums for ${ticker} - Last ${days} trading sessions`);
    
    // Test ClickHouse connection first
    const isConnected = await comprehensivePremiumsStorageService.testConnection();
    if (!isConnected) {
      return res.status(503).json({
        success: false,
        error: 'ClickHouse is not available. Please ensure ClickHouse is running.'
      });
    }
    
    // Create table if it doesn't exist
    await comprehensivePremiumsStorageService.createTable();
    
    // Clear existing data for this ticker
    await comprehensivePremiumsStorageService.clearTickerData(ticker);
    
    // Fetch comprehensive premium data
    const comprehensiveData = await getLastNTradingSessionsComprehensive(ticker.toUpperCase(), days);
    
    if (comprehensiveData.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'No data retrieved from API'
      });
    }
    
    // Filter successful results
    const successfulData = comprehensiveData.filter(data => data.success);
    
    if (successfulData.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'No successful data to store'
      });
    }
    
    // Store data in ClickHouse
    await comprehensivePremiumsStorageService.storeMultipleDaysData(successfulData);
    
    // Get storage statistics
    const stats = await comprehensivePremiumsStorageService.getStorageStatistics();
    
    res.json({
      success: true,
      message: `Successfully populated ${successfulData.length} records for ${ticker}`,
      data: {
        ticker: ticker.toUpperCase(),
        recordsStored: successfulData.length,
        totalRecords: stats.totalRecords,
        stats
      }
    });
    
  } catch (error) {
    console.error('❌ Error in populate endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error',
      details: error.message
    });
  }
});

/**
 * GET /api/unusual-whales/comprehensive-premiums/:ticker/storage
 * Get comprehensive premium data from ClickHouse storage
 */
router.get('/:ticker/storage', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 15 } = req.query;
    
    console.log(`📊 API Request: Get comprehensive premiums from storage for ${ticker} - Last ${days} days`);
    
    // Test ClickHouse connection first
    const isConnected = await comprehensivePremiumsStorageService.testConnection();
    if (!isConnected) {
      return res.status(503).json({
        success: false,
        error: 'ClickHouse is not available. Please ensure ClickHouse is running.'
      });
    }
    
    const data = await comprehensivePremiumsStorageService.getComprehensivePremiumData(ticker.toUpperCase(), parseInt(days));
    
    if (data.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'No data found in storage for this ticker'
      });
    }
    
    res.json({
      success: true,
      data: data,
      count: data.length
    });
    
  } catch (error) {
    console.error('❌ Error in storage endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error',
      details: error.message
    });
  }
});

/**
 * GET /api/unusual-whales/comprehensive-premiums/storage/stats
 * Get storage statistics
 */
router.get('/storage/stats', async (req, res) => {
  try {
    console.log('📊 API Request: Get comprehensive premiums storage statistics');
    
    // Test ClickHouse connection first
    const isConnected = await comprehensivePremiumsStorageService.testConnection();
    if (!isConnected) {
      return res.status(503).json({
        success: false,
        error: 'ClickHouse is not available. Please ensure ClickHouse is running.'
      });
    }
    
    const stats = await comprehensivePremiumsStorageService.getStorageStatistics();
    
    res.json({
      success: true,
      stats
    });
    
  } catch (error) {
    console.error('❌ Error in storage stats endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error',
      details: error.message
    });
  }
});

module.exports = router;
