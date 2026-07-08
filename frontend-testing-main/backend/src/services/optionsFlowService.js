const { createClient } = require('@clickhouse/client');
const NodeCache = require('node-cache');
const axios = require('axios');
require('dotenv').config();

// Create ClickHouse client with connection details from environment variables
const clickhouse = createClient({
  url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
  username: process.env.CLICKHOUSE_USER || 'default',
  password: process.env.CLICKHOUSE_PASSWORD || '',
  database: process.env.CLICKHOUSE_DATABASE || 'options'
});

// Create cache for recent queries with optimized settings for prefetching
const queryCache = new NodeCache({ 
  stdTTL: 180,        // 3 minutes cache for prefetched data
  checkperiod: 60,    // Check for expired keys every minute
  useClones: false,   // Don't clone objects (better performance)
  maxKeys: 100        // Limit cache size to prevent memory issues
});

/**
 * Parse option symbol to extract ticker, strike, type, and expiration
 * Example: O:CRWV250808C00145000 -> { ticker: 'CRWV', strike: 145.00, type: 'C', expiration: '2025-08-08' }
 */
function parseOptionSymbol(symbol) {
  try {
    // Remove O: prefix
    const cleanSymbol = symbol.replace('O:', '');
    
    // Regex to parse: TICKER + YYMMDD + (C|P) + Strike (8 digits with implied decimals)
    const regex = /^([A-Z]+)(\d{6})([CP])(\d{8})$/;
    const match = cleanSymbol.match(regex);
    
    if (!match) {
      throw new Error(`Cannot parse option symbol: ${symbol}`);
    }
    
    const [, ticker, dateStr, optionType, strikeStr] = match;
    
    // Parse date: YYMMDD to YYYY-MM-DD
    const year = 2000 + parseInt(dateStr.substring(0, 2));
    const month = dateStr.substring(2, 4);
    const day = dateStr.substring(4, 6);
    const expiration = `${year}-${month}-${day}`;
    
    // Parse strike: 8 digits with implied 3 decimal places
    const strike = parseInt(strikeStr) / 1000;
    
    return {
      ticker,
      strike,
      type: optionType,
      expiration
    };
  } catch (error) {
    console.error('Error parsing option symbol:', symbol, error);
    return {
      ticker: symbol,
      strike: 0,
      type: 'C',
      expiration: new Date().toISOString().split('T')[0]
    };
  }
}

/**
 * Calculate Days to Expiration (DTE)
 * Returns positive values for future expirations and negative values for expired contracts
 */
function calculateDTE(expiration) {
  try {
    const expirationDate = new Date(expiration);
    const currentDate = new Date();
    const timeDiff = expirationDate.getTime() - currentDate.getTime();
    const daysDiff = Math.ceil(timeDiff / (1000 * 3600 * 24));
    return daysDiff; // Allow negative values for expired contracts
  } catch (error) {
    console.error('Error calculating DTE:', expiration, error);
    return 0;
  }
}

/**
 * Format timestamp to EST 24-hour format
 */
function formatTimestampEST(timestamp) {
  try {
    const date = new Date(timestamp);
    return date.toLocaleString('en-US', {
      timeZone: 'America/New_York',
      hour12: false,
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    }).replace(/(\d{2})\/(\d{2})\/(\d{4}), (\d{2}:\d{2}:\d{2})/, '$1/$2 • $4');
  } catch (error) {
    console.error('Error formatting timestamp:', timestamp, error);
    return '';
  }
}

/**
 * Get current date in EST timezone for filtering
 */
function getCurrentDateEST() {
  const now = new Date();
  
  // Use Intl.DateTimeFormat to get proper EST date components
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
  
  const estDateString = formatter.format(now); // Returns YYYY-MM-DD format
  console.log(`Current EST date: ${estDateString}`);
  return estDateString;
}

/**
 * Transform a single row of data from ClickHouse to frontend format
 * Simplified - just pass through raw data with minimal transformation
 */
