import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testNetPremTicksExactAPISpec() {
  try {
    console.log('🧪 Testing net-prem-ticks endpoint using EXACT API spec method...');
    
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
      const data = response.data.data;
      
      console.log('\n📊 SAMPLE TICK DATA:');
      console.log('First 3 ticks:');
      data.slice(0, 3).forEach((tick, i) => {
        console.log(`\nTick ${i + 1}:`);
        console.log(`  Date: ${tick.date}`);
        console.log(`  Tape Time: ${tick.tape_time}`);
        console.log(`  Net Call Premium: ${tick.net_call_premium}`);
        console.log(`  Net Put Premium: ${tick.net_put_premium}`);
        console.log(`  Net Call Volume: ${tick.net_call_volume}`);
        console.log(`  Net Put Volume: ${tick.net_put_volume}`);
      });
      
      // Use EXACT method from API spec
      const fieldsToSum = [
        "net_call_premium",
        "net_call_volume",
        "net_put_premium",
        "net_put_volume"
      ];
    
      let result = [];
      data.forEach((e, idx) => {
        e.net_call_premium = parseFloat(e.net_call_premium);
        e.net_put_premium = parseFloat(e.net_put_premium);
        if (idx !== 0) {
          fieldsToSum.forEach((field) => {
            e[field] = e[field] + result[idx-1][field];
          });
        }
        result.push(e);
      });
      
      // Get the final cumulative values from the last tick
      const lastTick = result[result.length - 1];
      const netCallPremium = lastTick.net_call_premium;
      const netPutPremium = lastTick.net_put_premium;
      const netCallVolume = lastTick.net_call_volume;
      const netPutVolume = lastTick.net_put_volume;
      
      // Calculate net premium using UW logic: NCP - NPP
      const netPremium = netCallPremium - netPutPremium;
      
      console.log('\n📈 FINAL CUMULATIVE VALUES (using API spec method):');
      console.log(`Total Ticks: ${data.length}`);
      console.log(`Net Call Premium (NCP): $${(netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Put Premium (NPP): $${(netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Call Volume: ${netCallVolume.toLocaleString()}`);
      console.log(`Net Put Volume: ${netPutVolume.toLocaleString()}`);
      console.log(`Net Premium (NCP - NPP): $${(netPremium / 1000000).toFixed(2)}M`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES FOR 10/22:');
      console.log('NCP = -$26.49M');
      console.log('NPP = $3.44M');
      console.log('Net Premium = NCP - NPP = -$26.49M - $3.44M = -$29.93M');
      
      console.log('\n📊 COMPARISON:');
      console.log(`NCP: Expected -$26.49M, Got $${(netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`NPP: Expected $3.44M, Got $${(netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: Expected -$29.93M, Got $${(netPremium / 1000000).toFixed(2)}M`);
      
      // Check if values match
      const ncpMatch = Math.abs(-26490000 - netCallPremium) < 1000000;
      const nppMatch = Math.abs(3440000 - netPutPremium) < 1000000;
      const netPremMatch = Math.abs(-29930000 - netPremium) < 1000000;
      
      console.log('\n✅ ACCURACY CHECK:');
      console.log(`NCP: ${ncpMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`NPP: ${nppMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Net Premium: ${netPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      
      if (ncpMatch && nppMatch && netPremMatch) {
        console.log('\n🎉 SUCCESS: All values match expected results!');
      } else {
        console.log('\n⚠️ Values still don\'t match expected results');
        console.log('\n🔍 ANALYSIS:');
        console.log('The net-prem-ticks endpoint is working correctly according to API spec');
        console.log('But the values don\'t match your expected values');
        console.log('This suggests either:');
        console.log('1. The expected values are from a different source/calculation');
        console.log('2. There\'s another endpoint that provides the expected values');
        console.log('3. The expected values might be from a different date or ticker');
      }
      
      return {
        netCallPremium,
        netPutPremium,
        netPremium,
        totalTicks: data.length,
        success: true
      };
    } else {
      console.log('❌ No data returned from net-prem-ticks endpoint');
      return { success: false };
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
    return { success: false };
  }
}

testNetPremTicksExactAPISpec();
