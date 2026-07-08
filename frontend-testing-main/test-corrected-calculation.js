import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testCorrectedCalculation() {
  try {
    console.log('🧪 Testing corrected premium calculation...');
    
    const url = `${UNUSUAL_WHALES_API_URL}/stock/META/net-prem-ticks`;
    const params = { date: '2025-10-22' };
    
    console.log(`🔗 URL: ${url}`);
    console.log(`📅 Date: 2025-10-22`);
    
    const response = await axios.get(url, {
      params,
      headers: {
        'Accept': 'application/json',
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`
      }
    });

    console.log('✅ Response received');
    console.log(`📊 Status: ${response.status}`);
    console.log(`📊 Data length: ${response.data?.data?.length || 'No data'}`);
    
    if (response.data?.data?.length > 0) {
      const ticks = response.data.data;
      
      // Sum all ticks to get the total premium for the day
      let totalCallPremium = 0;
      let totalPutPremium = 0;
      let totalCallVolume = 0;
      let totalPutVolume = 0;
      
      ticks.forEach(tick => {
        totalCallPremium += parseFloat(tick.net_call_premium) || 0;
        totalPutPremium += parseFloat(tick.net_put_premium) || 0;
        totalCallVolume += parseFloat(tick.net_call_volume) || 0;
        totalPutVolume += parseFloat(tick.net_put_volume) || 0;
      });
      
      // Calculate net premium (calls - puts)
      const netPremium = totalCallPremium - Math.abs(totalPutPremium);
      
      console.log('\n📈 CORRECTED CALCULATION:');
      console.log(`Total ticks: ${ticks.length}`);
      console.log(`Total Call Premium: $${totalCallPremium.toLocaleString()}`);
      console.log(`Total Put Premium: $${Math.abs(totalPutPremium).toLocaleString()}`);
      console.log(`Net Premium: $${netPremium.toLocaleString()}`);
      console.log(`Total Call Volume: ${totalCallVolume.toLocaleString()}`);
      console.log(`Total Put Volume: ${Math.abs(totalPutVolume).toLocaleString()}`);
      
      console.log('\n🎯 EXPECTED vs ACTUAL:');
      console.log(`Expected Total Premium: $321,530,573`);
      console.log(`Actual Total Premium: $${netPremium.toLocaleString()}`);
      console.log(`Difference: $${(321530573 - netPremium).toLocaleString()}`);
      
      if (Math.abs(321530573 - netPremium) < 1000) {
        console.log('✅ SUCCESS: Values match within acceptable range!');
      } else {
        console.log('❌ MISMATCH: Values do not match expected total');
      }
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testCorrectedCalculation();

