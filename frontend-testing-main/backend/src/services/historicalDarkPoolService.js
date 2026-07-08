const axios = require('axios');
const http = require('http');
const https = require('https');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '..', '.env') });

const POLYGON_API_KEY = process.env.POLYGON_API_KEY || 'YOUR_POLYGON_API_KEY_HERE';
const PRICE_TOLERANCE = 0.15;
const MIN_STOCK_PRICE = 50;

// one shared axios instance with keep‑alive
const axiosInstance = axios.create({
  httpAgent: new http.Agent({ keepAlive: true }),
  httpsAgent: new https.Agent({ keepAlive: true }),
  timeout: 30000,
});

// session cache per minute
const sessionCache = new Map();
function getMarketSessionCached(nanos) {
  const ms = Number(nanos) / 1e6;
  const minuteKey = Math.floor(ms / 60000);
  if (sessionCache.has(minuteKey)) return sessionCache.get(minuteKey);

  const date = new Date(ms);
  const estH = date.getUTCHours() - 5;
  const tInMin = estH * 60 + date.getUTCMinutes();
  let session = 'After Market';
  if (tInMin >= 180 && tInMin < 570) session = 'Pre Market';
  else if (tInMin >= 570 && tInMin < 960) session = 'During Market';
  sessionCache.set(minuteKey, session);
  return session;
}

// Check if a date is a weekend
function isWeekend(dateStr) {
  const date = new Date(dateStr);
  const day = date.getDay();
  return day === 0 || day === 6;
}

// Get array of trading days for the last N days
function getTradingDays(daysBack) {
  const dates = [];
  const today = new Date();
  let currentDate = new Date(today);
  
  while (dates.length < daysBack) {
    const dateStr = currentDate.toISOString().split('T')[0];
    if (!isWeekend(dateStr)) {
      dates.push(dateStr);
    }
    currentDate.setDate(currentDate.getDate() - 1);
  }
  
  return dates.reverse(); // Return oldest to newest
}

// Fetch dark pool trades for a specific ticker and date
async function fetchDarkPoolForTickerAndDate(ticker, date) {
  console.log(`Fetching Dark Pool data for ${ticker} on ${date}...`);
  const groups = new Map();
  
  // Use timestamp.gte and timestamp.lte for date range
  const startOfDay = new Date(date + 'T00:00:00Z').getTime() * 1e6; // nanoseconds
  const endOfDay = new Date(date + 'T23:59:59Z').getTime() * 1e6; // nanoseconds
  
  let url = `https://api.polygon.io/v3/trades/${ticker}`
          + `?timestamp.gte=${startOfDay}&timestamp.lte=${endOfDay}`
          + `&order=desc&limit=50000&apiKey=${POLYGON_API_KEY}`;

  let totalTrades = 0;
  let darkPoolTrades = 0;

  while (url) {
    try {
      const resp = await axiosInstance.get(url);
      const results = resp.data.results || [];
      totalTrades += results.length;

      for (const t of results) {
        // Dark Pool: trf_id present AND exchange === 4
        if (t.trf_id && t.exchange === 4) {
          if (t.price < MIN_STOCK_PRICE) continue;
          darkPoolTrades++;

          const bucket = Math.round(t.price / PRICE_TOLERANCE);
          const price = bucket * PRICE_TOLERANCE;
          const key = `${ticker}_${price.toFixed(2)}`;
          let grp = groups.get(key);
          if (!grp) {
            grp = {
              ticker,
              price,
              size: 0,
              total_premium: 0,
              trades: 0,
              market_session: getMarketSessionCached(t.sip_timestamp),
              trade_date: date
            };
            groups.set(key, grp);
          }
          grp.size += t.size;
          grp.total_premium += t.size * t.price;
          grp.trades += 1;
        }
      }

      url = resp.data.next_url
        ? `${resp.data.next_url}&apiKey=${POLYGON_API_KEY}`
        : null;
      
      if (url) await new Promise(r => setTimeout(r, 200)); // Rate limit
    } catch (error) {
      console.error(`Error fetching ${ticker} on ${date}:`, error.message);
      break;
    }
  }

  const levels = Array.from(groups.values())
    .sort((a, b) => b.total_premium - a.total_premium);

  console.log(`  ${ticker} ${date}: ${totalTrades} total trades, ${darkPoolTrades} dark pool trades, ${levels.length} price levels`);
  
  return levels;
}