async function transformSingleRow(row, index, skipExternalAPIs = false) {
  // Clean and normalize symbol (remove extra spaces, ensure O: prefix)
  let cleanSymbol = row.symbol.replace(/\s+/g, ''); // Remove all whitespace
  if (!cleanSymbol.startsWith('O:')) {
    cleanSymbol = 'O:' + cleanSymbol;
  }
  
  // Parse symbol to extract basic info
  const parsedSymbol = parseOptionSymbol(cleanSymbol);
  
  // Format contract details as "strike type date"
  // Example: "450 call 10/21/2025"
  const optionType = parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put';
  const formattedDate = new Date(parsedSymbol.expiration).toLocaleDateString('en-US'); // MM/DD/YYYY
  const contractDetails = `${parsedSymbol.strike} ${optionType} ${formattedDate}`;
  
  return {
    index: `${index + 1}`,
    _id: `${row.symbol}_${row.timestamp}`,
    _score: null,
    _source: {
      // Raw data from ClickHouse
      id: `${row.symbol}_${row.timestamp}`,
      symbol: row.symbol,
      ticker: parsedSymbol.ticker,
      contract_id: contractDetails,
      price: row.price?.toString() || '0',
      size: row.size || 0,
      exchange_id: '0',
      
      // Bid/Ask from ClickHouse (max 3 decimal places, truncate not round, remove trailing zeros)
      bid: row.bid_px_00 ? parseFloat((Math.floor(parseFloat(row.bid_px_00) * 1000) / 1000).toFixed(3)).toString() : '0',
      ask: row.ask_px_00 ? parseFloat((Math.floor(parseFloat(row.ask_px_00) * 1000) / 1000).toFixed(3)).toString() : '0',
      bid_size: 0,
      ask_size: 0,
      mid_price: row.bid_px_00 && row.ask_px_00 ? parseFloat((Math.floor(((row.bid_px_00 + row.ask_px_00) / 2) * 1000) / 1000).toFixed(3)).toString() : '0',
      
      // Timestamps
      sip_timestamp: new Date(row.timestamp).getTime(),
      sip_timestamp_friendly: new Date(row.timestamp).toISOString(),
      participant_timestamp: new Date(row.timestamp).getTime(),
      participant_timestamp_friendly: new Date(row.timestamp).toISOString(),
      sip_date_friendly: new Date(row.timestamp).toISOString(),
      trade_time: new Date(row.timestamp).toISOString(),
      created_at: new Date(row.timestamp).toISOString(),
      updated_at: new Date(row.timestamp).toISOString(),
      
      // Calculated values
      net_premium: Math.round((row.size || 0) * ((row.price || 0) * 100)).toString(),
      type: parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put',
      strike: parsedSymbol.strike.toString(),
      expiration: parsedSymbol.expiration,
      dte: calculateDTE(parsedSymbol.expiration),
      
      // Empty/default values for fields that don't exist in new table
      spot_price: '0',
      underlying_asset_price: 0,
      trade_date: new Date(row.timestamp).toISOString().split('T')[0],
      trade_day: new Date(row.timestamp).getDate(),
      trade_hour: new Date(row.timestamp).getHours(),
      trade_minute: new Date(row.timestamp).getMinutes(),
      trade_second: new Date(row.timestamp).getSeconds(),
      trade_month: new Date(row.timestamp).getMonth() + 1,
      otm: '',
      itm: null,
      is_otm: 0,
      is_itm: 0,
      vwap: null,
      is_above_vwap: 0,
      is_below_vwap: 0,
      backtest_at: null,
      delta: null,
      gamma: null,
      vega: null,
      open_interest: 0,
      iv: null,
      rho: null,
      theta: null,
      bought: 0,
      sold: 0,
      above_mid_price: 0,
      below_mid_price: 0,
      above_ask_price: 0,
      below_bid_price: 0,
      days_to_earnings: null,
      signal_scan_at: null,
      is_sweep: 0,
      is_block: 0,
      is_split: 0,
      is_floor: 0,
      is_cross: 0,
      single_leg: 1,
      multi_leg: 0,
      is_bullish: 0,
      is_bearish: 0,
      krono: '',
      vw_delta: '',
      vw_gamma: '',
      vw_theta: '',
      vw_vega: '',
      vw_iv: '',
      vw_rho: '',
      spread: null,
      daily_volume: 0,
      daily_bought_volume: null,
      daily_sold_volume: null,
      weekly_volume: 0,
      weekly_bought_volume: null,
      weekly_sold_volume: null,
      '5m_return': '',
      '1d_return': '',
      '1w_return': '',
      '1m_return': null,
      '3m_return': null,
      '1y_return': null,
      bought_volume: 0,
      sold_volume: 0,
      bought_volume_day: 0,
      sold_volume_day: 0,
      total_volume: null,
      is_opening: 0,
      sector: null,
      industry_type: null,
      tags: null,
      dg_diff: null,
      dg_diff_perc: null,
      dt_diff: null,
      dt_diff_perc: null,
      tags_count: 0,
      vw_spread: null,
      conditions: []
    },
    _ids: null
  };
}


/**
 * Get stock price data from Polygon API for a specific ticker and timestamp
 */
async function getStockPrice(ticker, timestamp) {
  try {
    const apiKey = 'p_MWsJKX9K5FpW0pkfEysOphM9gmkTsv';
    
    // Get the trade date in YYYY-MM-DD format
    const tradeDate = new Date(timestamp);
    const tradeDateStr = tradeDate.toLocaleDateString('en-CA', { timeZone: 'America/New_York' }); // YYYY-MM-DD format in EST
    
    // Use the daily bars endpoint to get the stock price for the specific trade date
    const url = `https://api.polygon.io/v2/aggs/ticker/${ticker}/range/1/day/${tradeDateStr}/${tradeDateStr}?adjusted=true&sort=asc&limit=1&apiKey=${apiKey}`;
    console.log(`📞 Calling Stock API for ${tradeDateStr}: ${url}`);
    
    const response = await axios.get(url, {
        timeout: 10000 // 10 second timeout
      });
      
      if (response.data && response.data.results && response.data.results.length > 0) {
      const bar = response.data.results[0];
      // Use the close price for the trading day
      const price = bar.c || 0;
      console.log(`✅ Got stock price for ${ticker} on ${tradeDateStr}: $${price} (close price)`);
      return price;
    } else {
      console.log(`📭 No stock price found for ${ticker} on ${tradeDateStr}`);
    return 0;
    }
  } catch (error) {
    console.error(`❌ Error fetching stock price for ${ticker}:`, error.message);
    if (error.response) {
      console.error(`   Status: ${error.response.status}`);
      console.error(`   Data:`, error.response.data);
    }
    return 0;
  }
}

/**
 * Check if market is currently open using Polygon.io API
 */
async function isMarketOpen() {
  try {
    const response = await axios.get('https://api.polygon.io/v1/marketstatus/now?apiKey=p_MWsJKX9K5FpW0pkfEysOphM9gmkTsv');
    const marketStatus = response.data;
    
    // Check if NYSE or NASDAQ is open (main equity markets)
    const nyseStatus = marketStatus.exchanges?.nyse;
    const nasdaqStatus = marketStatus.exchanges?.nasdaq;
    
    const isOpen = nyseStatus === 'open' || nasdaqStatus === 'open';
    const isExtendedHours = nyseStatus === 'extended-hours' || nasdaqStatus === 'extended-hours';
    
    console.log(`Market Status - NYSE: ${nyseStatus}, NASDAQ: ${nasdaqStatus}`);
    console.log(`Market Open: ${isOpen}, Extended Hours: ${isExtendedHours}`);
    
    return {
      isOpen,
      isExtendedHours,
      marketStatus: marketStatus.market,
      serverTime: marketStatus.serverTime,
      exchanges: marketStatus.exchanges
    };
  } catch (error) {
    console.error('Error checking market status:', error.message);
    // Fallback: assume market was open today if it's a weekday
    const now = new Date();
    const estNow = new Date(now.toLocaleString('en-US', { timeZone: 'America/New_York' }));
    const dayOfWeek = estNow.getDay();
    const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
    const hour = estNow.getHours();
    
    // If it's a weekday and after 9:30 AM EST, assume market was open today
    const wasMarketOpen = !isWeekend && hour >= 9;
    const isAfterHours = !isWeekend && hour >= 16 && hour < 20; // 4 PM - 8 PM EST
    
    console.log(`Fallback logic: Weekday: ${!isWeekend}, Hour: ${hour}, Was Market Open: ${wasMarketOpen}`);
    
    return {
      isOpen: wasMarketOpen && hour >= 9 && hour < 16, // 9:30 AM - 4 PM EST
      isExtendedHours: isAfterHours,
      marketStatus: isWeekend ? 'closed' : (wasMarketOpen ? 'unknown' : 'closed'),
      serverTime: now.toISOString(),
      exchanges: null
    };
  }
}

