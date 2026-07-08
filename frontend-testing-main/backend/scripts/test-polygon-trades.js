/**
 * Test script to check Polygon trades API access
 */

const axios = require('axios');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;
const ticker = 'META';

async function testTradesAPI() {
  console.log('\n=== Testing Polygon Trades API ===\n');
  console.log(`API Key: ${POLYGON_API_KEY.substring(0, 15)}...`);
  console.log(`Ticker: ${ticker}\n`);
  
  // Test 1: Today's trades (like the scanner does)
  const today = new Date().toISOString().split('T')[0];
  console.log(`Test 1: Fetching today's trades (${today})...`);
  
  try {
    const url1 = `https://api.polygon.io/v3/trades/${ticker}?timestamp=${today}&order=desc&limit=10&apiKey=${POLYGON_API_KEY}`;
    console.log(`URL: ${url1.replace(POLYGON_API_KEY, 'API_KEY')}`);
    
    const resp1 = await axios.get(url1);
    console.log(`✅ Success! Got ${resp1.data.results?.length || 0} trades`);
    
    if (resp1.data.results && resp1.data.results.length > 0) {
      const trade = resp1.data.results[0];
      console.log(`   Sample trade:`, {
        price: trade.price,
        size: trade.size,
        exchange: trade.exchange,
        trf_id: trade.trf_id
      });
    }
  } catch (error) {
    console.log(`❌ Failed:`, error.response?.status, error.response?.statusText);
    console.log(`   Message:`, error.response?.data);
  }
  
  // Test 2: Yesterday's trades
  const yesterday = new Date();
  yesterday.setDate(yesterday.getDate() - 1);
  const yesterdayStr = yesterday.toISOString().split('T')[0];
  
  console.log(`\nTest 2: Fetching yesterday's trades (${yesterdayStr})...`);
  
  try {
    const url2 = `https://api.polygon.io/v3/trades/${ticker}?timestamp=${yesterdayStr}&order=desc&limit=10&apiKey=${POLYGON_API_KEY}`;
    console.log(`URL: ${url2.replace(POLYGON_API_KEY, 'API_KEY')}`);
    
    const resp2 = await axios.get(url2);
    console.log(`✅ Success! Got ${resp2.data.results?.length || 0} trades`);
  } catch (error) {
    console.log(`❌ Failed:`, error.response?.status, error.response?.statusText);
    console.log(`   Message:`, error.response?.data);
  }
  
  // Test 3: Using timestamp range (nanoseconds)
  const startOfDay = new Date(today + 'T00:00:00Z').getTime() * 1e6;
  const endOfDay = new Date(today + 'T23:59:59Z').getTime() * 1e6;
  
  console.log(`\nTest 3: Using timestamp.gte and timestamp.lte...`);
  
  try {
    const url3 = `https://api.polygon.io/v3/trades/${ticker}?timestamp.gte=${startOfDay}&timestamp.lte=${endOfDay}&order=desc&limit=10&apiKey=${POLYGON_API_KEY}`;
    console.log(`URL: ${url3.replace(POLYGON_API_KEY, 'API_KEY')}`);
    
    const resp3 = await axios.get(url3);
    console.log(`✅ Success! Got ${resp3.data.results?.length || 0} trades`);
  } catch (error) {
    console.log(`❌ Failed:`, error.response?.status, error.response?.statusText);
    console.log(`   Message:`, error.response?.data);
  }
  
  console.log('\n=== Test Complete ===\n');
}

testTradesAPI();

