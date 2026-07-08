#!/usr/bin/env node

/**
 * Daily Premiums Cron Job
 * Runs 45 minutes after market close to calculate and store daily premiums
 * Market close: 4:00 PM ET (21:00 UTC)
 * Cron runs: 4:45 PM ET (21:45 UTC)
 */

const axios = require("axios");

// Configuration
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:3001";
const TICKERS = ["SPY"]; // Start with SPY only as requested

/**
 * Calculate daily premiums for a ticker
 */
async function calculatePremiums(ticker) {
  try {
    console.log(`
🔄 Processing ${ticker}...`);
    
    // Calculate and store premiums
    const response = await axios.post(`${BACKEND_URL}/api/daily-premiums/${ticker}/calculate-and-store-polygon`);
    
    if (!response.data.success) {
      throw new Error(`API error: ${response.data.error}`);
    }
    
    const data = response.data.data;
    
    console.log(`✅ ${ticker} Premiums Calculated:`);
    console.log(`   Date: ${data.date}`);
    console.log(`   Net Call Premium: $${data.netCallPremium.toLocaleString()}`);
    console.log(`   Net Put Premium: $${data.netPutPremium.toLocaleString()}`);
    console.log(`   Net Premium: $${data.netPremium.toLocaleString()}`);
    console.log(`   Contracts Processed: ${data.tradesProcessed}`);
    
    return data;
    
  } catch (error) {
    console.error(`❌ Error processing ${ticker}:`, error.message);
    throw error;
  }
}

/**
 * Main execution function
 */
async function main() {
  const startTime = new Date();
  console.log(`
🚀 Starting Daily Premiums Calculation`);
  console.log(`   Time: ${startTime.toISOString()}`);
  console.log(`   Tickers: ${TICKERS.join(", ")}`);
  
  const results = {
    success: [],
    failed: []
  };
  
  // Process each ticker
  for (const ticker of TICKERS) {
    try {
      const data = await calculatePremiums(ticker);
      results.success.push({ ticker, data });
    } catch (error) {
      results.failed.push({ ticker, error: error.message });
    }
  }
  
  const endTime = new Date();
  const duration = (endTime - startTime) / 1000;
  
  console.log(`
📊 Daily Premiums Calculation Complete`);
  console.log(`   Duration: ${duration.toFixed(2)} seconds`);
  console.log(`   Successful: ${results.success.length}/${TICKERS.length}`);
  console.log(`   Failed: ${results.failed.length}/${TICKERS.length}`);
  
  if (results.failed.length > 0) {
    console.log(`
❌ Failed Tickers:`);
    results.failed.forEach(({ ticker, error }) => {
      console.log(`   ${ticker}: ${error}`);
    });
  }
  
  if (results.success.length === TICKERS.length) {
    console.log(`
🎉 All tickers processed successfully!`);
    process.exit(0);
  } else {
    console.log(`
⚠️  Some tickers failed. Check logs above.`);
    process.exit(1);
  }
}

// Handle errors
process.on("unhandledRejection", (error) => {
  console.error("❌ Unhandled rejection:", error);
  process.exit(1);
});

process.on("uncaughtException", (error) => {
  console.error("❌ Uncaught exception:", error);
  process.exit(1);
});

// Run the main function
main().catch((error) => {
  console.error("❌ Main function error:", error);
  process.exit(1);
});
