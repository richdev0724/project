// Test script to debug the allTickersService
const allTickersService = require('./src/services/allTickersService');

async function testTickerService() {
  console.log('Testing AllTickersService...');
  
  try {
    // Test FMP API directly
    console.log('\n1. Testing FMP API directly...');
    const fmpTickers = await allTickersService.getAllTickersFromFMP();
    console.log(`FMP returned ${fmpTickers.length} tickers`);
    
    if (fmpTickers.length > 0) {
      console.log('First 5 tickers:', fmpTickers.slice(0, 5).map(t => t.symbol));
      console.log('Sample exchange info:', fmpTickers.slice(0, 3).map(t => ({ symbol: t.symbol, exchange: t.exchange })));
    }
    
    // Test the main function
    console.log('\n2. Testing getAllUSTickers...');
    const usTickers = await allTickersService.getAllUSTickers(true); // Force refresh
    console.log(`getAllUSTickers returned ${usTickers.length} tickers`);
    
    if (usTickers.length > 0) {
      console.log('First 5 US tickers:', usTickers.slice(0, 5));
    }
    
    // Test sample function
    console.log('\n3. Testing getSampleTickers...');
    const sampleTickers = await allTickersService.getSampleTickers(5);
    console.log(`getSampleTickers returned ${sampleTickers.length} tickers`);
    
    if (sampleTickers.length > 0) {
      console.log('Sample tickers:', sampleTickers);
    }
    
  } catch (error) {
    console.error('Error testing ticker service:', error);
  }
}

testTickerService();
