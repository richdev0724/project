const { createClient } = require('@clickhouse/client');
require('dotenv').config();

// Create ClickHouse client for stocks database
const clickhouse = createClient({
  url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
  username: process.env.CLICKHOUSE_USER || 'default',
  password: process.env.CLICKHOUSE_PASSWORD || '',
  database: 'stocks'
});

/**
 * Get whale trades data from the stocks database
 * @param {Object} options - Query options
 * @param {number} options.page - Page number (1-based)
 * @param {number} options.pageSize - Number of records per page
 * @param {number} options.minPremium - Minimum premium threshold for whale trades
 * @param {number} options.maxPremium - Maximum premium threshold for whale trades
 * @param {number} options.minSize - Minimum size filter
 * @param {number} options.maxSize - Maximum size filter
 * @param {number} options.minPrice - Minimum price filter
 * @param {number} options.maxPrice - Maximum price filter
 * @param {string} options.session - Market session filter (comma-separated for multiple)
 * @param {string} options.ticker - Ticker symbol filter
 * @param {string} options.sortBy - Sort field (premium, timestamp, size, price)
 * @returns {Promise<Object>} Whale trades data with pagination info
 */
async function getWhaleTradesData(options = {}) {
  const {
    page = 1,
    pageSize = 50,
    minPremium = 10000, // Default threshold for whale trades: $10,000+
    maxPremium = null, // Optional maximum threshold
    minSize = null,
    maxSize = null,
    minPrice = null,
    maxPrice = null,
    session = null,
    ticker = null,
    sortBy = 'timestamp'
  } = options;

  try {
    const offset = (page - 1) * pageSize;
    
    // Build WHERE clause for filters
    let whereConditions = [`premium >= ${minPremium}`];
    
    if (maxPremium !== null && maxPremium !== undefined) {
      whereConditions.push(`premium <= ${maxPremium}`);
    }
    
    if (minSize !== null && minSize !== undefined) {
      whereConditions.push(`size >= ${minSize}`);
    }
    
    if (maxSize !== null && maxSize !== undefined) {
      whereConditions.push(`size <= ${maxSize}`);
    }
    
    if (minPrice !== null && minPrice !== undefined) {
      whereConditions.push(`price >= ${minPrice}`);
    }
    
    if (maxPrice !== null && maxPrice !== undefined) {
      whereConditions.push(`price <= ${maxPrice}`);
    }
    
    if (session) {
      // Handle multiple sessions (comma-separated)
      // Escape single quotes and wrap each session in quotes
      const sessions = session.split(',').map(s => `'${s.trim().replace(/'/g, "''")}'`).join(',');
      const sessionCondition = `session IN (${sessions})`;
      whereConditions.push(sessionCondition);
    }
    
    if (ticker) {
      whereConditions.push(`symbol = '${ticker.toUpperCase()}'`);
    }
    
    const whereClause = whereConditions.join(' AND ');

    // Get total count for pagination
    const countQuery = `
      SELECT COUNT(*) as total 
      FROM trades 
      WHERE ${whereClause}
    `;
    
    const countResult = await clickhouse.query({
      query: countQuery,
      format: 'JSONEachRow'
    });
    const countData = await countResult.json();
    const totalCount = countData[0]?.total || 0;

    // Build ORDER BY clause based on sortBy parameter
    let orderByClause = 'utc_timestamp DESC, premium DESC'; // Default sorting
    
    if (sortBy === 'premium') {
      orderByClause = 'premium DESC, utc_timestamp DESC';
    } else if (sortBy === 'timestamp') {
      orderByClause = 'utc_timestamp DESC, premium DESC';
    } else if (sortBy === 'size') {
      orderByClause = 'size DESC, premium DESC';
    } else if (sortBy === 'price') {
      orderByClause = 'price DESC, premium DESC';
    }

    // Get paginated whale trades data
    const dataQuery = `
      SELECT 
        symbol as ticker,
        price,
        size,
        premium,
        eastern_time as timestamp,
        session,
        exchange_id,
        trade_id
      FROM trades 
      WHERE ${whereClause}
      ORDER BY ${orderByClause}
      LIMIT ${pageSize} OFFSET ${offset}
    `;

    
    const dataResult = await clickhouse.query({
      query: dataQuery,
      format: 'JSONEachRow'
    });
    const trades = await dataResult.json();

    // Format the data for frontend consumption
    const formattedTrades = trades.map((trade, index) => ({
      id: `${trade.trade_id || `${offset + index + 1}`}`,
      symbol: trade.ticker,
      price: parseFloat(trade.price).toFixed(2),
      size: trade.size,
      total_premium: parseFloat(trade.premium).toFixed(2),
      market_session: trade.session,
      trade_date: new Date(trade.timestamp).toLocaleString('en-US', {
        timeZone: 'America/New_York',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      }),
      timestamp: trade.timestamp,
      exchange_id: trade.exchange_id
    }));

    return {
      data: formattedTrades,
      pagination: {
        page,
        pageSize,
        totalCount: parseInt(totalCount),
        totalPages: Math.ceil(totalCount / pageSize),
        hasNext: page * pageSize < totalCount,
        hasPrev: page > 1
      },
      filters: {
        minPremium,
        session,
        ticker
      }
    };

  } catch (error) {
    console.error('Error fetching whale trades data:', error);
    throw new Error(`Failed to fetch whale trades: ${error.message}`);
  }
}

