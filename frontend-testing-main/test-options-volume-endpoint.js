import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testOptionsVolumeEndpoint() {
  try {
    console.log('🧪 Testing /api/stock/{ticker}/options-volume endpoint for META...');
    
    // Test without date parameter first
    const url = `${UNUSUAL_WHALES_API_URL}/stock/META/options-volume`;
    const params = { limit: 1 };
    
    console.log(`🔗 URL: ${url}`);
    console.log(`📊 Limit: 1`);
    
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
      const data = response.data.data[0];
      
      console.log('\n📊 OPTIONS VOLUME DATA:');
      console.log(`Ticker: ${data.ticker}`);
      console.log(`Date: ${data.date}`);
      console.log(`Call Premium: ${data.call_premium}`);
      console.log(`Put Premium: ${data.put_premium}`);
      console.log(`Net Call Premium: ${data.net_call_premium}`);
      console.log(`Net Put Premium: ${data.net_put_premium}`);
      console.log(`Call Volume: ${data.call_volume}`);
      console.log(`Put Volume: ${data.put_volume}`);
      
      // Calculate values
      const totalCallPremium = parseFloat(data.call_premium) || 0;
      const totalPutPremium = parseFloat(data.put_premium) || 0;
      const netCallPremium = parseFloat(data.net_call_premium) || 0;
      const netPutPremium = parseFloat(data.net_put_premium) || 0;
      const netPremium = netCallPremium - Math.abs(netPutPremium);
      
      console.log('\n📈 CALCULATED VALUES:');
      console.log(`Total Call Premium: $${(totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Total Put Premium: $${(totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Call Premium: $${(netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Put Premium: $${(netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: $${(netPremium / 1000000).toFixed(2)}M`);
      
      console.log('\n🎯 YOUR EXPECTED VALUES FOR 10/22:');
      console.log('NCP = -$26.49M');
      console.log('NPP = $3.44M');
      console.log('Put Premium = $76.92M');
      console.log('Call Premium = $244.61M');
      console.log('Net Premium = -$10,464,052');
      
      console.log('\n📊 COMPARISON:');
      console.log(`NCP: Expected -$26.49M, Got $${(netCallPremium / 1000000).toFixed(2)}M`);
      console.log(`NPP: Expected $3.44M, Got $${(netPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Call Premium: Expected $244.61M, Got $${(totalCallPremium / 1000000).toFixed(2)}M`);
      console.log(`Put Premium: Expected $76.92M, Got $${(totalPutPremium / 1000000).toFixed(2)}M`);
      console.log(`Net Premium: Expected -$10,464,052, Got $${netPremium.toLocaleString()}`);
      
      // Check if values match
      const ncpMatch = Math.abs(-26490000 - netCallPremium) < 1000000;
      const nppMatch = Math.abs(3440000 - netPutPremium) < 1000000;
      const callPremMatch = Math.abs(244610000 - totalCallPremium) < 1000000;
      const putPremMatch = Math.abs(76920000 - totalPutPremium) < 1000000;
      const netPremMatch = Math.abs(-10464052 - netPremium) < 1000000;
      
      console.log('\n✅ ACCURACY CHECK:');
      console.log(`NCP: ${ncpMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`NPP: ${nppMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Call Premium: ${callPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Put Premium: ${putPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      console.log(`Net Premium: ${netPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
      
      if (ncpMatch && nppMatch && callPremMatch && putPremMatch && netPremMatch) {
        console.log('\n🎉 SUCCESS: All values match expected results!');
      } else {
        console.log('\n⚠️ Some values still need adjustment');
      }
    } else {
      console.log('❌ No data returned from options-volume endpoint');
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testOptionsVolumeEndpoint();

