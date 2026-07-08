import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function getNetPremiums() {
  try {
    console.log('📊 Getting net premiums for META on 10/22/2025...');
    
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
      
      console.log('\n📈 NET PREMIUMS FOR 10/22/2025:');
      console.log(`Net Call Premium: $${lastTick.net_call_premium.toLocaleString()}`);
      console.log(`Net Put Premium: $${lastTick.net_put_premium.toLocaleString()}`);
      console.log(`Net Premium (Call - |Put|): $${(lastTick.net_call_premium - Math.abs(lastTick.net_put_premium)).toLocaleString()}`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES:');
      console.log('Net Premium: -$10,464,052');
      
      const calculatedNetPremium = lastTick.net_call_premium - Math.abs(lastTick.net_put_premium);
      console.log(`\n✅ CALCULATED NET PREMIUM: $${calculatedNetPremium.toLocaleString()}`);
      
      if (Math.abs(-10464052 - calculatedNetPremium) < 1000) {
        console.log('✅ SUCCESS: Net premium matches expected value!');
      } else {
        console.log('❌ MISMATCH: Net premium does not match expected value');
      }
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

getNetPremiums();