/**
 * Get the most recent trading day using market status and data availability
 */
async function getLastTradingDay(startDate = null) {
  try {
    const todayEST = getCurrentDateEST();
    const searchDate = startDate || todayEST;
    
    console.log(`Starting search from date: ${searchDate}`);
    console.log(`Today's EST date: ${todayEST}`);
    
    // ALWAYS check today's date first, regardless of market status
    if (searchDate === todayEST) {
      console.log(`Checking if today (${todayEST}) has data...`);
      
      try {
        const testQuery = `SELECT COUNT(*) as count FROM options WHERE toDate(ts_recv) = '${todayEST}' LIMIT 1`;
        const result = await clickhouse.query({
          query: testQuery,
          format: 'JSONEachRow',
          clickhouse_settings: {
            max_execution_time: 5,
            max_threads: 1
          }
        });
        
        const data = await result.json();
        const count = parseInt(data[0]?.count || 0);
        
        if (count > 0) {
          console.log(`Found data for today ${todayEST}: ${count} records`);
          return todayEST;
        } else {
          console.log(`No data found for today ${todayEST}, checking previous days...`);
        }
      } catch (error) {
        console.error(`Error checking today's data:`, error.message);
      }
    }
    
    // Check market status for guidance
    const marketInfo = await isMarketOpen();
    console.log(`Market status: ${marketInfo.marketStatus}, isOpen: ${marketInfo.isOpen}, isExtendedHours: ${marketInfo.isExtendedHours}`);
    
    let currentDate = new Date(searchDate);
    
    // Go back up to 10 days to find a trading day with data
    for (let daysBack = 1; daysBack <= 10; daysBack++) {
      const checkDate = new Date(currentDate);
      checkDate.setDate(checkDate.getDate() - daysBack);
      
      // Check for data first, don't skip weekends automatically since data might exist
      const dayOfWeek = checkDate.getDay();
      const isWeekend = dayOfWeek === 0 || dayOfWeek === 6;
      
      const dateStr = checkDate.toISOString().split('T')[0];
      
      try {
        // Quick check if this date has any data with shorter timeout
        const testQuery = `SELECT COUNT(*) as count FROM options WHERE toDate(ts_recv) = '${dateStr}' LIMIT 1`;
        const result = await clickhouse.query({
          query: testQuery,
          format: 'JSONEachRow',
          clickhouse_settings: {
            max_execution_time: 5, // Short timeout for test queries
            max_threads: 1
          }
        });
        
        const data = await result.json();
        const count = parseInt(data[0]?.count || 0);
        
        if (count > 0) {
          console.log(`Found trading day with data: ${dateStr} (${count} records)${isWeekend ? ' [Weekend with data]' : ''}`);
          return dateStr;
        } else {
          console.log(`No data found for ${dateStr}${isWeekend ? ' [Weekend]' : ''}, checking previous day...`);
        }
      } catch (error) {
        console.error(`Error checking date ${dateStr}:`, error.message);
        continue;
      }
    }
    
    // Fallback to current date if no trading day found
    console.log('No recent trading day found, using current date as fallback');
    return searchDate;
    
  } catch (error) {
    console.error('Error in getLastTradingDay:', error);
    return startDate || getCurrentDateEST();
  }
}

/**
 * Parse ticker filter string to array of tickers
 */
function parseTickerFilter(tickerString) {
  if (!tickerString || typeof tickerString !== 'string') {
    return [];
  }
  
  return tickerString
    .split(',')
    .map(ticker => ticker.trim().toUpperCase())
    .filter(ticker => ticker.length > 0);
}

/**
 * Build ticker filter conditions for SQL query (case-insensitive)
 */
function buildTickerFilterConditions(tickers, ignoreTickers) {
  let condition = null;
  
  // Include specific tickers (ONLY these tickers)
  // Symbol format in DB: "QQQ 241014C00500000" or "AAPL 241014P00180000"
  // Using ILIKE for case-insensitive matching (works in ClickHouse)
  if (tickers && tickers.length > 0) {
    const tickerConditions = tickers.map(ticker => {
      // Match ticker at the beginning of symbol followed by a space (case-insensitive)
      // Convert to uppercase for consistency since parseTickerFilter already does this
      return `upper(symbol) LIKE '${ticker.toUpperCase()} %'`;
    }).join(' OR ');
    condition = `(${tickerConditions})`;
  }
  
  // Exclude specific tickers (remove these from results)
  if (ignoreTickers && ignoreTickers.length > 0) {
    const ignoreConditions = ignoreTickers.map(ticker => {
      // Match ticker at the beginning of symbol followed by a space (case-insensitive)
      return `upper(symbol) NOT LIKE '${ticker.toUpperCase()} %'`;
    }).join(' AND ');
    
    if (condition) {
      // If we have include filter, apply exclude on top of it
      condition = `(${condition}) AND (${ignoreConditions})`;
    } else {
      // If no include filter, just apply exclude filter
      condition = `(${ignoreConditions})`;
    }
  }
  
  return condition;
}

