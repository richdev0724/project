const axios = require('axios');

/**
 * Test script for market tide endpoint
 * Run with: node backend/test-market-tide.js
 */
async function testMarketTide() {
  try {
    console.log('🧪 Testing market tide endpoint...\n');
    
    const response = await axios.get('http://localhost:3001/api/market-tide');
    
    console.log('✅ Response Status:', response.status);
    console.log('\n📊 Response Summary:');
    console.log(`   Success: ${response.data.success}`);
    console.log(`   Data points: ${response.data.data?.length || 0}`);
    
    if (response.data.success && response.data.data && response.data.data.length > 0) {
      const firstPoint = response.data.data[0];
      const lastPoint = response.data.data[response.data.data.length - 1];
      
      console.log('\n📈 First Data Point:');
      console.log(`   Timestamp: ${firstPoint.original_timestamp}`);
      console.log(`   Call Premium: $${(firstPoint.call_premium / 1000000).toFixed(2)}M`);
      console.log(`   Put Premium: $${(firstPoint.put_premium / 1000000).toFixed(2)}M`);
      console.log(`   Net Volume: ${firstPoint.net_volume.toLocaleString()}`);
      console.log(`   SPY Close: ${firstPoint.spy_close || 'N/A'}`);
      
      console.log('\n📉 Last Data Point:');
      console.log(`   Timestamp: ${lastPoint.original_timestamp}`);
      console.log(`   Call Premium: $${(lastPoint.call_premium / 1000000).toFixed(2)}M`);
      console.log(`   Put Premium: $${(lastPoint.put_premium / 1000000).toFixed(2)}M`);
      console.log(`   Net Volume: ${lastPoint.net_volume.toLocaleString()}`);
      console.log(`   SPY Close: ${lastPoint.spy_close || 'N/A'}`);
      
      // Count how many have SPY prices
      const withSpy = response.data.data.filter(d => d.spy_close !== null).length;
      console.log(`\n✅ ${withSpy} out of ${response.data.data.length} data points have SPY prices`);
      
    } else {
      console.error('\n❌ Request failed or no data returned');
      console.error('Response data:', response.data);
    }
    
  } catch (error) {
    console.error('\n❌ Error:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
  }
}

testMarketTide();

