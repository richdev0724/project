import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testUWAPI() {
  try {
    console.log('🧪 Testing Unusual Whales API directly...');
    
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
      const firstTick = response.data.data[0];
      const lastTick = response.data.data[response.data.data.length - 1];
      
      console.log('\n🔍 FIRST TICK:');
      console.log(JSON.stringify(firstTick, null, 2));
      
      console.log('\n🔍 LAST TICK:');
      console.log(JSON.stringify(lastTick, null, 2));
      
      console.log('\n📈 ANALYSIS:');
      console.log(`Total ticks: ${response.data.data.length}`);
      console.log(`First tick time: ${firstTick.tape_time}`);
      console.log(`Last tick time: ${lastTick.tape_time}`);
      console.log(`Call premium (first): ${firstTick.net_call_premium}`);
      console.log(`Call premium (last): ${lastTick.net_call_premium}`);
      console.log(`Put premium (first): ${firstTick.net_put_premium}`);
      console.log(`Put premium (last): ${lastTick.net_put_premium}`);
      
      // Check if values are cumulative or incremental
      const callDiff = parseFloat(lastTick.net_call_premium) - parseFloat(firstTick.net_call_premium);
      const putDiff = parseFloat(lastTick.net_put_premium) - parseFloat(firstTick.net_put_premium);
      
      console.log(`\n🔢 DIFFERENCES:`);
      console.log(`Call premium difference: ${callDiff}`);
      console.log(`Put premium difference: ${putDiff}`);
      
      if (Math.abs(callDiff) > Math.abs(parseFloat(firstTick.net_call_premium))) {
        console.log('📊 Data appears to be CUMULATIVE (last tick = total)');
      } else {
        console.log('📊 Data appears to be INCREMENTAL (need to sum all ticks)');
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

testUWAPI();
