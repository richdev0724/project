import axios from 'axios';

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
 * Get comprehensive premium data using the stock screener endpoint from API spec
 * @param {string} ticker - Stock ticker
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Object>} Comprehensive premium data
 */
async function getComprehensivePremiumDataFromAPI(ticker, date) {
  try {
    console.log(`🔄 Fetching comprehensive premium data for ${ticker} on ${date} using stock screener endpoint...`);
    
    // Get net premium data from net-prem-ticks endpoint (as per API spec)
    const netPremUrl = `${UNUSUAL_WHALES_API_URL}/stock/${ticker}/net-prem-ticks`;
    const netPremResponse = await axios.get(netPremUrl, {
      params: { date },
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    // Get total premium data from stock screener endpoint (as per API spec)
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

    let netCallPremium = 0;
    let netPutPremium = 0;
    let netPremium = 0;
    let totalTicks = 0;

    // Process net premium data from net-prem-ticks (as per API spec)
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
      netPremium = netCallPremium - Math.abs(netPutPremium);
      totalTicks = ticks.length;
    }

    let totalCallPremium = 0;
    let totalPutPremium = 0;

    // Process total premium data from stock screener (as per API spec)
    if (screenerResponse.data?.data?.length > 0) {
      const screenerData = screenerResponse.data.data;
      
      // Find the specific ticker in the screener results
      const tickerData = screenerData.find(item => item.ticker === ticker.toUpperCase());
      
      if (tickerData) {
        totalCallPremium = parseFloat(tickerData.call_premium) || 0;
        totalPutPremium = parseFloat(tickerData.put_premium) || 0;
      }
    }

    return {
      date,
      ticker: ticker.toUpperCase(),
      // Net Premiums (from net-prem-ticks endpoint per API spec)
      netCallPremium,
      netPutPremium,
      netPremium,
      totalTicks,
      // Total Premiums (from stock screener endpoint per API spec)
      totalCallPremium,
      totalPutPremium,
      success: true
    };
    
  } catch (error) {
    console.error(`❌ Error fetching data for ${date}:`, error.message);
    return {
      date,
      ticker: ticker.toUpperCase(),
      netCallPremium: 0,
      netPutPremium: 0,
      netPremium: 0,
      totalCallPremium: 0,
      totalPutPremium: 0,
      success: false,
      error: error.message
    };
  }
}

/**
 * Get comprehensive premium data for the last N trading sessions using API spec endpoints
 * @param {string} ticker - Stock ticker (default: META)
 * @param {number} days - Number of trading days (default: 10)
 */
async function getComprehensiveTradingSessionsFromAPI(ticker = 'META', days = 10) {
  try {
    console.log(`📊 Fetching Comprehensive Premium Data for ${ticker} - Last ${days} Trading Sessions`);
    console.log('Using API Spec Endpoints: /api/stock/{ticker}/net-prem-ticks and /api/screener/stocks');
    console.log('='.repeat(80));
    
    const tradingDays = getLastNTradingDays(days);
    console.log(`📅 Trading days to fetch: ${tradingDays.join(', ')}`);
    
    const results = [];
    
    for (let i = 0; i < tradingDays.length; i++) {
      const date = tradingDays[i];
      const data = await getComprehensivePremiumDataFromAPI(ticker, date);
      results.push(data);
      
      // Add small delay to avoid rate limiting
      if (i < tradingDays.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 300));
      }
    }
    
    console.log('\n📈 COMPREHENSIVE PREMIUM DATA SUMMARY:');
    console.log('='.repeat(80));
    console.log('Date       | NCP (M)  | NPP (M)  | Call Prem (M) | Put Prem (M) | Net Prem (M) | Status');
    console.log('-'.repeat(80));
    
    results.forEach(result => {
      const ncpM = (result.netCallPremium / 1000000).toFixed(2);
      const nppM = (result.netPutPremium / 1000000).toFixed(2);
      const callPremM = (result.totalCallPremium / 1000000).toFixed(2);
      const putPremM = (result.totalPutPremium / 1000000).toFixed(2);
      const netPremM = (result.netPremium / 1000000).toFixed(2);
      const status = result.success ? '✅' : '❌';
      
      console.log(`${result.date} | ${ncpM.padStart(8)} | ${nppM.padStart(8)} | ${callPremM.padStart(13)} | ${putPremM.padStart(11)} | ${netPremM.padStart(11)} | ${status}`);
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
      console.log(`Net Premium: $${todayData.netPremium.toLocaleString()}`);
      console.log(`Total Ticks: ${todayData.totalTicks}`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES FOR 10/22:');
      console.log('NCP = -$26.49M');
      console.log('NPP = $3.44M');
      console.log('Put Premium = $76.92M');
      console.log('Call Premium = $244.61M');
      console.log('Net Premium = -$10,464,052');
      
      console.log('\n📊 COMPARISON:');
      console.log(`NCP: Expected -$26.49M, Got $${(todayData.netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`NPP: Expected $3.44M, Got $${(todayData.netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Call Premium: Expected $244.61M, Got $${(todayData.totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Put Premium: Expected $76.92M, Got $${(todayData.totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: Expected -$10,464,052, Got $${todayData.netPremium.toLocaleString()}`);
    }
    
    return results;
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    return [];
  }
}

// Run the function using API spec endpoints
getComprehensiveTradingSessionsFromAPI('META', 10);

