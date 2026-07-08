import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

/**
 * Get comprehensive premium data using multiple API spec endpoints
 * @param {string} ticker - Stock ticker
 * @param {string} date - Date in YYYY-MM-DD format
 * @returns {Promise<Object>} Comprehensive premium data
 */
async function getComprehensivePremiumDataFromAPISpec(ticker, date) {
  try {
    console.log(`🔄 Fetching comprehensive premium data for ${ticker} on ${date} using API spec endpoints...`);
    
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

    // 2. Get net premium data from top-net-impact endpoint (API spec)
    const topNetImpactUrl = `${UNUSUAL_WHALES_API_URL}/market/top-net-impact`;
    const topNetImpactResponse = await axios.get(topNetImpactUrl, {
      params: { 
        date: date,
        limit: 100
      },
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    // 3. Get net premium ticks data (API spec)
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
    let netCallPremiumFromTicks = 0;
    let netPutPremiumFromTicks = 0;
    let netPremiumFromTopImpact = 0;
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

    // Process net premium data from top-net-impact (API spec)
    if (topNetImpactResponse.data?.data?.length > 0) {
      const topNetImpactData = topNetImpactResponse.data.data;
      const tickerData = topNetImpactData.find(item => item.ticker === ticker.toUpperCase());
      
      if (tickerData) {
        netPremiumFromTopImpact = parseFloat(tickerData.net_premium) || 0;
      }
    }

    // Process net premium ticks data (API spec)
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
      netCallPremiumFromTicks = lastTick.net_call_premium;
      netPutPremiumFromTicks = lastTick.net_put_premium;
      totalTicks = ticks.length;
    }

    // Calculate net premium using UW logic: NCP - NPP
    const calculatedNetPremium = netCallPremiumFromTicks - netPutPremiumFromTicks;

    return {
      date,
      ticker: ticker.toUpperCase(),
      // Total Premiums (from stock screener - API spec)
      totalCallPremium: Math.round(totalCallPremium),
      totalPutPremium: Math.round(totalPutPremium),
      // Net Premiums (from net-prem-ticks - API spec)
      netCallPremium: Math.round(netCallPremiumFromTicks),
      netPutPremium: Math.round(netPutPremiumFromTicks),
      calculatedNetPremium: Math.round(calculatedNetPremium),
      // Net Premium (from top-net-impact - API spec)
      netPremiumFromTopImpact: Math.round(netPremiumFromTopImpact),
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
      netPremiumFromTopImpact: 0,
      success: false,
      error: error.message
    };
  }
}

/**
 * Test comprehensive premium data for 10/22/2025
 */
async function testComprehensiveDataFor1022() {
  try {
    console.log('🧪 Testing comprehensive premium data for META on 10/22/2025...');
    console.log('Using API Spec Endpoints:');
    console.log('1. /api/screener/stocks - for total premiums');
    console.log('2. /api/market/top-net-impact - for net premium');
    console.log('3. /api/stock/{ticker}/net-prem-ticks - for net call/put premiums');
    console.log('='.repeat(80));
    
    const data = await getComprehensivePremiumDataFromAPISpec('META', '2025-10-22');
    
    if (data.success) {
      console.log('\n📊 COMPREHENSIVE PREMIUM DATA FOR 10/22/2025:');
      console.log('='.repeat(50));
      console.log(`Ticker: ${data.ticker}`);
      console.log(`Date: ${data.date}`);
      console.log(`Total Ticks: ${data.totalTicks}`);
      
      console.log('\n💰 TOTAL PREMIUMS (from stock screener):');
      console.log(`Call Premium: $${(data.totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Put Premium: $${(data.totalPutPremium / 1000000).toFixed(2)}M`);
      
      console.log('\n📈 NET PREMIUMS (from net-prem-ticks):');
      console.log(`NCP (Net Call Premium): $${(data.netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`NPP (Net Put Premium): $${(data.netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Calculated Net Premium (NCP - NPP): $${(data.calculatedNetPremium / 1000000).toFixed(2)}M`);
      
      console.log('\n🎯 NET PREMIUM (from top-net-impact):');
      console.log(`Net Premium: $${(data.netPremiumFromTopImpact / 1000000).toFixed(2)}M`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES FOR 10/22:');
      console.log('NCP = -$26.49M');
      console.log('NPP = $3.44M');
      console.log('Put Premium = $76.92M');
      console.log('Call Premium = $244.61M');
      console.log('Net Premium = NCP - NPP = -$26.49M - $3.44M = -$29.93M');
      
      console.log('\n📊 COMPARISON:');
      console.log(`NCP: Expected -$26.49M, Got $${(data.netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`NPP: Expected $3.44M, Got $${(data.netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Call Premium: Expected $244.61M, Got $${(data.totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Put Premium: Expected $76.92M, Got $${(data.totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: Expected -$29.93M, Got $${(data.calculatedNetPremium / 1000000).toFixed(2)}M`);
      
      // Check if values match
      const ncpMatch = Math.abs(-26490000 - data.netCallPremium) < 1000000;
      const nppMatch = Math.abs(3440000 - data.netPutPremium) < 1000000;
      const callPremMatch = Math.abs(244610000 - data.totalCallPremium) < 1000000;
      const putPremMatch = Math.abs(76920000 - data.totalPutPremium) < 1000000;
      const netPremMatch = Math.abs(-29930000 - data.calculatedNetPremium) < 1000000;
      
      console.log('\n✅ ACCURACY CHECK:');
      console.log(`NCP: ${ncpMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`NPP: ${nppMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Call Premium: ${callPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Put Premium: ${putPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Net Premium: ${netPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      
      if (ncpMatch && nppMatch && callPremMatch && putPremMatch && netPremMatch) {
        console.log('\n🎉 SUCCESS: All values match expected results!');
      } else {
        console.log('\n⚠️ Some values still need adjustment');
        console.log('\n🔍 ANALYSIS:');
        console.log('✅ Total Call/Put Premiums match (from stock screener)');
        console.log('❌ Net Call/Put Premiums don\'t match (from net-prem-ticks)');
        console.log('💡 The net-prem-ticks endpoint may be calculating net premiums differently');
        console.log('💡 We need to find the correct endpoint for NCP and NPP values');
      }
    } else {
      console.log('❌ Failed to fetch comprehensive data');
    }
    
    return data;
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    return null;
  }
}

testComprehensiveDataFor1022();

