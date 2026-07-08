#!/usr/bin/env node

/**
 * Test script to verify ticker filtering functionality
 * Run this from the frontend-testing directory:
 * node test-ticker-filters.js
 */

const fetch = require('node-fetch'); // You may need to install this: npm install node-fetch

const BASE_URL = 'http://localhost:3001/api/flow';

async function testTickerFilters() {
  console.log('🧪 Testing Ticker Filter Functionality\n');
  
  try {
    // Test the dedicated test endpoint
    console.log('🔧 Testing backend filter logic...');
    const testResponse = await fetch(`${BASE_URL}/test-filters`);
    const testData = await testResponse.json();
    
    if (testData.success) {
      console.log('✅ Backend filter tests completed successfully!\n');
      
      testData.results.forEach(result => {
        console.log(`📊 Test: ${result.testName}`);
        console.log(`   Filters: ${JSON.stringify(result.filters)}`);
        if (result.error) {
          console.log(`   ❌ Error: ${result.error}`);
        } else {
          console.log(`   📈 Results: ${result.resultCount} trades, ${result.totalCount} total`);
          console.log(`   🎯 Tickers: ${result.uniqueTickers.join(', ')}`);
          console.log(`   📄 Sample: ${result.sampleContracts.slice(0, 2).join(', ')}`);
        }
        console.log('');
      });
    } else {
      console.log('❌ Backend filter tests failed:', testData.message);
    }
    
    // Test actual API endpoint
    console.log('🔍 Testing actual API endpoint...');
    
    const testCases = [
      { name: 'SPY only', params: 'tickers=SPY&pageSize=5' },
      { name: 'Exclude VIX', params: 'ignoreTickers=VIX&pageSize=5' },
      { name: 'SPY and AAPL', params: 'tickers=SPY,AAPL&pageSize=5' }
    ];
    
    for (const testCase of testCases) {
      console.log(`\n🎯 Testing: ${testCase.name}`);
      const response = await fetch(`${BASE_URL}?${testCase.params}`);
      const data = await response.json();
      
      if (data.success && data.data.results) {
        const tickers = [...new Set(data.data.results.map(item => item._source.ticker))];
        console.log(`   ✅ Got ${data.data.results.length} results`);
        console.log(`   🎯 Tickers found: ${tickers.join(', ')}`);
      } else {
        console.log(`   ❌ Failed: ${data.error || 'Unknown error'}`);
      }
    }
    
  } catch (error) {
    console.error('❌ Test failed:', error.message);
    console.log('\n💡 Make sure the backend server is running on port 3001');
  }
}

// Run the tests
testTickerFilters();