/**
 * Fetch options flow data from ClickHouse
 */
async function getOptionsFlowData(page = 1, pageSize = 100, tradingDate = null, filters = {}) {
  try {
    const offset = (page - 1) * pageSize;
    
    console.log(`Searching for options flow data from ANY date in database`);
    console.log(`Filtering out invalid trades (conditions 201, 203, 205, 207)`);
    console.log(`🔍 Backend Debug: page=${page}, pageSize=${pageSize}, offset=${offset}`);
    console.log(`🔍 Applied filters:`, filters);
    
    // Parse ticker filters
    const includeTickers = parseTickerFilter(filters.tickers);
    const excludeTickers = parseTickerFilter(filters.ignoreTickers);
    
    console.log(`\n🎯 TICKER FILTER DEBUG:`);
    console.log(`   Raw Include: "${filters.tickers}"`);
    console.log(`   Parsed Include:`, includeTickers);
    console.log(`   Raw Exclude: "${filters.ignoreTickers}"`);
    console.log(`   Parsed Exclude:`, excludeTickers);
    
    if (includeTickers.length > 0) {
      console.log(`   ✅ Will ONLY show: ${includeTickers.join(', ')}`);
    }
    if (excludeTickers.length > 0) {
      console.log(`   ❌ Will EXCLUDE: ${excludeTickers.join(', ')}`);
    }
    
    // Create cache key including filters
    const filterKey = JSON.stringify(filters);
    const cacheKey = `flow_data_${page}_${pageSize}_${filterKey}`;
    
    // Check if we have cached data (skip cache for filtered requests to ensure fresh data)
    if (!filters.tickers && !filters.ignoreTickers) {
      const cachedData = queryCache.get(cacheKey);
      if (cachedData) {
        console.log(`Returning cached data for page ${page}`);
        return cachedData;
      }
    }
    
    // Build ticker filter conditions
    const tickerFilterConditions = buildTickerFilterConditions(includeTickers, excludeTickers);
    
    if (tickerFilterConditions) {
      console.log(`\n🔧 Generated SQL Condition:`);
      console.log(`   ${tickerFilterConditions}`);
    } else {
      console.log(`\nℹ️ No ticker-specific filters applied (showing all tickers)`);
    }
    
    // Start with a simple base condition (1=1 to make adding filters easier)
    let whereConditions = `1=1`;
    
    // Apply ticker filters if provided
    if (tickerFilterConditions) {
      whereConditions += ` AND ${tickerFilterConditions}`;
      console.log(`✅ Applied ticker filters`);
    } else {
      // When no specific ticker filter is provided, exclude index options for performance
      // This prevents scanning the entire table but still allows size/premium/DTE filters to work
      whereConditions += ` AND upper(symbol) NOT LIKE 'SPXW %' AND upper(symbol) NOT LIKE 'XSP %' AND upper(symbol) NOT LIKE 'SPX %' AND upper(symbol) NOT LIKE 'VIX %' AND upper(symbol) NOT LIKE 'VIXW %' AND upper(symbol) NOT LIKE 'NDX %'`;
      console.log(`🚫 No ticker filter provided - excluding index options (SPXW, XSP, SPX, VIX, VIXW, NDX) for performance`);
    }
    
    // Add date filter if provided
    if (tradingDate) {
      whereConditions += ` AND toDate(ts_recv) = '${tradingDate}'`;
    }
    
    // Add additional filters (DTE, premium, size, etc.)
    // DTE filtering: Extract expiration date from symbol and calculate days to expiration
    // Symbol format: "TICKER   YYMMDDCSTRIKE" or "TICKER   YYMMDDPSTRIKE"
    // Example: "SPY   251015C00650000" -> expiration is 2025-10-15
    if (filters.dteMin !== null && filters.dteMin !== undefined) {
      // Extract YYMMDD from symbol (after spaces, 6 digits)
      // Parse as date: concat('20', substr(symbol, position(symbol, ' ') + 3, 2), '-', substr(symbol, position(symbol, ' ') + 5, 2), '-', substr(symbol, position(symbol, ' ') + 7, 2))
      // Calculate DTE: dateDiff('day', today(), expiration_date)
      whereConditions += ` AND dateDiff('day', today(), toDate(concat('20', substring(symbol, length(trim(substring(symbol, 1, position(symbol, ' ')))) + 2, 2), '-', substring(symbol, length(trim(substring(symbol, 1, position(symbol, ' ')))) + 4, 2), '-', substring(symbol, length(trim(substring(symbol, 1, position(symbol, ' ')))) + 6, 2)))) >= ${filters.dteMin}`;
      console.log(`📅 DTE Min filter: >= ${filters.dteMin} days`);
    }
    
    if (filters.dteMax !== null && filters.dteMax !== undefined) {
      whereConditions += ` AND dateDiff('day', today(), toDate(concat('20', substring(symbol, length(trim(substring(symbol, 1, position(symbol, ' ')))) + 2, 2), '-', substring(symbol, length(trim(substring(symbol, 1, position(symbol, ' ')))) + 4, 2), '-', substring(symbol, length(trim(substring(symbol, 1, position(symbol, ' ')))) + 6, 2)))) <= ${filters.dteMax}`;
      console.log(`📅 DTE Max filter: <= ${filters.dteMax} days`);
    }
    
    if (filters.minimumPremium !== null && filters.minimumPremium !== undefined) {
      // Premium = size * (price * 100)
      whereConditions += ` AND (size * (price * 100)) >= ${filters.minimumPremium}`;
    }
    
    if (filters.maximumPremium !== null && filters.maximumPremium !== undefined) {
      whereConditions += ` AND (size * (price * 100)) <= ${filters.maximumPremium}`;
    }
    
    if (filters.minimumSize !== null && filters.minimumSize !== undefined) {
      whereConditions += ` AND size >= ${filters.minimumSize}`;
    }
    
    if (filters.maximumSize !== null && filters.maximumSize !== undefined) {
      whereConditions += ` AND size <= ${filters.maximumSize}`;
    }
    
    // Add call/put filtering based on symbol
    // Symbol format in DB: TICKER + YYMMDD + (C|P) + STRIKE (8 digits)
    // Examples: "SPY   251015C00650000" or "AAPL  241014P00180000"
    // Use regex pattern matching to identify calls vs puts
    if (filters.calls === false) {
      // Hide calls - exclude contracts with 'C' in position after date
      whereConditions += ` AND symbol NOT REGEXP '[A-Z]+\\s+[0-9]{6}C[0-9]{8}'`;
      console.log(`🚫 Hiding call contracts`);
    }
    
    if (filters.puts === false) {
      // Hide puts - exclude contracts with 'P' in position after date
      whereConditions += ` AND symbol NOT REGEXP '[A-Z]+\\s+[0-9]{6}P[0-9]{8}'`;
      console.log(`🚫 Hiding put contracts`);
    }
    
    // If only calls should be shown (puts hidden)
    if (filters.calls === true && filters.puts !== true) {
      whereConditions += ` AND symbol REGEXP '[A-Z]+\\s+[0-9]{6}C[0-9]{8}'`;
      console.log(`✅ Showing only call contracts`);
    }
    
    // If only puts should be shown (calls hidden)
    if (filters.puts === true && filters.calls !== true) {
      whereConditions += ` AND symbol REGEXP '[A-Z]+\\s+[0-9]{6}P[0-9]{8}'`;
      console.log(`✅ Showing only put contracts`);
    }
    
    console.log(`📋 Final WHERE conditions: ${whereConditions}`);
    
    const query = `
      SELECT 
        symbol,
        price,
        size,
        ts_recv as timestamp,
        bid_px_00,
        ask_px_00,
        instrument_id
      FROM options 
      WHERE ${whereConditions}
      ORDER BY ts_recv DESC
      LIMIT ${pageSize}
      OFFSET ${offset}
    `;
    
    // Simple query execution
    const dataResult = await clickhouse.query({
      query: query,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_threads: 4,
        max_execution_time: 30,
        enable_http_compression: 1
      }
    });
    
    const rows = await dataResult.json();
    console.log(`🔍 Backend Debug: ClickHouse returned ${rows.length} rows for pageSize ${pageSize}`);
    
    // === RAW TRADE DATA FROM FLOW TABLE ===
    console.log('\n📊 RAW TRADE DATA FROM FLOW TABLE:');
    console.log('===============================================');
    
    if (rows.length > 0) {
      // Show first few results with ticker extraction
      rows.slice(0, 5).forEach((row, index) => {
        const parsedSymbol = parseOptionSymbol(row.symbol);
        console.log(`\n--- Trade ${index + 1} ---`);
        console.log(`Symbol: ${row.symbol}`);
        console.log(`Instrument ID: ${row.instrument_id}`);
        console.log(`Extracted Ticker: ${parsedSymbol.ticker}`);
        console.log(`Price: $${row.price}`);
        console.log(`Size: ${row.size}`);
        console.log(`Exchange: ${row.exchange}`);
      });
      
      // Show unique tickers in results
      const uniqueTickers = [...new Set(rows.map(row => {
        // Extract ticker from symbol (format: "QQQ 241014C00500000")
        const ticker = row.symbol.split(' ')[0];
        return ticker;
      }))].sort();
      
      console.log(`\n🎯 RESULTS SUMMARY:`);
      console.log(`   Total trades returned: ${rows.length}`);
      console.log(`   Unique tickers (${uniqueTickers.length}): ${uniqueTickers.join(', ')}`);
      
      if (includeTickers.length > 0) {
        const missing = includeTickers.filter(t => !uniqueTickers.includes(t));
        if (missing.length > 0) {
          console.log(`   ⚠️  No results found for: ${missing.join(', ')}`);
        }
      }
    } else {
      console.log(`❌ No results returned! Check filter conditions.`);
    }
    console.log('\n===============================================\n');
    
    // Count query with same filters applied
    const countQuery = `SELECT COUNT(*) as total FROM options WHERE ${whereConditions}`;
    console.log(`📊 Count query: ${countQuery}`);
    
    const countResult = await clickhouse.query({
      query: countQuery,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_threads: 2,
        max_execution_time: 10,
        enable_http_compression: 1
      }
    });
    const countData = await countResult.json();
    const totalCount = countData[0]?.total || 0;
    
    // Transform the data for the frontend with bid/ask data
    // Always fetch full data, but use smart batching for large page sizes
    const useBatchProcessing = pageSize > 100;
    
    if (useBatchProcessing) {
      console.log(`⚡ Batch mode: Processing ${pageSize} records with controlled rate limiting`);
    } else {
      console.log(`🚀 Standard mode: Processing ${pageSize} records`);
    }
    
    // Process data transformation (simplified - no batching needed)
    const transformedData = rows.map((row, index) => {
      // Clean and normalize symbol (remove extra spaces, ensure O: prefix)
      let cleanSymbol = row.symbol.replace(/\s+/g, '');
      if (!cleanSymbol.startsWith('O:')) {
        cleanSymbol = 'O:' + cleanSymbol;
      }
      
      const parsedSymbol = parseOptionSymbol(cleanSymbol);
      
      // Format contract details as "strike type date"
      const optionType = parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put';
      const formattedDate = new Date(parsedSymbol.expiration).toLocaleDateString('en-US');
      const contractDetails = `${parsedSymbol.strike} ${optionType} ${formattedDate}`;
      
      return {
        index: `${offset + index + 1}`,
        _id: `${row.symbol}_${row.timestamp}`,
        _score: null,
        _source: {
          // Raw data from ClickHouse
          id: `${row.symbol}_${row.timestamp}`,
          symbol: row.symbol,
          ticker: parsedSymbol.ticker,
          contract_id: contractDetails,
          price: row.price?.toString() || '0',
          size: row.size || 0,
          exchange_id: '0',
          
          // Bid/Ask from ClickHouse (max 3 decimal places, truncate not round, remove trailing zeros)
          bid: row.bid_px_00 ? parseFloat((Math.floor(parseFloat(row.bid_px_00) * 1000) / 1000).toFixed(3)).toString() : '0',
          ask: row.ask_px_00 ? parseFloat((Math.floor(parseFloat(row.ask_px_00) * 1000) / 1000).toFixed(3)).toString() : '0',
          bid_size: 0,
          ask_size: 0,
          mid_price: row.bid_px_00 && row.ask_px_00 ? parseFloat((Math.floor(((row.bid_px_00 + row.ask_px_00) / 2) * 1000) / 1000).toFixed(3)).toString() : '0',
          
          // Timestamps
          sip_timestamp: new Date(row.timestamp).getTime(),
          sip_timestamp_friendly: new Date(row.timestamp).toISOString(),
          participant_timestamp: new Date(row.timestamp).getTime(),
          participant_timestamp_friendly: new Date(row.timestamp).toISOString(),
          sip_date_friendly: new Date(row.timestamp).toISOString(),
          trade_time: new Date(row.timestamp).toISOString(),
          created_at: new Date(row.timestamp).toISOString(),
          updated_at: new Date(row.timestamp).toISOString(),
          
          // Calculated values
          net_premium: Math.round((row.size || 0) * ((row.price || 0) * 100)).toString(),
          type: parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put',
          strike: parsedSymbol.strike.toString(),
          expiration: parsedSymbol.expiration,
          dte: calculateDTE(parsedSymbol.expiration),
          
          // Defaults for UI compatibility
          spot_price: '0',
          underlying_asset_price: 0,
          trade_date: new Date(row.timestamp).toISOString().split('T')[0],
          trade_day: new Date(row.timestamp).getDate(),
          trade_hour: new Date(row.timestamp).getHours(),
          trade_minute: new Date(row.timestamp).getMinutes(),
          trade_second: new Date(row.timestamp).getSeconds(),
          trade_month: new Date(row.timestamp).getMonth() + 1,
          otm: '',
          itm: null,
          is_otm: 0,
          is_itm: 0,
          vwap: null,
          is_above_vwap: 0,
          is_below_vwap: 0,
          backtest_at: null,
          delta: null,
          gamma: null,
          vega: null,
          open_interest: 0,
          iv: null,
          rho: null,
          theta: null,
          bought: 0,
          sold: 0,
          above_mid_price: 0,
          below_mid_price: 0,
          above_ask_price: 0,
          below_bid_price: 0,
          days_to_earnings: null,
          signal_scan_at: null,
          is_sweep: 0,
          is_block: 0,
          is_split: 0,
          is_floor: 0,
          is_cross: 0,
          single_leg: 1,
          multi_leg: 0,
          is_bullish: 0,
          is_bearish: 0,
          krono: '',
          vw_delta: '',
          vw_gamma: '',
          vw_theta: '',
          vw_vega: '',
          vw_iv: '',
          vw_rho: '',
          spread: null,
          daily_volume: 0,
          daily_bought_volume: null,
          daily_sold_volume: null,
          weekly_volume: 0,
          weekly_bought_volume: null,
          weekly_sold_volume: null,
          '5m_return': '',
          '1d_return': '',
          '1w_return': '',
          '1m_return': null,
          '3m_return': null,
          '1y_return': null,
          bought_volume: 0,
          sold_volume: 0,
          bought_volume_day: 0,
          sold_volume_day: 0,
          total_volume: null,
          is_opening: 0,
          sector: null,
          industry_type: null,
          tags: null,
          dg_diff: null,
          dg_diff_perc: null,
          dt_diff: null,
          dt_diff_perc: null,
          tags_count: 0,
          vw_spread: null,
          conditions: []
        },
        _ids: null
      };
    });
    
    // Apply post-processing filters (DTE, etc.)
    let filteredData = transformedData;
    
    // Apply DTE filters if specified
    if (filters.dteMin !== null && filters.dteMin !== undefined) {
      filteredData = filteredData.filter(item => item._source.dte >= filters.dteMin);
      console.log(`📅 Applied DTE Min filter (${filters.dteMin}): ${filteredData.length} results remaining`);
    }
    
    if (filters.dteMax !== null && filters.dteMax !== undefined) {
      filteredData = filteredData.filter(item => item._source.dte <= filters.dteMax);
      console.log(`📅 Applied DTE Max filter (${filters.dteMax}): ${filteredData.length} results remaining`);
    }
    
    // Apply hideExpired filter if specified
    if (filters.hideExpired === true) {
      filteredData = filteredData.filter(item => item._source.dte >= 0);
      console.log(`🚫 Applied Hide Expired filter: ${filteredData.length} results remaining (showing only contracts with DTE >= 0)`);
    }
    
    const result = {
      results: filteredData,
      totalCount: totalCount // Note: This is the total before DTE filtering for pagination purposes
    };
    
    console.log(`🔍 Backend Debug: Returning ${filteredData.length} filtered records (${transformedData.length} before DTE filtering) for pageSize ${pageSize}`);
    
    // Cache the result for future requests (only cache non-filtered results to avoid cache pollution)
    if (!filters.tickers && !filters.ignoreTickers && Object.keys(filters).every(key => filters[key] === null || filters[key] === undefined || (Array.isArray(filters[key]) && filters[key].length === 0))) {
      queryCache.set(cacheKey, result);
      console.log('Cached data for page', page);
    } else {
      console.log('Skipping cache for filtered results');
    }
    
    return result;
   } catch (error) {
     console.error('Error in getOptionsFlowData:', error);
     throw error;
   }
}

