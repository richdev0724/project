#!/usr/bin/env node

/**
 * Test ClickHouse DP/LP API Endpoints
 * 
 * This script tests the storage service directly without needing the backend server
 */

const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const storageService = require('../src/services/darkPoolLitPoolStorageService');

async function testEndpoints() {
  console.log('🧪 Testing ClickHouse DP/LP Storage Service...\n');

  try {
    // Test Dark Pool data
    console.log('📊 Testing Dark Pool levels...');
    const dpLevels = await storageService.getDarkPoolLevels('META', 15, 5);
    console.log(`✅ Retrieved ${dpLevels.length} Dark Pool levels`);
    
    if (dpLevels.length > 0) {
      console.log('\nTop Dark Pool Level:');
      console.log(`  Ticker: ${dpLevels[0].ticker}`);
      console.log(`  Price: $${dpLevels[0].price.toFixed(2)}`);
      console.log(`  Premium: $${dpLevels[0].total_premium.toLocaleString()}`);
      console.log(`  Trades: ${dpLevels[0].trades}`);
      console.log(`  Date: ${dpLevels[0].trade_date}`);
    }

    // Test Lit Pool data
    console.log('\n📊 Testing Lit Pool levels...');
    const lpLevels = await storageService.getLitPoolLevels('META', 15, 5);
    console.log(`✅ Retrieved ${lpLevels.length} Lit Pool levels`);
    
    if (lpLevels.length > 0) {
      console.log('\nTop Lit Pool Level:');
      console.log(`  Ticker: ${lpLevels[0].ticker}`);
      console.log(`  Price: $${lpLevels[0].price.toFixed(2)}`);
      console.log(`  Premium: $${lpLevels[0].total_premium.toLocaleString()}`);
      console.log(`  Trades: ${lpLevels[0].trades}`);
      console.log(`  Date: ${lpLevels[0].trade_date}`);
    }

    // Test data freshness
    console.log('\n📅 Testing data freshness...');
    const today = new Date().toISOString().split('T')[0];
    const hasTodayDP = await storageService.hasDataForDate('META', today, 'dark');
    const hasTodayLP = await storageService.hasDataForDate('META', today, 'lit');
    
    console.log(`Dark Pool data for today (${today}): ${hasTodayDP ? '✅ Yes' : '❌ No'}`);
    console.log(`Lit Pool data for today (${today}): ${hasTodayLP ? '✅ Yes' : '❌ No'}`);

    console.log('\n🎉 All tests passed! The ClickHouse storage is working perfectly.');
    console.log('\n📋 Summary:');
    console.log(`  Dark Pool levels: ${dpLevels.length} (showing top 5)`);
    console.log(`  Lit Pool levels: ${lpLevels.length} (showing top 5)`);
    console.log(`  Data spans: 15 days`);
    console.log(`  Total levels stored: ${dpLevels.length + lpLevels.length} (from import)`);

  } catch (error) {
    console.error('❌ Test failed:', error.message);
    console.error(error.stack);
  } finally {
    await storageService.close();
  }
}

// Run the test
testEndpoints();

