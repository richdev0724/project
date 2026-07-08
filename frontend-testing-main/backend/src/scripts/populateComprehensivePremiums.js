const { getLastNTradingSessionsComprehensive } = require('../services/unusualWhalesComprehensiveService');
const comprehensivePremiumsStorageService = require('../services/comprehensivePremiumsStorageService');

/**
 * Script to populate ClickHouse with comprehensive premium data for the last 15 trading sessions
 * @param {string} ticker - Stock ticker symbol (default: META)
 * @param {number} days - Number of trading days (default: 15)
 */
async function populateComprehensivePremiumsData(ticker = 'META', days = 15) {
  try {
    console.log(`🚀 Starting comprehensive premiums data population for ${ticker}`);
    console.log(`📊 Fetching last ${days} trading sessions...`);
    console.log('='.repeat(80));

    // Test ClickHouse connection
    console.log('🔍 Testing ClickHouse connection...');
    const isConnected = await comprehensivePremiumsStorageService.testConnection();
    if (!isConnected) {
      throw new Error('Failed to connect to ClickHouse');
    }

    // Create table if it doesn't exist
    console.log('📋 Creating/verifying comprehensive premiums table...');
    await comprehensivePremiumsStorageService.createTable();

    // Clear existing data for this ticker
    console.log(`🗑️ Clearing existing data for ${ticker}...`);
    await comprehensivePremiumsStorageService.clearTickerData(ticker);

    // Fetch comprehensive premium data
    console.log(`📊 Fetching comprehensive premium data for ${ticker}...`);
    const comprehensiveData = await getLastNTradingSessionsComprehensive(ticker, days);

    if (comprehensiveData.length === 0) {
      console.log('⚠️ No data retrieved from API');
      return;
    }

    // Filter successful results
    const successfulData = comprehensiveData.filter(data => data.success);
    console.log(`✅ Retrieved ${successfulData.length} successful records out of ${comprehensiveData.length} total`);

    if (successfulData.length === 0) {
      console.log('⚠️ No successful data to store');
      return;
    }

    // Store data in ClickHouse
    console.log(`💾 Storing ${successfulData.length} records in ClickHouse...`);
    await comprehensivePremiumsStorageService.storeMultipleDaysData(successfulData);

    // Get storage statistics
    console.log('📈 Getting storage statistics...');
    const stats = await comprehensivePremiumsStorageService.getStorageStatistics();
    
    console.log('\n🎉 COMPREHENSIVE PREMIUMS DATA POPULATION COMPLETED!');
    console.log('='.repeat(60));
    console.log(`Ticker: ${ticker.toUpperCase()}`);
    console.log(`Records Stored: ${successfulData.length}`);
    console.log(`Total Records in DB: ${stats.totalRecords}`);
    console.log(`Unique Tickers: ${stats.uniqueTickers}`);
    console.log(`Date Range: ${stats.earliestDate} to ${stats.latestDate}`);
    
    console.log('\n📊 STORED DATA SUMMARY:');
    console.log('Date       | NCP (M)  | NPP (M)  | Call Prem (M) | Put Prem (M) | Net Prem (M) | Ticks');
    console.log('-'.repeat(80));
    
    successfulData.forEach(data => {
      const ncpM = (data.netCallPremium / 1000000).toFixed(2);
      const nppM = (data.netPutPremium / 1000000).toFixed(2);
      const callPremM = (data.totalCallPremium / 1000000).toFixed(2);
      const putPremM = (data.totalPutPremium / 1000000).toFixed(2);
      const netPremM = (data.calculatedNetPremium / 1000000).toFixed(2);
      
      console.log(`${data.date} | ${ncpM.padStart(8)} | ${nppM.padStart(8)} | ${callPremM.padStart(13)} | ${putPremM.padStart(11)} | ${netPremM.padStart(11)} | ${data.totalTicks.toString().padStart(5)}`);
    });

    return {
      success: true,
      ticker: ticker.toUpperCase(),
      recordsStored: successfulData.length,
      totalRecords: stats.totalRecords,
      stats
    };

  } catch (error) {
    console.error('❌ Error populating comprehensive premiums data:', error.message);
    return {
      success: false,
      error: error.message
    };
  }
}

// Run the script if this file is executed directly
if (require.main === module) {
  const ticker = process.argv[2] || 'META';
  const days = parseInt(process.argv[3]) || 15;
  
  populateComprehensivePremiumsData(ticker, days)
    .then(result => {
      if (result.success) {
        console.log('\n✅ Script completed successfully!');
        process.exit(0);
      } else {
        console.log('\n❌ Script failed:', result.error);
        process.exit(1);
      }
    })
    .catch(error => {
      console.error('\n❌ Script crashed:', error.message);
      process.exit(1);
    });
}

module.exports = { populateComprehensivePremiumsData };
