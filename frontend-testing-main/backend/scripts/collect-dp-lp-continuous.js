#!/usr/bin/env node

/**
 * Continuous Dark Pool & Lit Pool Collection Script
 * 
 * Runs throughout trading hours (4am - 8pm ET) to collect and store DP/LP levels
 * 
 * Usage: node collect-dp-lp-continuous.js [INTERVAL_MINUTES]
 * Example: node collect-dp-lp-continuous.js 5
 * 
 * Default interval: 5 minutes
 */

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const storageService = require('../src/services/darkPoolLitPoolStorageService');
const { runDarkPoolScan } = require('../src/services/darkPoolScanner');
const { runLitFlowScan } = require('../src/services/litFlowScanner');

// Configuration
const INTERVAL_MINUTES = parseInt(process.argv[2]) || 5;
const INTERVAL_MS = INTERVAL_MINUTES * 60 * 1000;

// Market hours (Eastern Time)
const MARKET_START_HOUR = 4;  // 4 AM ET (pre-market)
const MARKET_END_HOUR = 20;    // 8 PM ET (after-hours)

let isCollecting = false;
let collectionCount = 0;
let successCount = 0;
let errorCount = 0;

/**
 * Check if we're within market hours (4am - 8pm ET)
 */
function isMarketHours() {
  const now = new Date();
  // Convert to ET (UTC-5 or UTC-4 depending on DST)
  const etOffset = isDST(now) ? -4 : -5;
  const etHour = new Date(now.getTime() + etOffset * 60 * 60 * 1000).getUTCHours();
  
  return etHour >= MARKET_START_HOUR && etHour < MARKET_END_HOUR;
}

/**
 * Check if date is in Daylight Saving Time
 */
function isDST(date) {
  const jan = new Date(date.getFullYear(), 0, 1).getTimezoneOffset();
  const jul = new Date(date.getFullYear(), 6, 1).getTimezoneOffset();
  return Math.max(jan, jul) !== date.getTimezoneOffset();
}

/**
 * Format date for display
 */
function formatTime(date) {
  return date.toLocaleTimeString('en-US', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit',
    timeZoneName: 'short'
  });
}

/**
 * Get today's date in YYYY-MM-DD format
 */
function getTodayDate() {
  const now = new Date();
  return now.toISOString().split('T')[0];
}

/**
 * Transform scanner results to storage format
 */
function transformToStorageFormat(scanResults, type) {
  const today = getTodayDate();
  
  return scanResults.map(result => ({
    ticker: result.ticker,
    trade_date: today,
    price: result.price,
    size: result.size,
    total_premium: result.total_premium,
    trades: result.trades,
    market_session: result.market_session || 'unknown'
  }));
}

/**
 * Collect and store DP/LP data
 */
