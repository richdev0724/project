#!/usr/bin/env node

/**
 * Daily PCR Cron Job
 * Runs at 4:30 PM ET (21:30 UTC) to calculate PCR for the completed trading day
 * Market close: 4:00 PM ET (21:00 UTC)
 * Cron runs: 4:30 PM ET (21:30 UTC)
 */

const axios = require("axios");

// Configuration
const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:3001";
const TICKERS = ["SPY"];

/**
 * Calculate PCR for a ticker
 */
async function calculatePCR(ticker) {
  try {
    console.log(`\n🔄 Processing ${ticker}...`);
    
    // Calculate both Volume and OI PCR
    const response = await axios.get(`${BACKEND_URL}/api/pcr/${ticker}/both`);
    const data = response.data;
    
    console.log(`✅ ${ticker} PCR Calculated:`);
    console.log(`   Date: ${data.date}`);
    console.log(`   Volume PCR: ${data.volume}`);
    console.log(`   OI PCR: ${data.oi}`);
    
    return data;
    
  } catch (error) {
    console.error(`❌ Error processing ${ticker}:`, error.message);
    throw error;
  }
}

/**
 * Main function to run the cron job
 */
async function main() {
  const startTime = new Date();
  console.log(`\n🚀 Starting Daily PCR Calculation`);
  console.log(`   Time: ${startTime.toISOString()}`);
  console.log(`   Tickers: ${TICKERS.join(", ")}`);
  
  const results = {
    success: [],
    failed: []
  };

  for (const ticker of TICKERS) {
    try {
      const data = await calculatePCR(ticker);
      results.success.push({ ticker, data });
    } catch (error) {
      results.failed.push({ ticker, error: error.message });
    }
  }

  const endTime = new Date();
  const duration = (endTime - startTime) / 1000;
  
  console.log(`\n📊 Daily PCR Calculation Complete`);
  console.log(`   Duration: ${duration.toFixed(2)} seconds`);
  console.log(`   Successful: ${results.success.length}/${TICKERS.length}`);
  console.log(`   Failed: ${results.failed.length}/${TICKERS.length}`);
  
  if (results.failed.length > 0) {
    console.log(`\n❌ Failed Tickers:`);
    results.failed.forEach(({ ticker, error }) => {
      console.log(`   ${ticker}: ${error}`);
    });
  }
  
  if (results.success.length === TICKERS.length) {
    console.log(`\n🎉 All tickers processed successfully!`);
    process.exit(0);
  } else {
    console.log(`\n⚠️  Some tickers failed. Check logs above.`);
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