/**
 * Prefetch multiple pages in background to improve UX
 */
// Prefetching removed for simplification

/**
 * Background prefetching for next pages to improve UX
 */
async function prefetchNextPages(currentPage, pageSize, searchDate) {
  try {
    console.log(`🔄 Starting background prefetch for pages after ${currentPage}`);
    
    // Prefetch next 2 pages in background
    const pagesToPrefetch = [currentPage + 1, currentPage + 2];
    
    for (const nextPage of pagesToPrefetch) {
      const cacheKey = `flow_data_${nextPage}_${pageSize}_${searchDate}`;
      
      // Only prefetch if not already cached
      if (!queryCache.get(cacheKey)) {
        console.log(`🔄 Prefetching page ${nextPage} in background...`);
        
        // Add a small delay to not interfere with current request
        await new Promise(resolve => setTimeout(resolve, 1000));
        
        try {
          await getOptionsFlowData(nextPage, pageSize, searchDate);
          console.log(`✅ Successfully prefetched page ${nextPage}`);
        } catch (error) {
          console.log(`❌ Failed to prefetch page ${nextPage}:`, error.message);
        }
        
        // Add delay between prefetch requests
        await new Promise(resolve => setTimeout(resolve, 2000));
      } else {
        console.log(`⚡ Page ${nextPage} already cached, skipping prefetch`);
      }
    }
    
    console.log(`🔄 Background prefetching completed`);
  } catch (error) {
    console.error('Error in background prefetching:', error);
  }
}

