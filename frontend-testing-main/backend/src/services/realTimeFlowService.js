const { createClient } = require('@clickhouse/client');
const { parseOptionSymbol, calculateDTE, formatTimestampEST, getCurrentDateEST, getStockPrice } = require('./optionsFlowService');
require('dotenv').config();

// Create ClickHouse client with connection details from environment variables
const clickhouse = createClient({
  url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
  username: process.env.CLICKHOUSE_USER || 'default',
  password: process.env.CLICKHOUSE_PASSWORD || '',
  database: process.env.CLICKHOUSE_DATABASE || 'options'
});

let monitoringInterval = null;
let lastProcessedTimestamp = null;
let lastRowCount = 0;
let lastProcessedRowId = null;
let isMonitoring = false;
let activeClients = new Set();

/**
 * Start monitoring the database for new trades using ClickHouse streaming
 */
async function startFlowMonitoring(io, clientId = null) {
  if (clientId) {
    activeClients.add(clientId);
    console.log(`Client ${clientId} joined monitoring. Active clients: ${activeClients.size}`);
  }

  if (isMonitoring) {
    console.log('Flow monitoring is already running');
    return;
  }

  isMonitoring = true;
  console.log('Starting real-time flow monitoring with ClickHouse streaming...');

  // Initialize monitoring state to track new inserts
  if (lastRowCount === 0) {
    await initializeMonitoringState();
  }

  try {
    // Start ClickHouse streaming query for real-time data
    await startClickHouseStreaming(io);
  } catch (error) {
    console.error('Error starting ClickHouse streaming:', error);
    // Fallback to polling if streaming fails
    startPollingFallback(io);
  }

  console.log('Real-time flow monitoring started');
}

/**
 * Stop monitoring the database
 */
function stopFlowMonitoring(clientId = null) {
  if (clientId) {
    activeClients.delete(clientId);
    console.log(`Client ${clientId} left monitoring. Active clients: ${activeClients.size}`);
  }

  // Only stop monitoring if no active clients
  if (activeClients.size > 0) {
    console.log('Other clients still active, keeping monitoring running');
    return;
  }

  if (monitoringInterval) {
    clearInterval(monitoringInterval);
    monitoringInterval = null;
  }
  isMonitoring = false;
  console.log('Real-time flow monitoring stopped - no active clients');
}

/**
 * Start true real-time monitoring using ClickHouse tail mechanism
 */
async function startClickHouseStreaming(io) {
  console.log('Starting ClickHouse tail mechanism for real-time data...');
  
  try {
    // Start continuous monitoring with adaptive timing
    await startContinuousMonitoring(io);
  } catch (error) {
    console.error('Error starting continuous monitoring:', error);
    // Fallback to optimized polling
    startPollingFallback(io);
  }
}

/**
 * Consistent polling with 100ms interval to ensure no trades are missed
 */
async function startContinuousMonitoring(io) {
  console.log('Starting consistent 100ms polling for complete trade coverage...');
  
  // Use a simple, consistent interval to avoid missing data
  monitoringInterval = setInterval(async () => {
    if (!isMonitoring) return;
    
    try {
      await checkForNewTradesConsistent(io);
    } catch (error) {
      console.error('Error in consistent monitoring:', error);
    }
  }, 5); // Ultra-aggressive 5ms polling for instant updates
  
  console.log('Ultra-aggressive monitoring started with 5ms intervals');
}

/**
 * Fallback polling method if streaming doesn't work
 */
function startPollingFallback(io) {
  console.log('Starting polling fallback (50ms intervals)...');
  
  monitoringInterval = setInterval(async () => {
    try {
      await checkForNewTrades(io);
    } catch (error) {
      console.error('Error in polling fallback:', error);
    }
  }, 50); // Very fast polling as fallback
}

/**
 * Complete transformation with all data (simplified - just pass through)
 */
async function transformTradeDataFast(row) {
  try {
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
      _source: {
        // Raw data from ClickHouse
        id: `${row.symbol}_${row.timestamp}_${row.size}_${row.price}`,
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
        timestamp: row.timestamp,
        
        // Calculated values
        net_premium: Math.round((row.size || 0) * ((row.price || 0) * 100)).toString(),
        type: parsedSymbol.type.toLowerCase() === 'c' ? 'call' : 'put',
        strike: parsedSymbol.strike || 0,
        expiry: parsedSymbol.expiration || '',
        
        // Defaults
        underlying_asset_price: 0,
        daily_volume: 0,
        open_interest: 0,
        conditions: [],
        is_multi_leg: false,
        is_opening_trade: false,
        is_floor_trade: false,
        is_sweep: false
      }
    };
  } catch (error) {
    console.error('Error in complete transform:', error);
    throw error;
  }
}

