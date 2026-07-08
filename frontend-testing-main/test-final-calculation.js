import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testFinalCalculation() {
  try {
    console.log('🧪 Testing final corrected premium calculation...');
    
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
      
      // Sum all ticks to get the total premium for the day
      // Use absolute values to get TOTAL premium (not net)
      let totalCallPremium = 0;
      let totalPutPremium = 0;
      let totalCallVolume = 0;
      let totalPutVolume = 0;
      
      ticks.forEach(tick => {
        totalCallPremium += Math.abs(parseFloat(tick.net_call_premium)) || 0;
        totalPutPremium += Math.abs(parseFloat(tick.net_put_premium)) || 0;
        totalCallVolume += Math.abs(parseFloat(tick.net_call_volume)) || 0;
        totalPutVolume += Math.abs(parseFloat(tick.net_put_volume)) || 0;
      });
      
      // Calculate net premium (calls - puts) using original signed values
      let netCallPremium = 0;
      let netPutPremium = 0;
      ticks.forEach(tick => {
        netCallPremium += parseFloat(tick.net_call_premium) || 0;
        netPutPremium += parseFloat(tick.net_put_premium) || 0;
      });
      const netPremium = netCallPremium - Math.abs(netPutPremium);
      
      const totalPremium = totalCallPremium + totalPutPremium;
      const totalVolume = totalCallVolume + totalPutVolume;
      
      console.log('\n📈 FINAL CALCULATION RESULTS:');
      console.log(`Total ticks: ${ticks.length}`);
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
      
      if (Math.abs(321530573 - totalPremium) < 1000) {
        console.log('\n✅ SUCCESS: Total Premium matches within acceptable range!');
      } else {
        console.log('\n❌ MISMATCH: Total Premium does not match expected value');
      }
      
      if (Math.abs(-10464052 - netPremium) < 1000) {
        console.log('✅ SUCCESS: Net Premium matches within acceptable range!');
      } else {
        console.log('❌ MISMATCH: Net Premium does not match expected value');
      }
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

testFinalCalculation();

