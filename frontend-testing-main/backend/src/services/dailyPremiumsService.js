const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get all options trades for a specific date and calculate total premiums
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<object>} - Daily premiums data
 */
async function getDailyPremiums(ticker, date) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Fetching all options trades for ${ticker} on ${date}...`);
    
    let allTrades = [];
    let nextUrl = null;
    let pageCount = 0;
    const maxPages = 100; // Safety limit

    // First request
    const initialResponse = await axios.get(`https://api.polygon.io/v3/trades/${ticker}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        timestamp: date,
        limit: 50000 // Maximum limit per request
      }
    });

    if (initialResponse.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${initialResponse.data.status}`);
    }

    allTrades = allTrades.concat(initialResponse.data.results || []);
    nextUrl = initialResponse.data.next_url;
    pageCount++;

    console.log(`Page ${pageCount}: Found ${initialResponse.data.results?.length || 0} trades`);

    // Paginate through all results
    while (nextUrl && pageCount < maxPages) {
      try {
        const response = await axios.get(nextUrl);
        
        if (response.data.status !== 'OK') {
          console.log(`API error on page ${pageCount + 1}: ${response.data.status}`);
          break;
        }

        const newTrades = response.data.results || [];
        allTrades = allTrades.concat(newTrades);
        nextUrl = response.data.next_url;
        pageCount++;

        console.log(`Page ${pageCount}: Found ${newTrades.length} trades (Total: ${allTrades.length})`);

        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 100));

      } catch (error) {
        console.error(`Error fetching page ${pageCount + 1}:`, error.message);
        break;
      }
    }

    console.log(`Total trades fetched: ${allTrades.length}`);

    // Filter for options trades only (ticker starts with 'O:')
    const optionsTrades = allTrades.filter(trade => 
      trade.ticker && trade.ticker.startsWith('O:')
    );

    console.log(`Options trades: ${optionsTrades.length}`);

    if (optionsTrades.length === 0) {
      return {
        ticker,
        date,
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalTrades: 0,
        optionsTrades: 0
      };
    }

    // Calculate totals
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;

    optionsTrades.forEach(trade => {
      const price = parseFloat(trade.price || 0) || 0;
      const size = parseInt(trade.size || 0) || 0;
      
      // Calculate total premium (price * size * 100 shares per contract)
      const totalPremium = price * size * 100;
      
      // Determine if it's a call or put based on ticker
      const isCall = trade.ticker.includes('C');
      const isPut = trade.ticker.includes('P');
      
      if (isCall) {
        totalCallPremium += totalPremium;
        totalCallVolume += size;
      } else if (isPut) {
        totalPutPremium += totalPremium;
        totalPutVolume += size;
      }
    });

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Daily Premiums Summary for ${date}:`);
    console.log(`- Total Trades: ${allTrades.length}`);
    console.log(`- Options Trades: ${optionsTrades.length}`);
    console.log(`- Call Premium: $${totalCallPremium.toLocaleString()}`);
    console.log(`- Put Premium: $${totalPutPremium.toLocaleString()}`);
    console.log(`- Net Premium: $${netPremium.toLocaleString()}`);
    console.log(`- Call Volume: ${totalCallVolume.toLocaleString()}`);
    console.log(`- Put Volume: ${totalPutVolume.toLocaleString()}`);

    return {
      ticker,
      date,
      totalCallPremium: Math.round(totalCallPremium),
      totalPutPremium: Math.round(totalPutPremium),
      netPremium: Math.round(netPremium),
      totalCallVolume,
      totalPutVolume,
      totalTrades: allTrades.length,
      optionsTrades: optionsTrades.length
    };

  } catch (error) {
    console.error(`Error fetching daily premiums for ${ticker} on ${date}:`, error.message);
    throw error;
  }
}

/**
 * Store daily premiums data in ClickHouse
 * @param {object} premiumsData - Daily premiums data
 * @returns {Promise<boolean>} - Success status
 */
async function storeDailyPremiums(premiumsData) {
  try {
    const { createClient } = require('@clickhouse/client');
    
    const clickhouse = createClient({
      url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
      username: process.env.CLICKHOUSE_USER || 'default',
      password: process.env.CLICKHOUSE_PASSWORD || '',
      database: process.env.CLICKHOUSE_DATABASE || 'options'
    });

    // Create table if it doesn't exist
    await clickhouse.exec(`
      CREATE TABLE IF NOT EXISTS daily_premiums (
        ticker String,
        date Date,
        total_call_premium Float64,
        total_put_premium Float64,
        net_premium Float64,
        total_call_volume UInt64,
        total_put_volume UInt64,
        total_trades UInt32,
        options_trades UInt32,
        created_at DateTime DEFAULT now()
      ) ENGINE = MergeTree()
      ORDER BY (ticker, date)
    `);

    // Insert the data
    await clickhouse.insert({
      table: 'daily_premiums',
      values: [premiumsData]
    });

    console.log(`✅ Stored daily premiums for ${premiumsData.ticker} on ${premiumsData.date} in ClickHouse`);
    return true;

  } catch (error) {
    console.error('Error storing daily premiums in ClickHouse:', error.message);
    return false;
  }
}

/**
 * Get daily premiums from ClickHouse
 * @param {string} ticker - Stock ticker symbol
 * @param {number} days - Number of days to retrieve
 * @returns {Promise<object>} - Historical premiums data
 */
async function getHistoricalPremiums(ticker, days = 5) {
  try {
    const { createClient } = require('@clickhouse/client');
    
    const clickhouse = createClient({
      url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
      username: process.env.CLICKHOUSE_USER || 'default',
      password: process.env.CLICKHOUSE_PASSWORD || '',
      database: process.env.CLICKHOUSE_DATABASE || 'options'
    });

    const query = `
      SELECT 
        ticker,
        date,
        total_call_premium as calls,
        total_put_premium as puts,
        net_premium,
        total_call_volume as callVolume,
        total_put_volume as putVolume
      FROM daily_premiums 
      WHERE ticker = '${ticker}'
      ORDER BY date DESC 
      LIMIT ${days}
    `;

    const result = await clickhouse.query({ query });
    const data = await result.json();

    // Format for chart
    const chartData = data.data.map(row => ({
      name: new Date(row.date).toLocaleDateString('en-US', { month: 'numeric', day: 'numeric' }),
      calls: row.calls,
      puts: row.puts,
      callVolume: row.callVolume,
      putVolume: row.putVolume,
      netPremium: row.net_premium
    }));

    return {
      ticker,
      timeframe: `${days}D`,
      data: chartData.reverse(), // Oldest to newest
      totalCalls: data.data.reduce((sum, row) => sum + row.calls, 0),
      totalPuts: data.data.reduce((sum, row) => sum + row.puts, 0),
      netPremium: data.data.reduce((sum, row) => sum + row.net_premium, 0)
    };

  } catch (error) {
    console.error('Error getting historical premiums from ClickHouse:', error.message);
    return {
      ticker,
      timeframe: `${days}D`,
      data: [],
      totalCalls: 0,
      totalPuts: 0,
      netPremium: 0
    };
  }
}

module.exports = {
  getDailyPremiums,
  storeDailyPremiums,
  getHistoricalPremiums
};
