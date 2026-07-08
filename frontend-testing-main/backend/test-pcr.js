require('dotenv').config();
const pcrService = require('./src/services/pcrService');

async function testPCRService() {
  console.log('Testing PCR Service...\n');

  // Test single ticker
  console.log('1. Testing single ticker PCR calculation:');
  const metaPCR = await pcrService.getTickerPCR('META');
  console.log(`META PCR: ${metaPCR}\n`);

  // Test multiple tickers
  console.log('2. Testing multiple tickers PCR calculation:');
  const tickers = ['AAPL', 'MSFT', 'GOOGL'];
  const results = await pcrService.calculateMultiplePCR(tickers);
  console.log('Results:', results);
  console.log('');

  // Test cache
  console.log('3. Testing cache:');
  const cachedPCR = pcrService.getCachedPCR('META');
  console.log(`Cached META PCR: ${cachedPCR}`);
  
  const hasCached = pcrService.hasPCRForDate('META');
  console.log(`Has cached META PCR: ${hasCached}`);
  console.log('');

  // Test cache stats
  console.log('4. Cache statistics:');
  const stats = pcrService.getCacheStats();
  console.log('Cache stats:', stats);
}

// Run the test
testPCRService().catch(console.error);