/**
 * Force today's date for debugging after-hours issues
 */
async function getTodaysFlowData(page = 1, pageSize = 100) {
  const todayEST = getCurrentDateEST();
  console.log(`Forcing today's date for flow data: ${todayEST}`);
  return await getOptionsFlowData(page, pageSize, todayEST);
}

/**
 * Get latest flow data after a specific timestamp for real-time updates
 */
async function getLatestFlowData(afterTimestamp, pageSize = 50) {
  try {
    console.log(`Fetching latest trades after timestamp: ${afterTimestamp} (from ANY date)`);
    
    // Convert timestamp to Date object for comparison
    const afterDate = new Date(parseInt(afterTimestamp));
    
    // Build query to get trades after the specified timestamp (ANY DATE)
    // Also exclude index options (symbol format: "SPXW 241014C06580000") - case-insensitive
    const timestampFilter = `ts_recv > '${afterDate.toISOString()}' AND upper(symbol) NOT LIKE 'SPXW %' AND upper(symbol) NOT LIKE 'XSP %' AND upper(symbol) NOT LIKE 'SPX %' AND upper(symbol) NOT LIKE 'VIX %' AND upper(symbol) NOT LIKE 'VIXW %' AND upper(symbol) NOT LIKE 'NDX %'`;
    
    const query = `
      SELECT 
        symbol,
        price,
        size,
        ts_recv as timestamp,
        bid_px_00,
        ask_px_00,
        instrument_id
      FROM options 
      WHERE ${timestampFilter}
      ORDER BY ts_recv DESC 
      LIMIT ${pageSize}
    `;
    
    console.log(`Query: ${query}`);
    
    const result = await clickhouse.query({
      query: query,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_execution_time: 10,
        max_threads: 2
      }
    });

    const rows = await result.json();
    console.log(`Found ${rows.length} new trades after timestamp ${afterTimestamp}`);

    if (rows.length === 0) {
      return {
        results: [],
        totalCount: 0,
        page: 1,
        pageSize: pageSize,
        hasNextPage: false,
        searchDate: 'any_date',
        newTradesCount: 0
      };
    }

    // Transform the data for the frontend (simplified - just pass through)
    const transformedData = await Promise.all(rows.map(async (row, index) => {
      // Clean and normalize symbol (remove extra spaces, ensure O: prefix)
      let cleanSymbol = row.symbol.replace(/\s+/g, '');
      if (!cleanSymbol.startsWith('O:')) {
        cleanSymbol = 'O:' + cleanSymbol;
      }
      
      const parsedSymbol = parseOptionSymbol(cleanSymbol);
      
      // Format contract details as "strike type date"
      const optionType = parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put';
      const formattedDate = new Date(parsedSymbol.expiration).toLocaleDateString('en-US');
      const contractDetails = `${parsedSymbol.strike} ${optionType} ${formattedDate}`;
      
      return {
        index: `${index + 1}`,
        _id: `${row.symbol}_${row.timestamp}`,
        _score: null,
        _source: {
          // Raw data from ClickHouse
          id: `${row.symbol}_${row.timestamp}`,
          symbol: row.symbol,
          ticker: parsedSymbol.ticker,
          contract_id: contractDetails,
          price: row.price?.toString() || '0',
          size: row.size || 0,
          exchange_id: '0',
          
          // Bid/Ask from ClickHouse (max 3 decimal places, truncate not round, remove trailing zeros)
          bid: row.bid_px_00 ? parseFloat((Math.floor(parseFloat(row.bid_px_00) * 1000) / 1000).toFixed(3)).toString() : '0',
          ask: row.ask_px_00 ? parseFloat((Math.floor(parseFloat(row.ask_px_00) * 1000) / 1000).toFixed(3)).toString() : '0',
          bid_size: 0,
          ask_size: 0,
          mid_price: row.bid_px_00 && row.ask_px_00 ? parseFloat((Math.floor(((row.bid_px_00 + row.ask_px_00) / 2) * 1000) / 1000).toFixed(3)).toString() : '0',
          
          // Timestamps
          sip_timestamp: new Date(row.timestamp).getTime(),
          sip_timestamp_friendly: new Date(row.timestamp).toISOString(),
          participant_timestamp: new Date(row.timestamp).getTime(),
          participant_timestamp_friendly: new Date(row.timestamp).toISOString(),
          sip_date_friendly: new Date(row.timestamp).toISOString(),
          trade_time: new Date(row.timestamp).toISOString(),
          created_at: new Date(row.timestamp).toISOString(),
          updated_at: new Date(row.timestamp).toISOString(),
          
          // Calculated values
          net_premium: Math.round((row.size || 0) * ((row.price || 0) * 100)).toString(),
          type: parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put',
          strike: parsedSymbol.strike.toString(),
          expiration: parsedSymbol.expiration,
          dte: calculateDTE(parsedSymbol.expiration),
          
          // Defaults
          spot_price: '0',
          underlying_asset_price: 0,
          trade_date: new Date(row.timestamp).toISOString().split('T')[0],
          trade_day: new Date(row.timestamp).getDate(),
          trade_hour: new Date(row.timestamp).getHours(),
          trade_minute: new Date(row.timestamp).getMinutes(),
          trade_second: new Date(row.timestamp).getSeconds(),
          trade_month: new Date(row.timestamp).getMonth() + 1,
          trade_year: new Date(row.timestamp).getFullYear(),
          otm: '',
          itm: null,
          is_otm: 0,
          is_itm: 0,
          vwap: null,
          is_above_vwap: 0,
          is_below_vwap: 0,
          backtest_at: null,
          delta: null,
          gamma: null,
          vega: null,
          open_interest: 0,
          iv: null,
          rho: null,
          theta: null,
          bought: 0,
          sold: 0,
          above_mid_price: 0,
          below_mid_price: 0,
          above_ask_price: 0,
          below_bid_price: 0,
          days_to_earnings: null,
          signal_scan_at: null,
          is_sweep: 0,
          is_block: 0,
          is_split: 0,
          is_floor: 0,
          is_cross: 0,
          single_leg: 1,
          multi_leg: 0,
          is_bullish: 0,
          is_bearish: 0,
          krono: '',
          vw_delta: '',
          vw_gamma: '',
          vw_theta: '',
          vw_vega: '',
          vw_iv: '',
          vw_rho: '',
          spread: null,
          daily_volume: 0,
          daily_bought_volume: null,
          daily_sold_volume: null,
          weekly_volume: 0,
          weekly_bought_volume: null,
          weekly_sold_volume: null,
          '5m_return': '',
          '1d_return': '',
          '1w_return': '',
          '1m_return': null,
          '3m_return': null,
          '1y_return': null,
          bought_volume: 0,
          sold_volume: 0,
          bought_volume_day: 0,
          sold_volume_day: 0,
          total_volume: null,
          is_opening: 0,
          sector: null,
          industry_type: null,
          tags: null,
          dg_diff: null,
          dg_diff_perc: null,
          dt_diff: null,
          dt_diff_perc: null,
          tags_count: 0,
          vw_spread: null,
          conditions: []
        }
      };
    }));

    const result_data = {
      results: transformedData,
      totalCount: transformedData.length,
      page: 1,
      pageSize: pageSize,
      hasNextPage: false,
      searchDate: 'any_date',
      newTradesCount: transformedData.length
    };

    return result_data;
   } catch (error) {
     console.error('Error in getLatestFlowData:', error);
     throw error;
   }
}

