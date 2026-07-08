const { createClient } = require('@clickhouse/client')
const axios = require('axios')
require('dotenv').config()

const POLYGON_API_KEY = process.env.POLYGON_API_KEY

// Initialize ClickHouse client
const clickhouse = createClient({
  host: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
  username: process.env.CLICKHOUSE_USER || 'default',
  password: process.env.CLICKHOUSE_PASSWORD || '',
  database: process.env.CLICKHOUSE_DATABASE || 'options'
})

/**
 * ClickHouse schema for storing multiple timeframe data
 * This will store all historical price data for different timeframes
 */
const TIMEFRAME_SCHEMA = `
CREATE TABLE IF NOT EXISTS timeframe_data (
    ticker String,
    timeframe String,
    timestamp DateTime64(3),
    open Float64,
    high Float64,
    low Float64,
    close Float64,
    volume UInt64,
    vwap Float64,
    date Date MATERIALIZED toDate(timestamp),
    created_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY (ticker, timeframe, toYYYYMM(date))
ORDER BY (ticker, timeframe, timestamp)
SETTINGS index_granularity = 8192
`

/**
 * Initialize the timeframe data table
 */
async function initializeTimeframeTable() {
  try {
    await clickhouse.query({
      query: TIMEFRAME_SCHEMA,
      format: 'JSONEachRow'
    })
    console.log('✅ Timeframe data table initialized')
  } catch (error) {
    console.error('❌ Error initializing timeframe table:', error)
    throw error
  }
}

/**
 * Fetch historical data from Polygon for a specific timeframe
 */
