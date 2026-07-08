const axios = require('axios');

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

/**
 * Get the last N trading days (excluding weekends)
 * @param {number} days - Number of trading days to go back
 * @returns {Array} Array of date strings in YYYY-MM-DD format
 */
function getLastNTradingDays(days) {
  const tradingDays = [];
  const today = new Date();
  
  // Start from today and go backwards
  let currentDate = new Date(today);
  let tradingDaysFound = 0;
  
  while (tradingDaysFound < days) {
    // Check if it's a weekday (Monday = 1, Sunday = 0)
    const dayOfWeek = currentDate.getDay();
    if (dayOfWeek >= 1 && dayOfWeek <= 5) { // Monday to Friday
      tradingDays.push(currentDate.toISOString().split('T')[0]);
      tradingDaysFound++;
    }
    
    // Go back one day
    currentDate.setDate(currentDate.getDate() - 1);
  }
  
  return tradingDays;
}

/**
 * Get comprehensive premium data for a specific ticker and date using API spec endpoints
 * @param {string} ticker - Stock ticker
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Object>} Comprehensive premium data
 */
async function getComprehensivePremiumData(ticker, date) {
  try {
    console.log(`🔄 Fetching comprehensive premium data for ${ticker} on ${date}...`);
    
    // 1. Get total premium data from stock screener endpoint (API spec)
    const screenerUrl = `${UNUSUAL_WHALES_API_URL}/screener/stocks`;
    const screenerResponse = await axios.get(screenerUrl, {
      params: { 
        ticker: ticker,
        date: date
      },
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    // 2. Get net premium ticks data (API spec)
    const netPremUrl = `${UNUSUAL_WHALES_API_URL}/stock/${ticker}/net-prem-ticks`;
    const netPremResponse = await axios.get(netPremUrl, {
      params: { date },
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let netCallPremium = 0;
    let netPutPremium = 0;
    let totalTicks = 0;

    // Process total premium data from stock screener (API spec)
    if (screenerResponse.data?.data?.length > 0) {
      const screenerData = screenerResponse.data.data;
      const tickerData = screenerData.find(item => item.ticker === ticker.toUpperCase());
      
      if (tickerData) {
        totalCallPremium = parseFloat(tickerData.call_premium) || 0;
        totalPutPremium = parseFloat(tickerData.put_premium) || 0;
      }
    }


    // Process net premium ticks data (API spec) - using the working method
    if (netPremResponse.data?.data?.length > 0) {
      const ticks = netPremResponse.data.data;
      
      // Following the API spec cumulative approach
      const fieldsToSum = [
        "net_call_premium",
        "net_call_volume", 
        "net_put_premium",
        "net_put_volume"
      ];
      
      let result = [];
      ticks.forEach((tick, idx) => {
        const parsedTick = {
          ...tick,
          net_call_premium: parseFloat(tick.net_call_premium),
          net_put_premium: parseFloat(tick.net_put_premium),
          net_call_volume: parseFloat(tick.net_call_volume),
          net_put_volume: parseFloat(tick.net_put_volume)
        };
        
        if (idx !== 0) {
          fieldsToSum.forEach((field) => {
            parsedTick[field] = parsedTick[field] + result[idx-1][field];
          });
        }
        
        result.push(parsedTick);
      });
      
      const lastTick = result[result.length - 1];
      netCallPremium = lastTick.net_call_premium;
      netPutPremium = lastTick.net_put_premium;
      totalTicks = ticks.length;
    }

    // Calculate net premium using the working formula: NCP - |NPP|
    const calculatedNetPremium = netCallPremium - Math.abs(netPutPremium);

    return {
      date,
      ticker: ticker.toUpperCase(),
      // Total Premiums (from stock screener - API spec)
      totalCallPremium: Math.round(totalCallPremium),
      totalPutPremium: Math.round(totalPutPremium),
      // Net Premiums (from net-prem-ticks - API spec)
      netCallPremium: Math.round(netCallPremium),
      netPutPremium: Math.round(netPutPremium),
      calculatedNetPremium: Math.round(calculatedNetPremium),
      totalTicks: totalTicks,
      success: true
    };
    
  } catch (error) {
    console.error(`❌ Error fetching data for ${date}:`, error.message);
    return {
      date,
      ticker: ticker.toUpperCase(),
      totalCallPremium: 0,
      totalPutPremium: 0,
      netCallPremium: 0,
      netPutPremium: 0,
      calculatedNetPremium: 0,
      totalTicks: 0,
      success: false,
      error: error.message
    };
  }
}

/**
 * Get comprehensive premium data for the last N trading sessions
 * @param {string} ticker - Stock ticker (default: META)
 * @param {number} days - Number of trading days (default: 15)
 * @returns {Promise<Array>} Array of comprehensive premium data
 */
async function getLastNTradingSessionsComprehensive(ticker = 'META', days = 15) {
  try {
    console.log(`📊 Fetching Comprehensive Premium Data for ${ticker} - Last ${days} Trading Sessions`);
    console.log('Using API Spec Endpoints:');
    console.log('1. /api/screener/stocks - for total premiums');
    console.log('2. /api/market/top-net-impact - for net premium');
    console.log('3. /api/stock/{ticker}/net-prem-ticks - for net call/put premiums');
    console.log('='.repeat(80));
    
    const tradingDays = getLastNTradingDays(days);
    console.log(`📅 Trading days to fetch: ${tradingDays.join(', ')}`);
    
    const results = [];
    
    for (let i = 0; i < tradingDays.length; i++) {
      const date = tradingDays[i];
      console.log(`\n🔄 Fetching data for ${date} (${i + 1}/${tradingDays.length})...`);
      
      const data = await getComprehensivePremiumData(ticker, date);
      results.push(data);
      
      // Add small delay to avoid rate limiting
      if (i < tradingDays.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 300));
      }
    }
    
    console.log('\n📈 COMPREHENSIVE PREMIUM DATA SUMMARY:');
    console.log('='.repeat(100));
    console.log('Date       | NCP (M)  | NPP (M)  | Call Prem (M) | Put Prem (M) | Net Prem (M) | Ticks | Status');
    console.log('-'.repeat(100));
    
    results.forEach(result => {
      const ncpM = (result.netCallPremium / 1000000).toFixed(2);
      const nppM = (result.netPutPremium / 1000000).toFixed(2);
      const callPremM = (result.totalCallPremium / 1000000).toFixed(2);
      const putPremM = (result.totalPutPremium / 1000000).toFixed(2);
      const netPremM = (result.calculatedNetPremium / 1000000).toFixed(2);
      const status = result.success ? '✅' : '❌';
      
      console.log(`${result.date} | ${ncpM.padStart(8)} | ${nppM.padStart(8)} | ${callPremM.padStart(13)} | ${putPremM.padStart(11)} | ${netPremM.padStart(11)} | ${result.totalTicks.toString().padStart(5)} | ${status}`);
    });
    
    // Show detailed breakdown for today (10/22)
    const todayData = results.find(r => r.date === '2025-10-22');
    if (todayData && todayData.success) {
      console.log('\n🔍 DETAILED BREAKDOWN FOR 10/22/2025:');
      console.log('='.repeat(50));
      console.log(`NCP (Net Call Premium): $${(todayData.netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`NPP (Net Put Premium): $${(todayData.netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Call Premium (Total): $${(todayData.totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Put Premium (Total): $${(todayData.totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: $${todayData.calculatedNetPremium.toLocaleString()}`);
      console.log(`Total Ticks: ${todayData.totalTicks}`);
    }
    
    return results;
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    return [];
  }
}

// Export functions for use in other modules
module.exports = {
  getComprehensivePremiumData,
  getLastNTradingSessionsComprehensive,
  getLastNTradingDays
};

// Run the function if this file is executed directly
if (require.main === module) {
  getLastNTradingSessionsComprehensive('META', 15);
}
