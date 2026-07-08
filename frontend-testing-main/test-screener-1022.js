import axios from 'axios';

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api';
const UNUSUAL_WHALES_API_KEY = '18809e52-9b33-43f5-bcb0-fa4ea84bc462';

async function testStockScreenerFor1022() {
  try {
    console.log('🧪 Testing stock screener endpoint for META on 10/22/2025...');
    
    // Test the stock screener endpoint as per API spec
    const screenerUrl = `${UNUSUAL_WHALES_API_URL}/screener/stocks`;
    const params = { 
      ticker: 'META',
      date: '2025-10-22'
    };
    
    console.log(`🔗 URL: ${screenerUrl}`);
    console.log(`📅 Date: 2025-10-22`);
    console.log(`📊 Ticker: META`);
    
    const response = await axios.get(screenerUrl, {
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
      const screenerData = response.data.data;
      
      console.log('\n📊 SCREENER DATA:');
      console.log(`Total results: ${screenerData.length}`);
      
      // Find META in the results
      const metaData = screenerData.find(item => item.ticker === 'META');
      
      if (metaData) {
        console.log('\n🔍 META DATA FOUND:');
        console.log(`Ticker: ${metaData.ticker}`);
        console.log(`Date: ${metaData.date}`);
        console.log(`Call Premium: ${metaData.call_premium}`);
        console.log(`Put Premium: ${metaData.put_premium}`);
        console.log(`Call Volume: ${metaData.call_volume}`);
        console.log(`Put Volume: ${metaData.put_volume}`);
        console.log(`Net Call Premium: ${metaData.net_call_premium}`);
        console.log(`Net Put Premium: ${metaData.net_put_premium}`);
        
        // Calculate totals
        const totalCallPremium = parseFloat(metaData.call_premium) || 0;
        const totalPutPremium = parseFloat(metaData.put_premium) || 0;
        const netCallPremium = parseFloat(metaData.net_call_premium) || 0;
        const netPutPremium = parseFloat(metaData.net_put_premium) || 0;
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
        console.log('\n❌ META data not found in screener results');
        console.log('Available tickers:', screenerData.map(item => item.ticker).slice(0, 10));
      }
    } else {
      console.log('❌ No data returned from stock screener endpoint');
    }
    
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testStockScreenerFor1022();