/**
 * Test ClickHouse connection
 */
async function testConnection() {
  try {
    const result = await clickhouse.query({
      query: 'SELECT 1 as test',
      format: 'JSONEachRow'
    });
    const data = await result.json();
    console.log('ClickHouse connection test successful:', data);
    return true;
  } catch (error) {
    console.error('ClickHouse connection test failed:', error);
    return false;
  }
}

/**
 * Console log raw trades from options table - for debugging purposes
 */
async function logRawTradesFromFlowTable(limit = 10) {
  try {
    console.log('\n🔍 QUERYING RAW TRADES FROM OPTIONS TABLE...');
    console.log('==========================================');
    
    // First, check total row count
    const countQuery = `SELECT COUNT(*) as total FROM options`;
    const countResult = await clickhouse.query({
      query: countQuery,
      format: 'JSONEachRow'
    });
    const countData = await countResult.json();
    const totalRows = countData[0]?.total || 0;
    console.log(`📊 TOTAL ROWS IN OPTIONS TABLE: ${totalRows}`);
    
    const query = `
      SELECT 
        symbol,
        price,
        size,
        ts_recv as timestamp,
        bid_px_00,
        ask_px_00,
        instrument_id
      FROM options
      ORDER BY ts_recv DESC
      LIMIT ${limit}
    `;
    
    console.log(`Query: ${query}`);
    console.log(`Getting ANY ${limit} rows from database (no filters)`);
    
    const result = await clickhouse.query({
      query: query,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_execution_time: 10,
        max_threads: 2
      }
    });
    
    const rows = await result.json();
    
    console.log(`\n📊 FOUND ${rows.length} RAW TRADES:`);
    console.log('================================');
    
    rows.forEach((row, index) => {
      console.log(`\n--- RAW TRADE ${index + 1} ---`);
      console.log(`Symbol: ${row.symbol}`);
      console.log(`Instrument ID: ${row.instrument_id}`);
      console.log(`Price: $${row.price}`);
      console.log(`Size: ${row.size} contracts`);
      console.log(`Timestamp: ${row.timestamp}`);
      console.log(`Bid: $${row.bid_px_00 || 0}, Ask: $${row.ask_px_00 || 0}`);
      console.log(`Raw JSON:`, JSON.stringify(row, null, 2));
    });
    
    console.log('\n================================');
    console.log(`📈 TOTAL RAW TRADES DISPLAYED: ${rows.length}`);
    console.log('==========================================\n');
    
    return rows;
    
  } catch (error) {
    console.error('Error querying raw trades from options table:', error);
    throw error;
  }
}

module.exports = {
  getOptionsFlowData,
  getTodaysFlowData,
  getLatestFlowData,
  testConnection,
  parseOptionSymbol,
  calculateDTE,
  formatTimestampEST,
  getLastTradingDay,
  getCurrentDateEST,
  isMarketOpen,
  getStockPrice,
  logRawTradesFromFlowTable
};
