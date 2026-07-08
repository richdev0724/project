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
 * Get net premiums for a specific ticker and date
 * @param {string} ticker - Stock ticker
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Object>} Net premium data
 */
async function getNetPremiumsForDate(ticker, date) {
  try {
    const url = `${UNUSUAL_WHALES_API_URL}/stock/${ticker}/net-prem-ticks`;
    const params = { date };
    
    const response = await axios.get(url, {
      params,
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    if (response.data?.data?.length > 0) {
      const ticks = response.data.data;
      
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
      
      return {
        date,
        ticker: ticker.toUpperCase(),
        netCallPremium: lastTick.net_call_premium,
        netPutPremium: lastTick.net_put_premium,
        netPremium: lastTick.net_call_premium - Math.abs(lastTick.net_put_premium),
        totalTicks: ticks.length,
        success: true
      };
    } else {
      return {
        date,
        ticker: ticker.toUpperCase(),
        netCallPremium: 0,
        netPutPremium: 0,
        netPremium: 0,
        totalTicks: 0,
        success: false,
        error: 'No data available'
      };
    }
    
  } catch (error) {
    return {
      date,
      ticker: ticker.toUpperCase(),
      netCallPremium: 0,
      netPutPremium: 0,
      netPremium: 0,
      totalTicks: 0,
      success: false,
      error: error.message
    };
  }
}

/**
 * Get NCP and NPP for the last 10 trading sessions
 * @param {string} ticker - Stock ticker (default: META)
 * @param {number} days - Number of trading days (default: 10)
 */
async function getLastNTradingSessions(ticker = 'META', days = 10) {
  try {
    console.log(`📊 Fetching NCP and NPP for ${ticker} - Last ${days} Trading Sessions`);
    console.log('='.repeat(60));
    
    const tradingDays = getLastNTradingDays(days);
    console.log(`📅 Trading days to fetch: ${tradingDays.join(', ')}`);
    
    const results = [];
    
    for (let i = 0; i < tradingDays.length; i++) {
      const date = tradingDays[i];
      console.log(`\n🔄 Fetching data for ${date} (${i + 1}/${tradingDays.length})...`);
      
      const data = await getNetPremiumsForDate(ticker, date);
      results.push(data);
      
      // Add small delay to avoid rate limiting
      if (i < tradingDays.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 200));
      }
    }
    
    console.log('\n📈 RESULTS SUMMARY:');
    console.log('='.repeat(60));
    console.log('Date       | NCP (M)     | NPP (M)     | Net Prem (M) | Ticks | Status');
    console.log('-'.repeat(60));
    
    results.forEach(result => {
      const ncpM = (result.netCallPremium / 1000000).toFixed(2);
      const nppM = (result.netPutPremium / 1000000).toFixed(2);
      const netPremM = (result.netPremium / 1000000).toFixed(2);
      const status = result.success ? '✅' : '❌';
      
      console.log(`${result.date} | ${ncpM.padStart(10)} | ${nppM.padStart(10)} | ${netPremM.padStart(12)} | ${result.totalTicks.toString().padStart(5)} | ${status}`);
    });
    
    // Summary statistics
    const successfulResults = results.filter(r => r.success);
    if (successfulResults.length > 0) {
      const avgNCP = successfulResults.reduce((sum, r) => sum + r.netCallPremium, 0) / successfulResults.length;
      const avgNPP = successfulResults.reduce((sum, r) => sum + r.netPutPremium, 0) / successfulResults.length;
      const avgNetPrem = successfulResults.reduce((sum, r) => sum + r.netPremium, 0) / successfulResults.length;
      
      console.log('\n📊 AVERAGE VALUES:');
      console.log(`Average NCP: $${(avgNCP / 1000000).toFixed(2)}M`);
      console.log(`Average NPP: $${(avgNPP / 1000000).toFixed(2)}M`);
      console.log(`Average Net Premium: $${(avgNetPrem / 1000000).toFixed(2)}M`);
    }
    
    return results;
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    return [];
  }
}

// Run the function
getLastNTradingSessions('META', 10);