/**
 * Process a new trade from the streaming data
 */
async function processNewTradeFromStream(row, io) {
  try {
    console.log(`STREAM: New trade received - ${row.symbol} at ${row.timestamp}`);
    
    const transformedTrade = await transformTradeData(row);
    
    // Emit immediately to all connected clients
    io.emit('newTrade', transformedTrade);
    
    // Update timestamp
    lastProcessedTimestamp = row.timestamp;
    
    console.log(`STREAM: Broadcasted ${row.symbol} ${row.strike} ${row.type} - ${row.size} @ $${row.price}`);
    
  } catch (error) {
    console.error(`Error processing streamed trade ${row.symbol}:`, error.message);
  }
}

/**
 * Initialize monitoring state by getting current database state
 */
async function initializeMonitoringState() {
  try {
    console.log('Initializing monitoring state for ANY date in database');
    
    // Get current row count and latest record to establish baseline (ANY DATE)
    const countQuery = `
      SELECT COUNT(*) as total_count
      FROM options
    `;
    
    const latestQuery = `
      SELECT ts_recv as timestamp
      FROM options
      ORDER BY ts_recv DESC
      LIMIT 1
    `;

    const [countResult, latestResult] = await Promise.all([
      clickhouse.query({ query: countQuery, format: 'JSONEachRow' }),
      clickhouse.query({ query: latestQuery, format: 'JSONEachRow' })
    ]);

    const countData = await countResult.json();
    const latestData = await latestResult.json();

    lastRowCount = countData[0]?.total_count || 0;
    
    if (latestData.length > 0) {
      lastProcessedTimestamp = latestData[0].timestamp;
    } else {
      lastProcessedTimestamp = new Date().toISOString();
    }

    console.log(`📊 Initialized monitoring state:`);
    console.log(`   Current rows: ${lastRowCount}`);
    console.log(`   Last timestamp: ${lastProcessedTimestamp}`);
    
  } catch (error) {
    console.error('Error initializing monitoring state:', error);
    lastRowCount = 0;
    lastProcessedTimestamp = new Date().toISOString();
  }
}

/**
 * Direct single-query approach for maximum speed
 */
