const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get total call and put premiums for a ticker using Options Chain Snapshot API
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<object>} - Total premiums data
 */
async function getTotalOptionsPremiums(ticker, date) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Calculating total options premiums for ${ticker} on ${date}...`);
    
    let allOptions = [];
    let nextUrl = null;
    let pageCount = 0;
    const maxPages = 200; // Increased limit to get more contracts

    // First request
    const initialResponse = await axios.get(`https://api.polygon.io/v3/snapshot/options/${ticker}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        limit: 100 // Maximum limit per request
      }
    });

    if (initialResponse.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${initialResponse.data.status}`);
    }

    allOptions = allOptions.concat(initialResponse.data.results || []);
    nextUrl = initialResponse.data.next_url;
    pageCount++;

    console.log(`Page ${pageCount}: Found ${initialResponse.data.results?.length || 0} options`);

    // Paginate through all results
    while (nextUrl && pageCount < maxPages) {
      try {
        console.log(`Fetching page ${pageCount + 1}...`);
        const response = await axios.get(nextUrl);
        
        if (response.data.status !== 'OK') {
          console.log(`API error on page ${pageCount + 1}: ${response.data.status}`);
          break;
        }

        const newOptions = response.data.results || [];
        if (newOptions.length === 0) {
          console.log(`No more options found on page ${pageCount + 1}, stopping pagination`);
          break;
        }

        allOptions = allOptions.concat(newOptions);
        nextUrl = response.data.next_url;
        pageCount++;

        console.log(`Page ${pageCount}: Found ${newOptions.length} options (Total: ${allOptions.length})`);

        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 200));

      } catch (error) {
        console.error(`Error fetching page ${pageCount + 1}:`, error.message);
        break;
      }
    }

    console.log(`Total options fetched: ${allOptions.length}`);

    if (allOptions.length === 0) {
      return {
        ticker,
        date,
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalContracts: 0,
        callContracts: 0,
        putContracts: 0
      };
    }

    // Calculate totals using day.close * day.volume * 100 (shares per contract)
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;
    let callContracts = 0;
    let putContracts = 0;

    allOptions.forEach(option => {
      const details = option.details;
      const day = option.day;
      
      if (!details || !day) return;

      const closePrice = parseFloat(day.close || 0) || 0;
      const volume = parseInt(day.volume || 0) || 0;
      
      // Calculate total premium (close price * volume * 100 shares per contract)
      const totalPremium = closePrice * volume * 100;

      if (details.contract_type === 'call') {
        totalCallPremium += totalPremium;
        totalCallVolume += volume;
        callContracts++;
      } else if (details.contract_type === 'put') {
        totalPutPremium += totalPremium;
        totalPutVolume += volume;
        putContracts++;
      }
    });

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Total Options Premiums Summary for ${date}:`);
    console.log(`- Total Contracts: ${allOptions.length}`);
    console.log(`- Call Contracts: ${callContracts}`);
    console.log(`- Put Contracts: ${putContracts}`);
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
      totalContracts: allOptions.length,
      callContracts,
      putContracts
    };

  } catch (error) {
    console.error(`Error calculating total options premiums for ${ticker}:`, error.message);
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
        total_contracts UInt32,
        call_contracts UInt32,
        put_contracts UInt32,
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

module.exports = {
  getTotalOptionsPremiums,
  storeDailyPremiums
};
