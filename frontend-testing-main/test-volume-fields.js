import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testVolumeFields() {
  try {
    console.log('🔍 Testing different volume field calculations...');
    
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
      
      console.log('\n📊 SAMPLE TICK ANALYSIS:');
      const sampleTick = ticks[0];
      console.log('Sample tick fields:');
      Object.keys(sampleTick).forEach(key => {
        console.log(`  ${key}: ${sampleTick[key]}`);
      });
      
      // Test different volume calculations
      let totalCallVolumeNet = 0;
      let totalPutVolumeNet = 0;
      let totalCallVolumeTotal = 0;
      let totalPutVolumeTotal = 0;
      
      ticks.forEach(tick => {
        totalCallVolumeNet += Math.abs(parseFloat(tick.net_call_volume)) || 0;
        totalPutVolumeNet += Math.abs(parseFloat(tick.net_put_volume)) || 0;
        totalCallVolumeTotal += parseFloat(tick.call_volume) || 0;
        totalPutVolumeTotal += parseFloat(tick.put_volume) || 0;
      });
      
      console.log('\n🧮 VOLUME CALCULATIONS:');
      console.log(`Total Call Volume (net): ${totalCallVolumeNet.toLocaleString()}`);
      console.log(`Total Put Volume (net): ${totalPutVolumeNet.toLocaleString()}`);
      console.log(`Total Call Volume (total): ${totalCallVolumeTotal.toLocaleString()}`);
      console.log(`Total Put Volume (total): ${totalPutVolumeTotal.toLocaleString()}`);
      console.log(`Combined Net Volume: ${(totalCallVolumeNet + totalPutVolumeNet).toLocaleString()}`);
      console.log(`Combined Total Volume: ${(totalCallVolumeTotal + totalPutVolumeTotal).toLocaleString()}`);
      
      console.log('\n🎯 EXPECTED VOLUME: 237,000');
      
      // Test premium calculations with different approaches
      console.log('\n🧮 PREMIUM CALCULATIONS:');
      
      // Approach 1: Sum absolute net premiums
      let totalCallPremiumAbs = 0;
      let totalPutPremiumAbs = 0;
      ticks.forEach(tick => {
        totalCallPremiumAbs += Math.abs(parseFloat(tick.net_call_premium)) || 0;
        totalPutPremiumAbs += Math.abs(parseFloat(tick.net_put_premium)) || 0;
      });
      
      // Approach 2: Sum raw net premiums (signed)
      let totalCallPremiumRaw = 0;
      let totalPutPremiumRaw = 0;
      ticks.forEach(tick => {
        totalCallPremiumRaw += parseFloat(tick.net_call_premium) || 0;
        totalPutPremiumRaw += parseFloat(tick.net_put_premium) || 0;
      });
      
      console.log(`Total Call Premium (abs): $${totalCallPremiumAbs.toLocaleString()}`);
      console.log(`Total Put Premium (abs): $${totalPutPremiumAbs.toLocaleString()}`);
      console.log(`Total Premium (abs): $${(totalCallPremiumAbs + totalPutPremiumAbs).toLocaleString()}`);
      
      console.log(`Total Call Premium (raw): $${totalCallPremiumRaw.toLocaleString()}`);
      console.log(`Total Put Premium (raw): $${totalPutPremiumRaw.toLocaleString()}`);
      console.log(`Net Premium (raw): $${(totalCallPremiumRaw - Math.abs(totalPutPremiumRaw)).toLocaleString()}`);
      
      console.log('\n🎯 EXPECTED VALUES:');
      console.log('Total Premium: $321,530,573');
      console.log('Net Premium: -$10,464,052');
      console.log('Total Volume: 237,000');
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

testVolumeFields();

