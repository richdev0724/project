const express = require('express')
const router = express.Router()
const { 
  initializeTimeframeTable,
  getTimeframeData,
  fetchAndStoreTimeframeData,
  fetchAndStoreAllTimeframes,
  getDateRange
} = require('../services/timeframeDataService')

/**
 * Initialize the timeframe data table
 */
router.post('/initialize', async (req, res) => {
  try {
    await initializeTimeframeTable()
    res.json({
      success: true,
      message: 'Timeframe data table initialized successfully'
    })
  } catch (error) {
    console.error('Error initializing timeframe table:', error)
    res.status(500).json({
      success: false,
      error: 'Failed to initialize timeframe table',
      message: error.message
    })
  }
})

/**
 * Get timeframe data for a specific ticker and timeframe
 */
router.get('/data/:ticker/:timeframe', async (req, res) => {
  try {
    const { ticker, timeframe } = req.params
    const { limit = 1000 } = req.query
    
    console.log(`📊 API Request: ${ticker} ${timeframe} (limit: ${limit})`)
    
    const data = await getTimeframeData(ticker, timeframe, parseInt(limit))
    
    res.json({
      success: true,
      ticker: ticker.toUpperCase(),
      timeframe,
      data,
      count: data.length
    })
    
  } catch (error) {
    console.error(`Error getting timeframe data for ${req.params.ticker} ${req.params.timeframe}:`, error)
    res.status(500).json({
      success: false,
      error: 'Failed to get timeframe data',
      message: error.message
    })
  }
})

/**
 * Fetch and store data for a specific ticker and timeframe
 */
router.post('/fetch/:ticker/:timeframe', async (req, res) => {
  try {
    const { ticker, timeframe } = req.params
    
    console.log(`🚀 API Request: Fetch ${ticker} ${timeframe}`)
    
    await fetchAndStoreTimeframeData(ticker, timeframe)
    
    res.json({
      success: true,
      message: `Successfully fetched and stored ${ticker} ${timeframe} data`
    })
    
  } catch (error) {
    console.error(`Error fetching timeframe data for ${req.params.ticker} ${req.params.timeframe}:`, error)
    res.status(500).json({
      success: false,
      error: 'Failed to fetch timeframe data',
      message: error.message
    })
  }
})

/**
 * Fetch and store data for all timeframes for a ticker
 */
router.post('/fetch-all/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params
    
    console.log(`🎯 API Request: Fetch all timeframes for ${ticker}`)
    
    // Run this asynchronously to avoid timeout
    fetchAndStoreAllTimeframes(ticker).catch(error => {
      console.error(`Background fetch error for ${ticker}:`, error)
    })
    
    res.json({
      success: true,
      message: `Started fetching all timeframes for ${ticker}. This will run in the background.`
    })
    
  } catch (error) {
    console.error(`Error starting fetch for ${req.params.ticker}:`, error)
    res.status(500).json({
      success: false,
      error: 'Failed to start fetch process',
      message: error.message
    })
  }
})

/**
 * Get available timeframes
 */
router.get('/timeframes', (req, res) => {
  const timeframes = [
    { id: '1D', label: '1 Day', description: 'Current day, market hours only' },
    { id: '5D', label: '5 Days', description: '5 days including current day, market hours only' },
    { id: '1M', label: '1 Month', description: '1 month of daily candles' },
    { id: '3M', label: '3 Months', description: '3 months of daily candles' },
    { id: '6M', label: '6 Months', description: '6 months of daily candles' },
    { id: 'YTD', label: 'Year to Date', description: 'Year-to-date daily candles' },
    { id: '1Y', label: '1 Year', description: '1 year of daily candles' },
    { id: '5Y', label: '5 Years', description: '5 years of daily candles' },
    { id: 'All', label: 'All Time', description: 'All available historical data' }
  ]
  
  res.json({
    success: true,
    timeframes
  })
})

/**
 * Get date range for a specific timeframe
 */
router.get('/date-range/:timeframe', (req, res) => {
  try {
    const { timeframe } = req.params
    const dateRange = getDateRange(timeframe)
    
    res.json({
      success: true,
      timeframe,
      dateRange
    })
    
  } catch (error) {
    console.error(`Error getting date range for ${req.params.timeframe}:`, error)
    res.status(400).json({
      success: false,
      error: 'Invalid timeframe',
      message: error.message
    })
  }
})

module.exports = router