async function collectData() {
  if (isCollecting) {
    console.log('⏳ Previous collection still in progress, skipping...');
    return;
  }

  if (!isMarketHours()) {
    console.log('🌙 Outside market hours (4am-8pm ET), skipping collection...');
    return;
  }

  isCollecting = true;
  collectionCount++;
  
  const startTime = Date.now();
  console.log(`\n${'='.repeat(60)}`);
  console.log(`📊 Collection #${collectionCount} - ${formatTime(new Date())}`);
  console.log('='.repeat(60));

  try {
    // Run Dark Pool scan
    console.log('🔍 Scanning Dark Pool trades...');
    const dpResults = await runDarkPoolScan();
    console.log(`✅ Found ${dpResults.length} Dark Pool levels`);

    // Run Lit Pool scan
    console.log('🔍 Scanning Lit Pool trades...');
    const lpResults = await runLitFlowScan();
    console.log(`✅ Found ${lpResults.length} Lit Pool levels`);

    // Transform to storage format
    const dpLevels = transformToStorageFormat(dpResults, 'dark');
    const lpLevels = transformToStorageFormat(lpResults, 'lit');

    // Store Dark Pool data
    console.log('💾 Storing Dark Pool levels...');
    const dpStoreResult = await storageService.storeDarkPoolLevels(dpLevels);
    if (dpStoreResult.success) {
      console.log(`✅ Stored ${dpStoreResult.inserted} Dark Pool levels`);
    } else {
      console.error(`❌ Error storing Dark Pool: ${dpStoreResult.error}`);
      errorCount++;
    }

    // Store Lit Pool data
    console.log('💾 Storing Lit Pool levels...');
    const lpStoreResult = await storageService.storeLitPoolLevels(lpLevels);
    if (lpStoreResult.success) {
      console.log(`✅ Stored ${lpStoreResult.inserted} Lit Pool levels`);
    } else {
      console.error(`❌ Error storing Lit Pool: ${lpStoreResult.error}`);
      errorCount++;
    }

    const duration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log(`\n⏱️  Collection completed in ${duration}s`);
    
    if (dpStoreResult.success && lpStoreResult.success) {
      successCount++;
    }

    // Stats
    console.log(`\n📈 Session Stats:`);
    console.log(`   Total Collections: ${collectionCount}`);
    console.log(`   Successful: ${successCount}`);
    console.log(`   Errors: ${errorCount}`);
    console.log(`   Success Rate: ${((successCount / collectionCount) * 100).toFixed(1)}%`);

  } catch (error) {
    console.error('❌ Collection error:', error.message);
    console.error(error.stack);
    errorCount++;
  } finally {
    isCollecting = false;
  }
}

/**
 * Run data cleanup (once per day at midnight)
 */
async function runDailyCleanup() {
  try {
    console.log('\n🧹 Running daily cleanup...');
    await storageService.cleanupOldData(30); // Keep 30 days
    console.log('✅ Cleanup complete');
  } catch (error) {
    console.error('❌ Cleanup error:', error.message);
  }
}

/**
 * Main function
 */
async function main() {
  console.log('\n' + '='.repeat(60));
  console.log('🚀 Dark Pool & Lit Pool Continuous Collector');
  console.log('='.repeat(60));
  console.log(`⏰ Collection Interval: ${INTERVAL_MINUTES} minutes`);
  console.log(`🕐 Market Hours: ${MARKET_START_HOUR}:00 - ${MARKET_END_HOUR}:00 ET`);
  console.log(`📅 Started: ${formatTime(new Date())}`);
  console.log('='.repeat(60));
  console.log('\nPress Ctrl+C to stop\n');

  // Initialize storage service
  try {
    await storageService.initialize();
    console.log('✅ ClickHouse connection established\n');
  } catch (error) {
    console.error('❌ Failed to initialize storage service:', error.message);
    process.exit(1);
  }

  // Run initial collection
  await collectData();

  // Schedule regular collections
  const collectionInterval = setInterval(collectData, INTERVAL_MS);

  // Schedule daily cleanup at midnight
  const now = new Date();
  const tomorrow = new Date(now);
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(0, 0, 0, 0);
  const msUntilMidnight = tomorrow - now;

  setTimeout(() => {
    runDailyCleanup();
    // Run cleanup daily
    setInterval(runDailyCleanup, 24 * 60 * 60 * 1000);
  }, msUntilMidnight);

  // Graceful shutdown
  process.on('SIGINT', async () => {
    console.log('\n\n⏹️  Stopping collector...');
    clearInterval(collectionInterval);
    
    console.log('\n📊 Final Stats:');
    console.log(`   Total Collections: ${collectionCount}`);
    console.log(`   Successful: ${successCount}`);
    console.log(`   Errors: ${errorCount}`);
    console.log(`   Success Rate: ${((successCount / collectionCount) * 100).toFixed(1)}%`);
    
    await storageService.close();
    console.log('\n✅ Collector stopped\n');
    process.exit(0);
  });

  process.on('SIGTERM', async () => {
    console.log('\n\n⏹️  Received SIGTERM, stopping collector...');
    clearInterval(collectionInterval);
    await storageService.close();
    process.exit(0);
  });
}

// Run the collector
main().catch(error => {
  console.error('❌ Fatal error:', error);
  process.exit(1);
});