// Fetch lit pool trades for a specific ticker and date
async function fetchLitPoolForTickerAndDate(ticker, date) {
  console.log(`Fetching Lit Pool data for ${ticker} on ${date}...`);
  const groups = new Map();
  
  // Use timestamp.gte and timestamp.lte for date range
  const startOfDay = new Date(date + 'T00:00:00Z').getTime() * 1e6; // nanoseconds
  const endOfDay = new Date(date + 'T23:59:59Z').getTime() * 1e6; // nanoseconds
  
  let url = `https://api.polygon.io/v3/trades/${ticker}`
          + `?timestamp.gte=${startOfDay}&timestamp.lte=${endOfDay}`
          + `&order=desc&limit=50000&apiKey=${POLYGON_API_KEY}`;

  let totalTrades = 0;
  let litPoolTrades = 0;

  while (url) {
    try {
      const resp = await axiosInstance.get(url);
      const results = resp.data.results || [];
      totalTrades += results.length;

      for (const t of results) {
        // Skip dark pool trades (exchange 4 AND trf_id present)
        if (t.trf_id && t.exchange === 4) continue;
        if (t.price < MIN_STOCK_PRICE) continue;
        litPoolTrades++;

        const bucket = Math.round(t.price / PRICE_TOLERANCE);
        const price = bucket * PRICE_TOLERANCE;
        const key = `${ticker}_${price.toFixed(2)}`;
        let grp = groups.get(key);
        if (!grp) {
          grp = {
            ticker,
            price,
            size: 0,
            total_premium: 0,
            trades: 0,
            market_session: getMarketSessionCached(t.sip_timestamp),
            trade_date: date
          };
          groups.set(key, grp);
        }
        grp.size += t.size;
        grp.total_premium += t.size * t.price;
        grp.trades += 1;
      }

      url = resp.data.next_url
        ? `${resp.data.next_url}&apiKey=${POLYGON_API_KEY}`
        : null;
      
      if (url) await new Promise(r => setTimeout(r, 200)); // Rate limit
    } catch (error) {
      console.error(`Error fetching ${ticker} on ${date}:`, error.message);
      break;
    }
  }

  const levels = Array.from(groups.values())
    .sort((a, b) => b.total_premium - a.total_premium);

  console.log(`  ${ticker} ${date}: ${totalTrades} total trades, ${litPoolTrades} lit pool trades, ${levels.length} price levels`);
  
  return levels;
}

// Fetch historical dark pool data for a ticker
async function fetchHistoricalDarkPool(ticker, daysBack = 15) {
  console.log(`\n=== Fetching ${daysBack} days of Dark Pool data for ${ticker} ===\n`);
  const dates = getTradingDays(daysBack);
  const allLevels = [];
  
  for (const date of dates) {
    const levels = await fetchDarkPoolForTickerAndDate(ticker, date);
    allLevels.push(...levels);
  }
  
  console.log(`\nTotal Dark Pool levels collected: ${allLevels.length}`);
  return allLevels;
}

// Fetch historical lit pool data for a ticker
async function fetchHistoricalLitPool(ticker, daysBack = 15) {
  console.log(`\n=== Fetching ${daysBack} days of Lit Pool data for ${ticker} ===\n`);
  const dates = getTradingDays(daysBack);
  const allLevels = [];
  
  for (const date of dates) {
    const levels = await fetchLitPoolForTickerAndDate(ticker, date);
    allLevels.push(...levels);
  }
  
  console.log(`\nTotal Lit Pool levels collected: ${allLevels.length}`);
  return allLevels;
}

module.exports = {
  fetchHistoricalDarkPool,
  fetchHistoricalLitPool,
  fetchDarkPoolForTickerAndDate,
  fetchLitPoolForTickerAndDate,
  getTradingDays
};

