import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function investigatePremiumCalculation() {
  try {
    console.log('🔍 Investigating premium calculation methods...');
    
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
      
      console.log('\n📊 DETAILED PREMIUM ANALYSIS:');
      
      // Let's look at the distribution of premium values
      let positiveCallPremiums = 0;
      let negativeCallPremiums = 0;
      let positivePutPremiums = 0;
      let negativePutPremiums = 0;
      
      let totalPositiveCallPremium = 0;
      let totalNegativeCallPremium = 0;
      let totalPositivePutPremium = 0;
      let totalNegativePutPremium = 0;
      
      ticks.forEach(tick => {
        const callPrem = parseFloat(tick.net_call_premium);
        const putPrem = parseFloat(tick.net_put_premium);
        
        if (callPrem > 0) {
          positiveCallPremiums++;
          totalPositiveCallPremium += callPrem;
        } else if (callPrem < 0) {
          negativeCallPremiums++;
          totalNegativeCallPremium += Math.abs(callPrem);
        }
        
        if (putPrem > 0) {
          positivePutPremiums++;
          totalPositivePutPremium += putPrem;
        } else if (putPrem < 0) {
          negativePutPremiums++;
          totalNegativePutPremium += Math.abs(putPrem);
        }
      });
      
      console.log(`Call Premiums:`);
      console.log(`  Positive: ${positiveCallPremiums} ticks, Total: $${totalPositiveCallPremium.toLocaleString()}`);
      console.log(`  Negative: ${negativeCallPremiums} ticks, Total: $${totalNegativeCallPremium.toLocaleString()}`);
      
      console.log(`Put Premiums:`);
      console.log(`  Positive: ${positivePutPremiums} ticks, Total: $${totalPositivePutPremium.toLocaleString()}`);
      console.log(`  Negative: ${negativePutPremiums} ticks, Total: $${totalNegativePutPremium.toLocaleString()}`);
      
      // Try different calculation methods
      console.log('\n🧮 ALTERNATIVE CALCULATION METHODS:');
      
      // Method 1: Sum all positive premiums only
      const totalPositivePremiums = totalPositiveCallPremium + totalPositivePutPremium;
      console.log(`Method 1 - Sum positive premiums only: $${totalPositivePremiums.toLocaleString()}`);
      
      // Method 2: Sum all absolute premiums
      const totalAbsPremiums = totalPositiveCallPremium + totalNegativeCallPremium + totalPositivePutPremium + totalNegativePutPremium;
      console.log(`Method 2 - Sum all absolute premiums: $${totalAbsPremiums.toLocaleString()}`);
      
      // Method 3: Sum call premiums (absolute) + put premiums (absolute)
      const totalCallAbs = totalPositiveCallPremium + totalNegativeCallPremium;
      const totalPutAbs = totalPositivePutPremium + totalNegativePutPremium;
      console.log(`Method 3 - Call abs + Put abs: $${(totalCallAbs + totalPutAbs).toLocaleString()}`);
      
      // Method 4: Check if we need to multiply by volume or something
      let totalCallVolume = 0;
      let totalPutVolume = 0;
      ticks.forEach(tick => {
        totalCallVolume += parseFloat(tick.call_volume) || 0;
        totalPutVolume += parseFloat(tick.put_volume) || 0;
      });
      
      console.log(`\nVolume-weighted calculations:`);
      console.log(`Total Call Volume: ${totalCallVolume.toLocaleString()}`);
      console.log(`Total Put Volume: ${totalPutVolume.toLocaleString()}`);
      
      // Maybe premium per volume?
      const avgCallPremiumPerVolume = totalCallAbs / totalCallVolume;
      const avgPutPremiumPerVolume = totalPutAbs / totalPutVolume;
      console.log(`Avg Call Premium per Volume: $${avgCallPremiumPerVolume.toFixed(2)}`);
      console.log(`Avg Put Premium per Volume: $${avgPutPremiumPerVolume.toFixed(2)}`);
      
      console.log('\n🎯 EXPECTED VALUES:');
      console.log('Total Premium: $321,530,573');
      console.log('Net Premium: -$10,464,052');
      
      // Check if any method gets us close
      const methods = [
        { name: 'Positive premiums only', value: totalPositivePremiums },
        { name: 'All absolute premiums', value: totalAbsPremiums },
        { name: 'Call abs + Put abs', value: totalCallAbs + totalPutAbs }
      ];
      
      methods.forEach(method => {
        const diff = Math.abs(321530573 - method.value);
        const percentDiff = (diff / 321530573) * 100;
        console.log(`${method.name}: $${method.value.toLocaleString()} (${percentDiff.toFixed(1)}% off)`);
      });
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

investigatePremiumCalculation();

