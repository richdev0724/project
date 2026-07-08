#!/usr/bin/env node

/**
 * Import Historical Dark Pool & Lit Pool Data to ClickHouse
 * 
 * Usage: node import-historical-dp-lp.js <TICKER> <DAYS>
 * Example: node import-historical-dp-lp.js META 15
 * 
 * This script:
 * 1. Fetches historical DP/LP trades from Polygon
 * 2. Aggregates into price levels
 * 3. Stores in ClickHouse for fast retrieval
 */

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const storageService = require('../src/services/darkPoolLitPoolStorageService');
const { fetchHistoricalDarkPool, fetchHistoricalLitPool } = require('../src/services/historicalDarkPoolService');

async function importHistoricalData(ticker, days) {
  const startTime = Date.now();
  console.log(`\n🚀 Starting historical import for ${ticker} (${days} days)...\n`);

  try {
    // Fetch Dark Pool data
    console.log('📊 Fetching Dark Pool trades...');
    const dpStartTime = Date.now();
    const darkPoolLevels = await fetchHistoricalDarkPool(ticker, days);
    const dpDuration = ((Date.now() - dpStartTime) / 1000).toFixed(2);
    console.log(`✅ Fetched ${darkPoolLevels.length} Dark Pool levels in ${dpDuration}s`);

    // Fetch Lit Pool data
    console.log('\n📊 Fetching Lit Pool trades...');
    const lpStartTime = Date.now();
    const litPoolLevels = await fetchHistoricalLitPool(ticker, days);
    const lpDuration = ((Date.now() - lpStartTime) / 1000).toFixed(2);
    console.log(`✅ Fetched ${litPoolLevels.length} Lit Pool levels in ${lpDuration}s`);

    // Store Dark Pool data
    console.log('\n💾 Storing Dark Pool levels in ClickHouse...');
    const dpStoreResult = await storageService.storeDarkPoolLevels(darkPoolLevels);
    if (dpStoreResult.success) {
      console.log(`✅ Stored ${dpStoreResult.inserted} Dark Pool levels`);
    } else {
      console.error(`❌ Error storing Dark Pool levels: ${dpStoreResult.error}`);
    }

    // Store Lit Pool data
    console.log('\n💾 Storing Lit Pool levels in ClickHouse...');
    const lpStoreResult = await storageService.storeLitPoolLevels(litPoolLevels);
    if (lpStoreResult.success) {
      console.log(`✅ Stored ${lpStoreResult.inserted} Lit Pool levels`);
    } else {
      console.error(`❌ Error storing Lit Pool levels: ${lpStoreResult.error}`);
    }

    // Summary
    const totalDuration = ((Date.now() - startTime) / 1000).toFixed(2);
    console.log('\n' + '='.repeat(60));
    console.log('📊 IMPORT SUMMARY');
    console.log('='.repeat(60));
    console.log(`Ticker:            ${ticker}`);
    console.log(`Days:              ${days}`);
    console.log(`Dark Pool Levels:  ${dpStoreResult.inserted || 0}`);
    console.log(`Lit Pool Levels:   ${lpStoreResult.inserted || 0}`);
    console.log(`Total Duration:    ${totalDuration}s`);
    console.log('='.repeat(60));

    // Test retrieval
    console.log('\n🔍 Testing data retrieval...');
    const dpTest = await storageService.getDarkPoolLevels(ticker, 1, 10);
    const lpTest = await storageService.getLitPoolLevels(ticker, 1, 10);
    console.log(`✅ Retrieved ${dpTest.length} Dark Pool levels for today`);
    console.log(`✅ Retrieved ${lpTest.length} Lit Pool levels for today`);

    if (dpTest.length > 0) {
      console.log('\nTop Dark Pool Level:');
      console.log(`  Price: $${dpTest[0].price.toFixed(2)}`);
      console.log(`  Premium: $${dpTest[0].total_premium.toLocaleString()}`);
      console.log(`  Trades: ${dpTest[0].trades}`);
    }

    if (lpTest.length > 0) {
      console.log('\nTop Lit Pool Level:');
      console.log(`  Price: $${lpTest[0].price.toFixed(2)}`);
      console.log(`  Premium: $${lpTest[0].total_premium.toLocaleString()}`);
      console.log(`  Trades: ${lpTest[0].trades}`);
    }

    console.log('\n✅ Import complete!\n');

  } catch (error) {
    console.error('\n❌ Import failed:', error.message);
    console.error(error.stack);
    process.exit(1);
  } finally {
    await storageService.close();
  }
}

// Parse command line arguments
const args = process.argv.slice(2);
if (args.length < 2) {
  console.error('Usage: node import-historical-dp-lp.js <TICKER> <DAYS>');
  console.error('Example: node import-historical-dp-lp.js META 15');
  process.exit(1);
}

const ticker = args[0].toUpperCase();
const days = parseInt(args[1], 10);

if (isNaN(days) || days < 1 || days > 30) {
  console.error('Error: Days must be a number between 1 and 30');
  process.exit(1);
}

// Run import
importHistoricalData(ticker, days);

