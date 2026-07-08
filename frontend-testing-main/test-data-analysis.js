import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function analyzeDataStructure() {
  try {
    console.log('🔍 Analyzing data structure to understand premium calculation...');
    
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
      
      console.log('\n📊 SAMPLE DATA ANALYSIS:');
      console.log('First 5 ticks:');
      ticks.slice(0, 5).forEach((tick, i) => {
        console.log(`Tick ${i + 1}:`);
        console.log(`  Call Premium: ${tick.net_call_premium}`);
        console.log(`  Put Premium: ${tick.net_put_premium}`);
        console.log(`  Call Volume: ${tick.net_call_volume}`);
        console.log(`  Put Volume: ${tick.net_put_volume}`);
        console.log(`  Total Call Volume: ${tick.call_volume}`);
        console.log(`  Total Put Volume: ${tick.put_volume}`);
        console.log(`  Time: ${tick.tape_time}`);
        console.log('');
      });
      
      // Let's try different calculations
      console.log('\n🧮 DIFFERENT CALCULATION APPROACHES:');
      
      // Approach 1: Sum net premiums (what we tried)
      let totalNetCallPremium = 0;
      let totalNetPutPremium = 0;
      ticks.forEach(tick => {
        totalNetCallPremium += parseFloat(tick.net_call_premium) || 0;
        totalNetPutPremium += parseFloat(tick.net_put_premium) || 0;
      });
      
      // Approach 2: Sum absolute values of net premiums
      let totalAbsCallPremium = 0;
      let totalAbsPutPremium = 0;
      ticks.forEach(tick => {
        totalAbsCallPremium += Math.abs(parseFloat(tick.net_call_premium)) || 0;
        totalAbsPutPremium += Math.abs(parseFloat(tick.net_put_premium)) || 0;
      });
      
      // Approach 3: Sum total volumes (not net)
      let totalCallVolume = 0;
      let totalPutVolume = 0;
      ticks.forEach(tick => {
        totalCallVolume += parseFloat(tick.call_volume) || 0;
        totalPutVolume += parseFloat(tick.put_volume) || 0;
      });
      
      console.log(`Approach 1 - Sum net premiums:`);
      console.log(`  Net Call Premium: $${totalNetCallPremium.toLocaleString()}`);
      console.log(`  Net Put Premium: $${totalNetPutPremium.toLocaleString()}`);
      console.log(`  Total: $${(totalNetCallPremium + Math.abs(totalNetPutPremium)).toLocaleString()}`);
      
      console.log(`\nApproach 2 - Sum absolute net premiums:`);
      console.log(`  Abs Call Premium: $${totalAbsCallPremium.toLocaleString()}`);
      console.log(`  Abs Put Premium: $${totalAbsPutPremium.toLocaleString()}`);
      console.log(`  Total: $${(totalAbsCallPremium + totalAbsPutPremium).toLocaleString()}`);
      
      console.log(`\nApproach 3 - Total volumes:`);
      console.log(`  Total Call Volume: ${totalCallVolume.toLocaleString()}`);
      console.log(`  Total Put Volume: ${totalPutVolume.toLocaleString()}`);
      console.log(`  Total Volume: ${(totalCallVolume + totalPutVolume).toLocaleString()}`);
      
      console.log(`\n🎯 EXPECTED: $321,530,573`);
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

analyzeDataStructure();

