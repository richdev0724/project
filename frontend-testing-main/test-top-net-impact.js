import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testTopNetImpactEndpoint() {
  try {
    console.log('🧪 Testing /api/market/top-net-impact endpoint for 10/22/2025...');
    
    const url = `${UNUSUAL_WHALES_API_URL}/market/top-net-impact`;
    const params = { 
      date: '2025-10-22',
      limit: 100
    };
    
    console.log(`🔗 URL: ${url}`);
    console.log(`📅 Date: 2025-10-22`);
    console.log(`📊 Limit: 100`);
    
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
      
      console.log('\n📊 TOP NET IMPACT DATA:');
      console.log('First 10 tickers:');
      data.slice(0, 10).forEach((item, i) => {
        console.log(`${i + 1}. ${item.ticker}: $${(item.net_premium / 1000000).toFixed(2)}M`);
      });
      
      // Find META in the results
      const metaData = data.find(item => item.ticker === 'META');
      
      if (metaData) {
        console.log('\n🔍 META DATA FOUND:');
        console.log(`Ticker: ${metaData.ticker}`);
        console.log(`Net Premium: ${metaData.net_premium}`);
        
        const netPremium = metaData.net_premium;
        
        console.log('\n📈 CALCULATED VALUES:');
        console.log(`Net Premium: $${(netPremium / 1000000).toFixed(2)}M`);
        
        console.log('\n🎯 YOUR EXPECTED VALUES FOR 10/22:');
        console.log('Net Premium = -$10,464,052');
        
        console.log('\n📊 COMPARISON:');
        console.log(`Net Premium: Expected -$10,464,052, Got $${netPremium.toLocaleString()}`);
        
        // Check if values match
        const netPremMatch = Math.abs(-10464052 - netPremium) < 1000000;
        
        console.log('\n✅ ACCURACY CHECK:');
        console.log(`Net Premium: ${netPremMatch ? '✅ MATCH' : '❌ MISMATCH'}`);
        
        if (netPremMatch) {
          console.log('\n🎉 SUCCESS: Net premium matches expected result!');
        } else {
          console.log('\n⚠️ Net premium still needs adjustment');
        }
        
        // Now let's see if we can find the individual NCP and NPP values
        // We know from the stock screener that:
        // Total Call Premium = $244.61M
        // Total Put Premium = $76.92M
        // Net Premium = -$10,464,052
        
        // If Net Premium = Call Premium - Put Premium
        // Then: -$10,464,052 = Call Premium - Put Premium
        // We need to find the individual net call and put premiums
        
        console.log('\n🧮 WORKING BACKWARDS:');
        console.log('We know:');
        console.log('- Total Call Premium = $244.61M');
        console.log('- Total Put Premium = $76.92M');
        console.log('- Net Premium = -$10,464,052');
        console.log('');
        console.log('If Net Premium = Net Call Premium - |Net Put Premium|');
        console.log('And Net Call Premium = Call Ask - Call Bid');
        console.log('And Net Put Premium = Put Ask - Put Bid');
        console.log('');
        console.log('We need to find the ask/bid breakdown to get the correct net premiums');
        
      } else {
        console.log('\n❌ META data not found in top net impact results');
        console.log('Available tickers:', data.map(item => item.ticker).slice(0, 10));
      }
    } else {
      console.log('❌ No data returned from top-net-impact endpoint');
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testTopNetImpactEndpoint();