async function checkForNewTradesConsistent(io) {
  try {
    // Single optimized query to get new trades directly (ANY DATE)
    // Exclude index options (symbol format: "SPXW 241014C06580000") - case-insensitive
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
      WHERE ts_recv > '${lastProcessedTimestamp}'
        AND upper(symbol) NOT LIKE 'SPXW %'
        AND upper(symbol) NOT LIKE 'XSP %'
        AND upper(symbol) NOT LIKE 'SPX %'
        AND upper(symbol) NOT LIKE 'VIX %'
        AND upper(symbol) NOT LIKE 'VIXW %'
        AND upper(symbol) NOT LIKE 'NDX %'
      ORDER BY ts_recv ASC 
      LIMIT 1
    `;

    const result = await clickhouse.query({
      query: query,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_execution_time: 0.5, // Very short timeout
        max_threads: 1,
        max_block_size: 1,
        optimize_read_in_order: 1,
        max_memory_usage: 100000000
      }
    });

    const rows = await result.json();
    
    if (rows.length > 0) {
      const row = rows[0];
      
      console.log(`⚡ INSTANT: ${row.symbol} ${row.strike}${row.type} @ ${row.timestamp}`);
      
      // === RAW REAL-TIME TRADE DATA ===
      console.log('\n🔴 LIVE RAW TRADE FROM FLOW TABLE:');
      console.log('===================================');
      console.log(`Symbol: ${row.symbol}`);
      console.log(`Instrument ID: ${row.instrument_id}`);
      console.log(`Price: $${row.price}`);
      console.log(`Size: ${row.size}`);
      console.log(`Timestamp: ${row.timestamp}`);
      console.log(`Exchange: ${row.exchange}`);
      console.log(`Conditions: ${row.conditions}`);
      console.log(`Complete Raw Data:`, JSON.stringify(row, null, 2));
      console.log('===================================\n');
      
      // Transform and emit immediately (skip external API calls for speed)
      const transformedTrade = await transformTradeDataFast(row);
      io.emit('newTrade', transformedTrade);
      
      // Update tracking
      lastProcessedTimestamp = row.timestamp;
      
      return 1;
    }
    
    return 0;
    
  } catch (error) {
    console.error('Error in instant trade check:', error);
    return 0;
  }
}

/**
 * Optimized function to check for new trades with minimal latency
 * Returns true if a trade was found and processed
 */
async function checkForNewTradesOptimized(io) {
  try {
    // Ultra-fast query to get just the next trade after our last timestamp (ANY DATE)
    // Exclude index options (symbol format: "SPXW 241014C06580000") - case-insensitive
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
      WHERE ts_recv > '${lastProcessedTimestamp}'
        AND upper(symbol) NOT LIKE 'SPXW %'
        AND upper(symbol) NOT LIKE 'XSP %'
        AND upper(symbol) NOT LIKE 'SPX %'
        AND upper(symbol) NOT LIKE 'VIX %'
        AND upper(symbol) NOT LIKE 'VIXW %'
        AND upper(symbol) NOT LIKE 'NDX %'
      ORDER BY ts_recv ASC 
      LIMIT 1
    `;

    const result = await clickhouse.query({
      query: query,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_execution_time: 1, // Very short timeout for speed
        max_threads: 1,
        max_block_size: 1
      }
    });

    const rows = await result.json();
    
    if (rows.length > 0) {
      const row = rows[0];
      
      // Process and emit immediately
      console.log(`⚡ REALTIME: ${row.symbol} ${row.strike}${row.type} @ ${row.timestamp}`);
      
      const transformedTrade = await transformTradeData(row);
      io.emit('newTrade', transformedTrade);
      
      // Update timestamp for next query
      lastProcessedTimestamp = row.timestamp;
      
      return true; // Found and processed a trade
    }
    
    return false; // No new trades found
    
  } catch (error) {
    console.error('Error in optimized trade check:', error);
    return false;
  }
}

/**
 * Check for new trades in the database (fallback polling method)
 */
async function checkForNewTrades(io) {
  try {
    // Query for trades newer than our last processed timestamp (ANY DATE)
    // Exclude index options (symbol format: "SPXW 241014C06580000") - case-insensitive
    const timestampFilter = `ts_recv > '${lastProcessedTimestamp}' AND upper(symbol) NOT LIKE 'SPXW %' AND upper(symbol) NOT LIKE 'XSP %' AND upper(symbol) NOT LIKE 'SPX %' AND upper(symbol) NOT LIKE 'VIX %' AND upper(symbol) NOT LIKE 'VIXW %' AND upper(symbol) NOT LIKE 'NDX %'`;
    
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
      ORDER BY ts_recv ASC 
      LIMIT 1
    `;

    const result = await clickhouse.query({
      query: query,
      format: 'JSONEachRow',
      clickhouse_settings: {
        max_execution_time: 5,
        max_threads: 1
      }
    });

    const rows = await result.json();
    
    if (rows.length > 0) {
      const row = rows[0]; // We only get 1 trade at a time now
      
      try {
        console.log(`Processing new trade: ${row.symbol} at ${row.timestamp}`);
        
        const transformedTrade = await transformTradeData(row);
        
        // Emit the new trade to all connected clients immediately
        io.emit('newTrade', transformedTrade);
        
        // Update our last processed timestamp to this trade's timestamp
        lastProcessedTimestamp = row.timestamp;
        
        console.log(`Broadcasted trade: ${row.symbol} ${row.strike} ${row.type} - ${row.size} @ $${row.price}`);
        
      } catch (error) {
        console.error(`Error processing trade ${row.symbol}:`, error.message);
      }
    }
    
  } catch (error) {
    console.error('Error in checkForNewTrades:', error);
  }
}

/**
 * Transform raw database trade data into frontend format (simplified)
 */
async function transformTradeData(row) {
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
    index: `realtime_${Date.now()}`,
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
}

/**
 * Get monitoring status
 */
function getMonitoringStatus() {
  return {
    isMonitoring,
    lastProcessedTimestamp,
    intervalMs: monitoringInterval ? 500 : null
  };
}

module.exports = {
  startFlowMonitoring,
  stopFlowMonitoring,
  getMonitoringStatus
};