/**
 * Get whale trades statistics
 * @returns {Promise<Object>} Statistics about whale trades
 */
async function getWhaleTradesStats() {
  try {
    const statsQuery = `
      SELECT 
        COUNT(*) as total_trades,
        SUM(premium) as total_volume,
        AVG(premium) as avg_premium,
        MAX(premium) as max_premium,
        COUNT(DISTINCT symbol) as unique_tickers,
        session,
        COUNT(*) as trades_by_session
      FROM trades 
      WHERE premium >= 10000
      GROUP BY session
      ORDER BY trades_by_session DESC
    `;

    const result = await clickhouse.query({
      query: statsQuery,
      format: 'JSONEachRow'
    });
    const stats = await result.json();

    // Get overall stats (without session grouping)
    const overallQuery = `
      SELECT 
        COUNT(*) as total_trades,
        SUM(premium) as total_volume,
        AVG(premium) as avg_premium,
        MAX(premium) as max_premium,
        COUNT(DISTINCT symbol) as unique_tickers
      FROM trades 
      WHERE premium >= 10000
    `;

    const overallResult = await clickhouse.query({
      query: overallQuery,
      format: 'JSONEachRow'
    });
    const overallStats = await overallResult.json()[0];

    return {
      overall: {
        totalTrades: parseInt(overallStats.total_trades),
        totalVolume: parseFloat(overallStats.total_volume).toFixed(2),
        avgPremium: parseFloat(overallStats.avg_premium).toFixed(2),
        maxPremium: parseFloat(overallStats.max_premium).toFixed(2),
        uniqueTickers: parseInt(overallStats.unique_tickers)
      },
      bySession: stats.map(stat => ({
        session: stat.session,
        trades: parseInt(stat.trades_by_session),
        volume: parseFloat(stat.total_volume).toFixed(2),
        avgPremium: parseFloat(stat.avg_premium).toFixed(2)
      }))
    };

  } catch (error) {
    console.error('Error fetching whale trades stats:', error);
    throw new Error(`Failed to fetch whale trades stats: ${error.message}`);
  }
}

/**
 * Get top whale trades by premium
 * @param {number} limit - Number of top trades to return
 * @returns {Promise<Array>} Top whale trades
 */
async function getTopWhaleTrades(limit = 10) {
  try {
    const query = `
      SELECT 
        symbol as ticker,
        price,
        size,
        premium,
        eastern_time as timestamp,
        session
      FROM trades 
      WHERE premium >= 10000
      ORDER BY premium DESC
      LIMIT ${limit}
    `;

    const result = await clickhouse.query({
      query: query,
      format: 'JSONEachRow'
    });
    const trades = await result.json();

    return trades.map(trade => ({
      ticker: trade.ticker,
      price: parseFloat(trade.price).toFixed(2),
      size: trade.size,
      premium: parseFloat(trade.premium).toFixed(2),
      timestamp: trade.timestamp,
      session: trade.session
    }));

  } catch (error) {
    console.error('Error fetching top whale trades:', error);
    throw new Error(`Failed to fetch top whale trades: ${error.message}`);
  }
}

/**
 * Test connection to stocks database
 */
async function testStocksConnection() {
  try {
    const result = await clickhouse.query({
      query: 'SELECT COUNT(*) as total FROM trades LIMIT 1',
      format: 'JSONEachRow'
    });
    const data = await result.json();
    console.log('Stocks database connection test successful. Total trades:', data[0].total);
    return true;
  } catch (error) {
    console.error('Stocks database connection test failed:', error);
    return false;
  }
}

module.exports = {
  getWhaleTradesData,
  getWhaleTradesStats,
  getTopWhaleTrades,
  testStocksConnection
};