async function fetchHistoricalData(ticker, timeframe, fromDate, toDate) {
  try {
    console.log(`📊 Fetching ${timeframe} data for ${ticker} from ${fromDate} to ${toDate}`)
    
    // Determine timespan and multiplier based on timeframe
    let timespan, multiplier
    switch (timeframe) {
      case '1D':
        timespan = 'minute'
        multiplier = 1
        break
      case '5D':
        timespan = 'minute'
        multiplier = 1
        break
      case '1M':
      case '3M':
      case '6M':
      case 'YTD':
      case '1Y':
      case '5Y':
      case 'All':
        timespan = 'day'
        multiplier = 1
        break
      default:
        throw new Error(`Unsupported timeframe: ${timeframe}`)
    }

    const url = `https://api.polygon.io/v2/aggs/ticker/${ticker}/range/${multiplier}/${timespan}/${fromDate}/${toDate}?adjusted=true&sort=asc&limit=50000&apiKey=${POLYGON_API_KEY}`
    
    console.log(`🌐 API URL: ${url}`)
    
    const response = await axios.get(url)
    
    if (response.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${response.data.status}`)
    }

    const results = response.data.results || []
    console.log(`📈 Fetched ${results.length} data points for ${ticker} ${timeframe}`)
    
    return results.map(item => ({
      ticker: ticker.toUpperCase(),
      timeframe,
      timestamp: new Date(item.t),
      open: item.o,
      high: item.h,
      low: item.l,
      close: item.c,
      volume: item.v,
      vwap: item.vw || item.c // Use VWAP if available, otherwise use close
    }))
    
  } catch (error) {
    console.error(`❌ Error fetching ${timeframe} data for ${ticker}:`, error.message)
    throw error
  }
}

/**
 * Store timeframe data in ClickHouse
 */
async function storeTimeframeData(data) {
  if (!data || data.length === 0) {
    console.log('⚠️ No data to store')
    return
  }

  try {
    console.log(`💾 Storing ${data.length} records in ClickHouse`)
    
    // Insert data in batches to avoid memory issues
    const batchSize = 1000
    for (let i = 0; i < data.length; i += batchSize) {
      const batch = data.slice(i, i + batchSize)
      
      const values = batch.map(item => 
        `('${item.ticker}', '${item.timeframe}', '${item.timestamp.toISOString()}', ${item.open}, ${item.high}, ${item.low}, ${item.close}, ${item.volume}, ${item.vwap})`
      ).join(',')
      
      const query = `
        INSERT INTO timeframe_data (ticker, timeframe, timestamp, open, high, low, close, volume, vwap)
        VALUES ${values}
      `
      
      await clickhouse.query({
        query,
        format: 'JSONEachRow'
      })
      
      console.log(`✅ Stored batch ${Math.floor(i/batchSize) + 1}/${Math.ceil(data.length/batchSize)}`)
    }
    
    console.log(`🎉 Successfully stored ${data.length} records`)
    
  } catch (error) {
    console.error('❌ Error storing timeframe data:', error)
    throw error
  }
}

/**
 * Get timeframe data from ClickHouse
 */
async function getTimeframeData(ticker, timeframe, limit = 1000) {
  try {
    console.log(`📊 Retrieving ${timeframe} data for ${ticker}`)
    
    const query = `
      SELECT 
        ticker,
        timeframe,
        timestamp,
        open,
        high,
        low,
        close,
        volume,
        vwap
      FROM timeframe_data
      WHERE ticker = '${ticker.toUpperCase()}'
        AND timeframe = '${timeframe}'
      ORDER BY timestamp DESC
      LIMIT ${limit}
    `
    
    const result = await clickhouse.query({
      query,
      format: 'JSONEachRow'
    })
    
    const data = await result.json()
    console.log(`📈 Retrieved ${data.length} records for ${ticker} ${timeframe}`)
    
    return data.reverse() // Return in chronological order
    
  } catch (error) {
    console.error(`❌ Error retrieving ${timeframe} data for ${ticker}:`, error)
    throw error
  }
}

/**
 * Calculate date ranges for different timeframes
 */
function getDateRange(timeframe) {
  const now = new Date()
  const today = now.toISOString().slice(0, 10)
  
  switch (timeframe) {
    case '1D':
      return { fromDate: today, toDate: today }
    
    case '5D':
      const fiveDaysAgo = new Date(now)
      fiveDaysAgo.setDate(fiveDaysAgo.getDate() - 5)
      return { fromDate: fiveDaysAgo.toISOString().slice(0, 10), toDate: today }
    
    case '1M':
      const oneMonthAgo = new Date(now)
      oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1)
      return { fromDate: oneMonthAgo.toISOString().slice(0, 10), toDate: today }
    
    case '3M':
      const threeMonthsAgo = new Date(now)
      threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3)
      return { fromDate: threeMonthsAgo.toISOString().slice(0, 10), toDate: today }
    
    case '6M':
      const sixMonthsAgo = new Date(now)
      sixMonthsAgo.setMonth(sixMonthsAgo.getMonth() - 6)
      return { fromDate: sixMonthsAgo.toISOString().slice(0, 10), toDate: today }
    
    case 'YTD':
      const yearStart = new Date(now.getFullYear(), 0, 1)
      return { fromDate: yearStart.toISOString().slice(0, 10), toDate: today }
    
    case '1Y':
      const oneYearAgo = new Date(now)
      oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1)
      return { fromDate: oneYearAgo.toISOString().slice(0, 10), toDate: today }
    
    case '5Y':
      const fiveYearsAgo = new Date(now)
      fiveYearsAgo.setFullYear(fiveYearsAgo.getFullYear() - 5)
      return { fromDate: fiveYearsAgo.toISOString().slice(0, 10), toDate: today }
    
    case 'All':
      // Go back 20 years for "All" data
      const twentyYearsAgo = new Date(now)
      twentyYearsAgo.setFullYear(twentyYearsAgo.getFullYear() - 20)
      return { fromDate: twentyYearsAgo.toISOString().slice(0, 10), toDate: today }
    
    default:
      throw new Error(`Unsupported timeframe: ${timeframe}`)
  }
}

/**
 * Fetch and store data for a specific ticker and timeframe
 */
async function fetchAndStoreTimeframeData(ticker, timeframe) {
  try {
    console.log(`🚀 Starting data fetch for ${ticker} ${timeframe}`)
    
    const { fromDate, toDate } = getDateRange(timeframe)
    console.log(`📅 Date range: ${fromDate} to ${toDate}`)
    
    // Fetch data from Polygon
    const data = await fetchHistoricalData(ticker, timeframe, fromDate, toDate)
    
    if (data.length === 0) {
      console.log(`⚠️ No data found for ${ticker} ${timeframe}`)
      return
    }
    
    // Store in ClickHouse
    await storeTimeframeData(data)
    
    console.log(`✅ Successfully processed ${ticker} ${timeframe}`)
    
  } catch (error) {
    console.error(`❌ Error processing ${ticker} ${timeframe}:`, error)
    throw error
  }
}

/**
 * Fetch and store data for all timeframes for a ticker
 */
async function fetchAndStoreAllTimeframes(ticker) {
  const timeframes = ['1D', '5D', '1M', '3M', '6M', 'YTD', '1Y', '5Y', 'All']
  
  console.log(`🎯 Processing all timeframes for ${ticker}`)
  
  for (const timeframe of timeframes) {
    try {
      await fetchAndStoreTimeframeData(ticker, timeframe)
      
      // Add delay between requests to respect rate limits
      await new Promise(resolve => setTimeout(resolve, 1000))
      
    } catch (error) {
      console.error(`❌ Failed to process ${ticker} ${timeframe}:`, error.message)
      // Continue with other timeframes even if one fails
    }
  }
  
  console.log(`🎉 Completed processing all timeframes for ${ticker}`)
}

module.exports = {
  initializeTimeframeTable,
  fetchHistoricalData,
  storeTimeframeData,
  getTimeframeData,
  getDateRange,
  fetchAndStoreTimeframeData,
  fetchAndStoreAllTimeframes
}
