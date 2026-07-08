const { getLastNTradingSessionsComprehensive } = require('../services/unusualWhalesComprehensiveService');
const comprehensivePremiumsStorageService = require('../services/comprehensivePremiumsStorageService');

/**
 * Script to populate ClickHouse with comprehensive premium data for the last 15 trading sessions
 * This is a one-time script to get historical data into the system
 * @param {string} ticker - Stock ticker symbol (default: META)
 * @param {number} days - Number of trading days (default: 15)
 */
async function populateHistoricalData(ticker = 'META', days = 15) {
  try {
    console.log(`🚀 Starting historical data population for ${ticker}`);
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
    
    console.log('\n🎉 HISTORICAL DATA POPULATION COMPLETED!');
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
    console.error('❌ Error populating historical data:', error.message);
    return {
      success: false,
      error: error.message
    };
  }
}

/**
 * Get top 100 most popular tickers
 * This is a simplified list - you can expand this based on your needs
 */
function getTop100Tickers() {
  return [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'BRK.B', 'UNH', 'JNJ',
    'V', 'PG', 'JPM', 'HD', 'MA', 'DIS', 'PYPL', 'BAC', 'ADBE', 'CMCSA',
    'NFLX', 'PFE', 'TMO', 'ABT', 'PEP', 'KO', 'AVGO', 'TXN', 'COST', 'MRK',
    'ACN', 'WMT', 'CRM', 'NKE', 'QCOM', 'AMD', 'INTC', 'CSCO', 'ORCL', 'IBM',
    'AMAT', 'T', 'VZ', 'CVX', 'XOM', 'LLY', 'ABBV', 'DHR', 'TGT', 'LOW',
    'SPGI', 'ISRG', 'GILD', 'BKNG', 'ADP', 'MDT', 'AMGN', 'HON', 'RTX', 'UPS',
    'CAT', 'GE', 'AXP', 'BLK', 'SYK', 'TJX', 'CVS', 'MMM', 'MO', 'PM',
    'CL', 'KO', 'PEP', 'WBA', 'BMY', 'ABT', 'TMO', 'DHR', 'ISRG', 'GILD',
    'BKNG', 'ADP', 'MDT', 'AMGN', 'HON', 'RTX', 'UPS', 'CAT', 'GE', 'AXP',
    'BLK', 'SYK', 'TJX', 'CVS', 'MMM', 'MO', 'PM', 'CL', 'KO', 'PEP'
  ];
}

/**
 * Populate historical data for top 100 tickers
 */
async function populateTop100Tickers() {
  const top100Tickers = getTop100Tickers();
  const results = [];
  
  console.log(`🚀 Starting historical data population for top 100 tickers`);
  console.log(`📊 Processing ${top100Tickers.length} tickers...`);
  console.log('='.repeat(80));
  
  for (let i = 0; i < top100Tickers.length; i++) {
    const ticker = top100Tickers[i];
    console.log(`\n🔄 Processing ${ticker} (${i + 1}/${top100Tickers.length})...`);
    
    try {
      const result = await populateHistoricalData(ticker, 15);
      results.push({
        ticker,
        success: result.success,
        recordsStored: result.recordsStored || 0,
        error: result.error || null
      });
      
      if (result.success) {
        console.log(`✅ Successfully processed ${ticker}`);
      } else {
        console.log(`❌ Failed to process ${ticker}: ${result.error}`);
      }
      
      // Add delay between tickers to avoid rate limiting
      if (i < top100Tickers.length - 1) {
        console.log('⏳ Waiting 2 seconds before next ticker...');
        await new Promise(resolve => setTimeout(resolve, 2000));
      }
      
    } catch (error) {
      console.error(`❌ Error processing ${ticker}:`, error.message);
      results.push({
        ticker,
        success: false,
        recordsStored: 0,
        error: error.message
      });
    }
  }
  
  // Summary
  const successCount = results.filter(r => r.success).length;
  const totalRecords = results.reduce((sum, r) => sum + r.recordsStored, 0);
  
  console.log('\n🎉 TOP 100 TICKERS PROCESSING COMPLETED!');
  console.log('='.repeat(60));
  console.log(`Total Tickers: ${top100Tickers.length}`);
  console.log(`Successful: ${successCount}`);
  console.log(`Failed: ${top100Tickers.length - successCount}`);
  console.log(`Total Records Stored: ${totalRecords}`);
  
  return results;
}

// Run the script if this file is executed directly
if (require.main === module) {
  const args = process.argv.slice(2);
  const ticker = args[0] || 'META';
  const days = parseInt(args[1]) || 15;
  const top100 = args[2] === 'top100';
  
  if (top100) {
    populateTop100Tickers()
      .then(results => {
        console.log('\n✅ Top 100 tickers script completed!');
        process.exit(0);
      })
      .catch(error => {
        console.error('\n❌ Top 100 tickers script failed:', error.message);
        process.exit(1);
      });
  } else {
    populateHistoricalData(ticker, days)
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
}

module.exports = { populateHistoricalData, populateTop100Tickers, getTop100Tickers };
