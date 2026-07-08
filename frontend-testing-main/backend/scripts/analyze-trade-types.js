/**
 * Script to analyze trade types from Polygon
 * This will help us understand the actual trade data structure
 */

const axios = require('axios');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;
const ticker = 'META';

async function analyzeTradeTypes() {
  const today = new Date().toISOString().split('T')[0];
  const url = `https://api.polygon.io/v3/trades/${ticker}?timestamp=${today}&order=desc&limit=1000&apiKey=${POLYGON_API_KEY}`;
  
  console.log('\n=== Analyzing Trade Types for', ticker, '===\n');
  console.log(`Fetching 1000 trades from today (${today})...\n`);
  
  try {
    const resp = await axios.get(url);
    const trades = resp.data.results || [];
    
    console.log(`Total trades fetched: ${trades.length}\n`);
    
    // Analyze exchanges
    const exchangeCounts = {};
    const trfIdCounts = { with_trf: 0, without_trf: 0 };
    const combinations = {};
    
    trades.forEach(t => {
      // Count exchanges
      const ex = t.exchange || 'unknown';
      exchangeCounts[ex] = (exchangeCounts[ex] || 0) + 1;
      
      // Count trf_id presence
      if (t.trf_id) {
        trfIdCounts.with_trf++;
      } else {
        trfIdCounts.without_trf++;
      }
      
      // Count combinations
      const key = `ex:${ex} + trf:${t.trf_id ? 'YES' : 'NO'}`;
      combinations[key] = (combinations[key] || 0) + 1;
    });
    
    // Display results
    console.log('📊 Exchange Distribution:');
    console.log('----------------------------');
    Object.entries(exchangeCounts)
      .sort((a, b) => b[1] - a[1])
      .forEach(([ex, count]) => {
        const pct = ((count / trades.length) * 100).toFixed(1);
        console.log(`  Exchange ${String(ex).padStart(2)}: ${String(count).padStart(4)} trades (${pct}%)`);
      });
    
    console.log('\n📊 TRF ID Distribution:');
    console.log('----------------------------');
    console.log(`  With TRF ID:    ${trfIdCounts.with_trf} trades (${((trfIdCounts.with_trf / trades.length) * 100).toFixed(1)}%)`);
    console.log(`  Without TRF ID: ${trfIdCounts.without_trf} trades (${((trfIdCounts.without_trf / trades.length) * 100).toFixed(1)}%)`);
    
    console.log('\n📊 Exchange + TRF Combinations:');
    console.log('----------------------------');
    Object.entries(combinations)
      .sort((a, b) => b[1] - a[1])
      .forEach(([combo, count]) => {
        const pct = ((count / trades.length) * 100).toFixed(1);
        console.log(`  ${combo.padEnd(25)}: ${String(count).padStart(4)} trades (${pct}%)`);
      });
    
    // Show sample trades with TRF
    console.log('\n📋 Sample trades WITH trf_id:');
    console.log('----------------------------');
    const withTrf = trades.filter(t => t.trf_id).slice(0, 5);
    if (withTrf.length > 0) {
      withTrf.forEach((t, i) => {
        console.log(`${i + 1}. Price: $${t.price}, Size: ${t.size}, Exchange: ${t.exchange}, TRF: ${t.trf_id}`);
      });
    } else {
      console.log('  No trades found with trf_id');
    }
    
    // Show sample trades WITHOUT TRF
    console.log('\n📋 Sample trades WITHOUT trf_id (Lit Pool):');
    console.log('----------------------------');
    const withoutTrf = trades.filter(t => !t.trf_id).slice(0, 5);
    withoutTrf.forEach((t, i) => {
      console.log(`${i + 1}. Price: $${t.price}, Size: ${t.size}, Exchange: ${t.exchange}, TRF: ${t.trf_id || 'null'}`);
    });
    
    // Check specific Dark Pool logic
    console.log('\n🔍 Dark Pool Logic Test (trf_id && exchange === 4):');
    console.log('----------------------------');
    const darkPoolCount = trades.filter(t => t.trf_id && t.exchange === 4).length;
    console.log(`  Matches: ${darkPoolCount} trades (${((darkPoolCount / trades.length) * 100).toFixed(1)}%)`);
    
    // Alternative Dark Pool logic tests
    console.log('\n🔍 Alternative Dark Pool Criteria:');
    console.log('----------------------------');
    console.log(`  trf_id present:           ${trades.filter(t => t.trf_id).length} trades`);
    console.log(`  exchange === 4:           ${trades.filter(t => t.exchange === 4).length} trades`);
    console.log(`  exchange >= 200:          ${trades.filter(t => t.exchange >= 200).length} trades`);
    console.log(`  exchange in [4,15,16,17]: ${trades.filter(t => [4, 15, 16, 17].includes(t.exchange)).length} trades`);
    
    console.log('\n=== Analysis Complete ===\n');
    
  } catch (error) {
    console.error('❌ Error:', error.message);
  }
}

analyzeTradeTypes();

