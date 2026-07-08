const axios = require('axios');

/**
 * Test script for top net impact endpoint
 * Run with: node backend/test-top-net-impact.js
 */
async function testTopNetImpact() {
  try {
    console.log('🧪 Testing top net impact endpoint...\n');
    
    const response = await axios.get('http://localhost:3001/api/top-net-impact');
    
    console.log('✅ Response Status:', response.status);
    console.log('\n📊 Response Data:');
    console.log(JSON.stringify(response.data, null, 2));
    
    if (response.data.success) {
      console.log('\n✅ Success!');
      console.log(`   Bullish tickers: ${response.data.data.bullish.length}`);
      console.log(`   Bearish tickers: ${response.data.data.bearish.length}`);
      
      if (response.data.data.bullish.length > 0) {
        console.log('\n📈 Sample Bullish:');
        console.log(`   ${response.data.data.bullish[0].ticker}: $${(response.data.data.bullish[0].net_premium / 1000000).toFixed(2)}M`);
      }
      
      if (response.data.data.bearish.length > 0) {
        console.log('\n📉 Sample Bearish:');
        console.log(`   ${response.data.data.bearish[0].ticker}: $${(response.data.data.bearish[0].net_premium / 1000000).toFixed(2)}M`);
      }
    } else {
      console.error('\n❌ Request failed:', response.data.error);
    }
    
  } catch (error) {
    console.error('\n❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testTopNetImpact();

