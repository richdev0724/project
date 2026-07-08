/**
 * Script to download historical Dark Pool and Lit Pool data
 * 
 * Usage:
 *   node backend/scripts/download-historical-dp-lp.js META 15
 * 
 * This will download 15 days of Dark Pool and Lit Pool data for META
 * and save to JSON files in backend/data/
 */

const fs = require('fs');
const path = require('path');
const { 
  fetchHistoricalDarkPool, 
  fetchHistoricalLitPool 
} = require('../src/services/historicalDarkPoolService');

// Get arguments
const ticker = process.argv[2] || 'META';
const days = parseInt(process.argv[3]) || 15;

console.log(`\n${'='.repeat(60)}`);
console.log(`Downloading Historical Dark Pool & Lit Pool Data`);
console.log(`${'='.repeat(60)}`);
console.log(`Ticker: ${ticker}`);
console.log(`Days: ${days}`);
console.log(`${'='.repeat(60)}\n`);

async function downloadAndSave() {
  try {
    // Fetch Dark Pool data
    console.log('📊 Fetching Dark Pool data...\n');
    const darkPoolData = await fetchHistoricalDarkPool(ticker, days);
    
    // Fetch Lit Pool data
    console.log('\n📊 Fetching Lit Pool data...\n');
    const litPoolData = await fetchHistoricalLitPool(ticker, days);
    
    // Create data directory if it doesn't exist
    const dataDir = path.join(__dirname, '..', 'data');
    if (!fs.existsSync(dataDir)) {
      fs.mkdirSync(dataDir, { recursive: true });
    }
    
    // Generate filenames with timestamp
    const timestamp = new Date().toISOString().split('T')[0];
    const dpFilename = `${ticker}_dark_pool_${days}days_${timestamp}.json`;
    const lpFilename = `${ticker}_lit_pool_${days}days_${timestamp}.json`;
    
    const dpPath = path.join(dataDir, dpFilename);
    const lpPath = path.join(dataDir, lpFilename);
    
    // Prepare summary data
    const dpSummary = {
      ticker,
      days,
      timestamp,
      total_levels: darkPoolData.length,
      total_trades: darkPoolData.reduce((sum, level) => sum + level.trades, 0),
      total_volume: darkPoolData.reduce((sum, level) => sum + level.size, 0),
      total_premium: darkPoolData.reduce((sum, level) => sum + level.total_premium, 0),
      levels: darkPoolData
    };
    
    const lpSummary = {
      ticker,
      days,
      timestamp,
      total_levels: litPoolData.length,
      total_trades: litPoolData.reduce((sum, level) => sum + level.trades, 0),
      total_volume: litPoolData.reduce((sum, level) => sum + level.size, 0),
      total_premium: litPoolData.reduce((sum, level) => sum + level.total_premium, 0),
      levels: litPoolData
    };
    
    // Save to files
    fs.writeFileSync(dpPath, JSON.stringify(dpSummary, null, 2));
    fs.writeFileSync(lpPath, JSON.stringify(lpSummary, null, 2));
    
    // Print summary
    console.log(`\n${'='.repeat(60)}`);
    console.log('✅ Download Complete!');
    console.log(`${'='.repeat(60)}\n`);
    
    console.log('📁 Dark Pool Data:');
    console.log(`   File: ${dpFilename}`);
    console.log(`   Path: ${dpPath}`);
    console.log(`   Levels: ${dpSummary.total_levels.toLocaleString()}`);
    console.log(`   Trades: ${dpSummary.total_trades.toLocaleString()}`);
    console.log(`   Volume: ${dpSummary.total_volume.toLocaleString()} shares`);
    console.log(`   Premium: $${(dpSummary.total_premium / 1e6).toFixed(2)}M`);
    
    console.log('\n📁 Lit Pool Data:');
    console.log(`   File: ${lpFilename}`);
    console.log(`   Path: ${lpPath}`);
    console.log(`   Levels: ${lpSummary.total_levels.toLocaleString()}`);
    console.log(`   Trades: ${lpSummary.total_trades.toLocaleString()}`);
    console.log(`   Volume: ${lpSummary.total_volume.toLocaleString()} shares`);
    console.log(`   Premium: $${(lpSummary.total_premium / 1e6).toFixed(2)}M`);
    
    console.log(`\n${'='.repeat(60)}\n`);
    
    // Group data by date for analysis
    const dpByDate = {};
    const lpByDate = {};
    
    darkPoolData.forEach(level => {
      if (!dpByDate[level.trade_date]) {
        dpByDate[level.trade_date] = {
          levels: 0,
          trades: 0,
          volume: 0,
          premium: 0
        };
      }
      dpByDate[level.trade_date].levels++;
      dpByDate[level.trade_date].trades += level.trades;
      dpByDate[level.trade_date].volume += level.size;
      dpByDate[level.trade_date].premium += level.total_premium;
    });
    
    litPoolData.forEach(level => {
      if (!lpByDate[level.trade_date]) {
        lpByDate[level.trade_date] = {
          levels: 0,
          trades: 0,
          volume: 0,
          premium: 0
        };
      }
      lpByDate[level.trade_date].levels++;
      lpByDate[level.trade_date].trades += level.trades;
      lpByDate[level.trade_date].volume += level.size;
      lpByDate[level.trade_date].premium += level.total_premium;
    });
    
    console.log('📊 Daily Breakdown:\n');
    console.log('Date         | DP Levels | DP Premium    | LP Levels | LP Premium');
    console.log('-'.repeat(70));
    
    const allDates = [...new Set([...Object.keys(dpByDate), ...Object.keys(lpByDate)])].sort();
    
    allDates.forEach(date => {
      const dp = dpByDate[date] || { levels: 0, premium: 0 };
      const lp = lpByDate[date] || { levels: 0, premium: 0 };
      
      const dpPremiumStr = `$${(dp.premium / 1e6).toFixed(1)}M`.padStart(13);
      const lpPremiumStr = `$${(lp.premium / 1e6).toFixed(1)}M`.padStart(13);
      
      console.log(
        `${date} | ${String(dp.levels).padStart(9)} | ${dpPremiumStr} | ${String(lp.levels).padStart(9)} | ${lpPremiumStr}`
      );
    });
    
    console.log('');
    
  } catch (error) {
    console.error('\n❌ Error downloading data:', error);
    process.exit(1);
  }
}

downloadAndSave();

