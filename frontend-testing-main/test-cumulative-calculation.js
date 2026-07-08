import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testCumulativeCalculation() {
  try {
    console.log('🧪 Testing cumulative calculation as per API spec...');
    
    const url = `${UNUSUAL_WHALES_API_URL}/stock/META/net-prem-ticks`;
    const params = { date: '2025-10-22' };
    
    const response = await axios.get(url, {
      params,
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    if (response.data?.data?.length > 0) {
      const ticks = response.data.data;
      
      console.log(`📊 Processing ${ticks.length} ticks using cumulative approach...`);
      
      // Following the official API spec approach
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
      const totalCallPremium = Math.abs(lastTick.net_call_premium);
      const totalPutPremium = Math.abs(lastTick.net_put_premium);
      const totalCallVolume = Math.abs(lastTick.net_call_volume);
      const totalPutVolume = Math.abs(lastTick.net_put_volume);
      
      // Calculate net premium (calls - puts)
      const netPremium = lastTick.net_call_premium - Math.abs(lastTick.net_put_premium);
      const totalPremium = totalCallPremium + totalPutPremium;
      const totalVolume = totalCallVolume + totalPutVolume;
      
      console.log('\n📈 CUMULATIVE CALCULATION RESULTS:');
      console.log(`Total ticks processed: ${ticks.length}`);
      console.log(`Total Call Premium: $${totalCallPremium.toLocaleString()}`);
      console.log(`Total Put Premium: $${totalPutPremium.toLocaleString()}`);
      console.log(`Total Premium: $${totalPremium.toLocaleString()}`);
      console.log(`Net Premium: $${netPremium.toLocaleString()}`);
      console.log(`Total Volume: ${totalVolume.toLocaleString()}`);
      
      console.log('\n🎯 COMPARISON WITH EXPECTED VALUES:');
      console.log(`Expected Total Premium: $321,530,573`);
      console.log(`Actual Total Premium: $${totalPremium.toLocaleString()}`);
      console.log(`Difference: $${(321530573 - totalPremium).toLocaleString()}`);
      
      console.log(`\nExpected Net Premium: -$10,464,052`);
      console.log(`Actual Net Premium: $${netPremium.toLocaleString()}`);
      console.log(`Difference: $${(Math.abs(-10464052) - Math.abs(netPremium)).toLocaleString()}`);
      
      console.log(`\nExpected Total Volume: 237,000`);
      console.log(`Actual Total Volume: ${totalVolume.toLocaleString()}`);
      console.log(`Difference: ${(237000 - totalVolume).toLocaleString()}`);
      
      // Check accuracy
      const premiumAccuracy = Math.abs(321530573 - totalPremium) < 1000;
      const netAccuracy = Math.abs(-10464052 - netPremium) < 1000;
      const volumeAccuracy = Math.abs(237000 - totalVolume) < 1000;
      
      console.log('\n✅ ACCURACY CHECK:');
      console.log(`Total Premium: ${premiumAccuracy ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Net Premium: ${netAccuracy ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Total Volume: ${volumeAccuracy ? '✅ MATCH' : '❌ MISMATCH'}`);
      
      if (premiumAccuracy && netAccuracy && volumeAccuracy) {
        console.log('\n🎉 SUCCESS: All values match expected results!');
      } else {
        console.log('\n⚠️ Some values still need adjustment');
      }
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

testCumulativeCalculation();

