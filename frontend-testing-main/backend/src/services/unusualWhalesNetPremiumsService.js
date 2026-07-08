const axios = require('axios');

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = process.env.UNUSUAL_WHALES_API_KEY;

/**
 * Get daily net premiums for a ticker using Unusual Whales API
 * @param {string} ticker - Stock ticker symbol (e.g., 'META')
 * @param {string} date - Date in YYYY-MM-DD format (optional, defaults to today)
 * @returns {Promise<object>} - Daily net premiums data
 */
async function getDailyNetPremiums(ticker, date = null) {
  try {
    if (!UNUSUAL_WHALES_API_KEY) {
      throw new Error('UW_API_KEY is not set in environment variables');
    }

    // Use today's date if not provided
    const targetDate = date || new Date().toISOString().split('T')[0];
    
    const url = `${UNUSUAL_WHALES_API_URL}/stock/${ticker}/net-prem-ticks`;
    const params = { date: targetDate };
    
    console.log(`🔄 Fetching daily net premiums for ${ticker} on ${targetDate} from Unusual Whales...`);
    console.log(`🔗 URL: ${url}`);
    console.log(`📅 Date: ${targetDate}`);

    const response = await axios.get(url, {
      params,
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    console.log(`📊 Raw response status: ${response.status}`);
    console.log(`📊 Response data keys:`, Object.keys(response.data || {}));
    
    if (!response.data || !response.data.data || !Array.isArray(response.data.data)) {
      console.error('❌ Invalid response format:', JSON.stringify(response.data, null, 2));
      throw new Error('Invalid response format from Unusual Whales API');
    }

    const ticks = response.data.data;
    console.log(`📊 Ticks received: ${ticks.length}`);
    
    if (ticks.length === 0) {
      console.log(`⚠️ No data found for ${ticker} on ${targetDate}`);
      return {
        ticker,
        date: targetDate,
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalTicks: 0,
        dataSource: 'unusual_whales',
        error: 'No data available for this date'
      };
    }

    // Debug: Log the first few and last few ticks to understand the data
    console.log(`📊 Debug: Total ticks received: ${ticks.length}`);
    console.log(`📊 Debug: First tick:`, JSON.stringify(ticks[0], null, 2));
    console.log(`📊 Debug: Last tick:`, JSON.stringify(ticks[ticks.length - 1], null, 2));
    
    // The data is INCREMENTAL - we need to build cumulative totals as per API spec
    // Following the official documentation approach
    const fieldsToSum = [
      "net_call_premium",
      "net_call_volume", 
      "net_put_premium",
      "net_put_volume"
    ];
    
    let result = [];
    ticks.forEach((tick, idx) => {
      // Parse the values
      const parsedTick = {
        ...tick,
        net_call_premium: parseFloat(tick.net_call_premium),
        net_put_premium: parseFloat(tick.net_put_premium),
        net_call_volume: parseFloat(tick.net_call_volume),
        net_put_volume: parseFloat(tick.net_put_volume)
      };
      
      // Add to previous cumulative values (except for first tick)
      if (idx !== 0) {
        fieldsToSum.forEach((field) => {
          parsedTick[field] = parsedTick[field] + result[idx-1][field];
        });
      }
      
      result.push(parsedTick);
    });
    
    // Get the final cumulative values from the last tick
    const lastTick = result[result.length - 1];
    const netCallPremium = lastTick.net_call_premium;
    const netPutPremium = lastTick.net_put_premium;
    const totalCallVolume = Math.abs(lastTick.net_call_volume);
    const totalPutVolume = Math.abs(lastTick.net_put_volume);
    
    // Calculate net premium (calls - puts)
    // Net premium = net_call_premium - |net_put_premium|
    const netPremium = netCallPremium - Math.abs(netPutPremium);

    // For total premium, we need to use absolute values of net premiums
    const totalCallPremium = Math.abs(netCallPremium);
    const totalPutPremium = Math.abs(netPutPremium);
    const totalPremium = totalCallPremium + totalPutPremium;
    
    console.log(`✅ ${ticker} Daily Net Premiums Summary for ${targetDate}:`);
    console.log(`   - Total Ticks: ${ticks.length}`);
    console.log(`   - Net Call Premium: $${netCallPremium.toLocaleString()}`);
    console.log(`   - Net Put Premium: $${netPutPremium.toLocaleString()}`);
    console.log(`   - Total Call Premium: $${totalCallPremium.toLocaleString()}`);
    console.log(`   - Total Put Premium: $${totalPutPremium.toLocaleString()}`);
    console.log(`   - Total Premium: $${totalPremium.toLocaleString()}`);
    console.log(`   - Net Premium: $${netPremium.toLocaleString()}`);

    return {
      ticker: ticker.toUpperCase(),
      date: targetDate,
      netCallPremium: Math.round(netCallPremium),
      netPutPremium: Math.round(netPutPremium),
      totalCallPremium: Math.round(totalCallPremium),
      totalPutPremium: Math.round(totalPutPremium),
      totalPremium: Math.round(totalPremium),
      netPremium: Math.round(netPremium),
      totalCallVolume: Math.round(totalCallVolume),
      totalPutVolume: Math.round(totalPutVolume),
      totalTicks: ticks.length,
      dataSource: 'unusual_whales',
      lastTickTime: ticks[ticks.length - 1].tape_time,
      success: true
    };

  } catch (error) {
    console.error(`❌ Error fetching daily net premiums for ${ticker}:`, error.message);
    
    // Return error response
    return {
      ticker: ticker.toUpperCase(),
      date: date || new Date().toISOString().split('T')[0],
      totalCallPremium: 0,
      totalPutPremium: 0,
      netPremium: 0,
      totalCallVolume: 0,
      totalPutVolume: 0,
      totalTicks: 0,
      dataSource: 'unusual_whales',
      error: error.message,
      success: false
    };
  }
}

/**
 * Get multiple days of net premiums data
 * @param {string} ticker - Stock ticker symbol
 * @param {number} days - Number of days to fetch
 * @returns {Promise<Array>} - Array of daily net premiums data
 */
async function getMultipleDaysNetPremiums(ticker, days = 5) {
  try {
    const results = [];
    const today = new Date();
    
    for (let i = 0; i < days; i++) {
      const date = new Date(today);
      date.setDate(date.getDate() - i);
      const dateStr = date.toISOString().split('T')[0];
      
      const dayData = await getDailyNetPremiums(ticker, dateStr);
      results.push(dayData);
      
      // Add small delay to avoid rate limiting
      if (i < days - 1) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
    }
    
    return results;
  } catch (error) {
    console.error(`Error fetching multiple days net premiums for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = {
  getDailyNetPremiums,
  getMultipleDaysNetPremiums
};
