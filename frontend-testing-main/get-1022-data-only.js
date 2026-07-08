import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

/**
 * Get comprehensive premium data for 10/22/2025 using API spec endpoints
 * @param {string} ticker - Stock ticker (default: META)
 * @returns {Promise<Object>} Comprehensive premium data for 10/22
 */
async function getComprehensiveDataFor1022(ticker = 'META') {
  try {
    console.log(`📊 Fetching Comprehensive Premium Data for ${ticker} on 10/22/2025`);
    console.log('Using API Spec Endpoints:');
    console.log('1. /api/screener/stocks - for total premiums');
    console.log('2. /api/market/top-net-impact - for net premium');
    console.log('3. /api/stock/{ticker}/net-prem-ticks - for net call/put premiums');
    console.log('='.repeat(80));
    
    const date = '2025-10-22';
    
    // 1. Get total premium data from stock screener endpoint (API spec)
    console.log('🔄 Fetching total premiums from stock screener...');
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
    console.log('🔄 Fetching net premium from top-net-impact...');
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
    console.log('🔄 Fetching net call/put premiums from net-prem-ticks...');
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

    console.log('\n📊 COMPREHENSIVE PREMIUM DATA FOR 10/22/2025:');
    console.log('='.repeat(60));
    console.log(`Ticker: ${ticker.toUpperCase()}`);
    console.log(`Date: ${date}`);
    console.log(`Total Ticks: ${totalTicks}`);
    
    console.log('\n💰 TOTAL PREMIUMS (from stock screener):');
    console.log(`Call Premium: $${(totalCallPremium / 1000000).toFixed(2)}M`);
    console.log(`Put Premium: $${(totalPutPremium / 1000000).toFixed(2)}M`);
    
    console.log('\n📈 NET PREMIUMS (from net-prem-ticks):');
    console.log(`NCP (Net Call Premium): $${(netCallPremium / 1000000).toFixed(2)}M`);
    console.log(`NPP (Net Put Premium): $${(netPutPremium / 1000000).toFixed(2)}M`);
    console.log(`Calculated Net Premium (NCP - |NPP|): $${(calculatedNetPremium / 1000000).toFixed(2)}M`);
    
    console.log('\n🎯 NET PREMIUM (from top-net-impact):');
    console.log(`Net Premium: $${(netPremiumFromTopImpact / 1000000).toFixed(2)}M`);
    
    console.log('\n✅ FINAL RESULTS FOR 10/22/2025:');
    console.log('='.repeat(40));
    console.log(`NCP: $${(netCallPremium / 1000000).toFixed(2)}M`);
    console.log(`NPP: $${(netPutPremium / 1000000).toFixed(2)}M`);
    console.log(`Call Premium: $${(totalCallPremium / 1000000).toFixed(2)}M`);
    console.log(`Put Premium: $${(totalPutPremium / 1000000).toFixed(2)}M`);
    console.log(`Net Premium: $${(calculatedNetPremium / 1000000).toFixed(2)}M`);
    
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
      // Net Premium (from top-net-impact - API spec)
      netPremiumFromTopImpact: Math.round(netPremiumFromTopImpact),
      totalTicks: totalTicks,
      success: true
    };
    
  } catch (error) {
    console.error(`❌ Error fetching data for 10/22:`, error.message);
    return {
      date: '2025-10-22',
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

// Run the function for 10/22 only
getComprehensiveDataFor1022('META');
