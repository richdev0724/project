const axios = require('axios');
const http = require('http');
const https = require('https');
const NodeCache = require('node-cache');
let limit;
(async () => {
  const pLimit = await import('p-limit');
  limit = pLimit.default(20);
})();
require('dotenv').config();

// ——— Configuration —————————————————————————————————————————————
const POLYGON_API_KEY = process.env.POLYGON_API_KEY || 'YOUR_POLYGON_API_KEY_HERE';
const TOP_TICKERS_COUNT = 10;
const TOP_TRADES_COUNT = 10;
const SCAN_INTERVAL_MIN = 5;
const PRICE_TOLERANCE = 0.15;
const MIN_STOCK_PRICE = 50;
const PRIORITY_TICKERS = [
  'SPY', 'QQQ', 'IWM', 'DIA', 'VOO',
  'AAPL', 'NVDA', 'TSLA', 'META', 'NFLX',
  'AMZN', 'MSFT'
];

// lightweight cache & concurrency limiter
const cache = new NodeCache({ stdTTL: 600 });
const DEBUG = false;  // set true to enable intra‑loop logs

// one shared axios instance with keep‑alive
const axiosInstance = axios.create({
  httpAgent: new http.Agent({ keepAlive: true }),
  httpsAgent: new https.Agent({ keepAlive: true }),
  timeout: 30000,
});

// ——— Utilities ———————————————————————————————————————————————

// format large numbers
const formatNumber = n =>
  n >= 1e9 ? (n/1e9).toFixed(1)+'B'
  : n >= 1e6 ? (n/1e6).toFixed(1)+'M'
  : n >= 1e3 ? (n/1e3).toFixed(1)+'K'
  : n.toFixed(1);

// get previous date string
const getPreviousDate = d => {
  const dt = new Date(d);
  dt.setDate(dt.getDate() - 1);
  return dt.toISOString().split('T')[0];
};

// session cache per minute‑bucket
const sessionCache = new Map();
function getMarketSessionCached(nanos) {
  const ms = Number(nanos) / 1e6;
  const minuteKey = Math.floor(ms/60000);
  if (sessionCache.has(minuteKey)) return sessionCache.get(minuteKey);

  const date = new Date(ms);
  const hoursUTC = date.getUTCHours();
  const minutes = date.getUTCMinutes();
  const estH = hoursUTC - 5; // UTC→EST
  const tInMin = estH * 60 + minutes;
  let session = 'After Market';
  if (tInMin >= 180 && tInMin < 570) session = 'Pre Market';
  else if (tInMin >= 570 && tInMin < 960) session = 'During Market';
  sessionCache.set(minuteKey, session);
  return session;
}

// check market status
async function checkMarketStatus() {
  try {
    const { data } = await axiosInstance.get(
      `https://api.polygon.io/v1/marketstatus/now?apiKey=${POLYGON_API_KEY}`
    );
    return data.market === 'open' || data.market === 'extended-hours';
  } catch {
    return false;
  }
}

// find the most recent trading day (YYYY-MM-DD)
async function getMostRecentTradingDay() {
  let dt = new Date();
  if (!await checkMarketStatus()) dt.setDate(dt.getDate() - 1);
  while ([0,6].includes(dt.getDay())) dt.setDate(dt.getDate() - 1);
  return dt.toISOString().split('T')[0];
}

// fetch & inline‑aggregate dark‑pool trades for one ticker
async function fetchAndAggregateForTicker(ticker) {
  const cacheKey = `dpAgg_${ticker}`;
  if (cache.has(cacheKey)) return cache.get(cacheKey);

  const groups = new Map();
  let date = await getMostRecentTradingDay();
  let attempts = 0, MAX_ATT = 5;

  while (attempts < MAX_ATT && groups.size === 0) {
    let url = `https://api.polygon.io/v3/trades/${ticker}`
            + `?timestamp=${date}&order=desc&limit=50000&apiKey=${POLYGON_API_KEY}`;

    while (url) {
      if (DEBUG) console.log(`→ [${ticker}] GET ${url}`);
      const resp = await axiosInstance.get(url);
      const results = resp.data.results || [];

      // inline aggregation
      for (const t of results) {
        if (t.trf_id && t.exchange === 4) {
          if (t.price < MIN_STOCK_PRICE) continue;
        } else {
          continue;
        }

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
      // light throttle
      if (url) await new Promise(r => setTimeout(r, 200));
    }

    if (groups.size === 0) {
      attempts++;
      date = getPreviousDate(date);
    }
  }

  const arr = Array.from(groups.values())
        .sort((a,b) => b.total_premium - a.total_premium);
  cache.set(cacheKey, arr);
  return arr;
}

// get top tickers by volume (excluding priority)
async function getTopTickers() {
  const key = 'top_tkers';
  if (cache.has(key)) return cache.get(key);
  try {
    const { data } = await axiosInstance.get(
      `https://api.polygon.io/v2/snapshot/locale/us/markets/stocks/tickers?apiKey=${POLYGON_API_KEY}`
    );
    const top = (data.tickers||[])
      .filter(t => t.day?.v > 0)
      .sort((a,b) => b.day.v - a.day.v)
      .map(t => t.ticker)
      .filter(t => !PRIORITY_TICKERS.includes(t))
      .slice(0, TOP_TICKERS_COUNT);
    cache.set(key, top);
    return top;
  } catch {
    return [];
  }
}

// scan all tickers, aggregate & pick top trades
async function scanMarketForDarkPoolTrades() {
  if (DEBUG) console.log('Starting full market scan…');
  
  // Wait for limit to be initialized
  while (!limit) {
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  
  const topVol = await getTopTickers();
  const allList = [...PRIORITY_TICKERS, ...topVol];

  // run up to 20 fetches in flight
  const promises = allList.map(t =>
    limit(() => fetchAndAggregateForTicker(t))
  );
  const results = await Promise.all(promises);

  // flatten & sort
  const allGroups = results.flat();
  return allGroups
    .sort((a,b) => b.total_premium - a.total_premium)
    .slice(0, TOP_TRADES_COUNT);
}

// main runner
async function runDarkPoolScan() {
  try {
    if (DEBUG) console.log('Running dark pool scan…');
    const topTrades = await scanMarketForDarkPoolTrades();
    if (DEBUG) {
      if (topTrades.length) {
        console.log('\nTop trades by premium:');
        topTrades.forEach((trade, index) => {
          console.log(`${index + 1}. ${trade.ticker}: $${formatNumber(trade.total_premium)} @ $${trade.price.toFixed(2)} (${trade.market_session})`);
        });
      } else {
        console.warn('No trades found this scan.');
      }
    }
    return topTrades;
  } catch (err) {
    console.error('Scan error:', err);
    return [];
  }
}

console.log(`Dark Pool Scanner started; scanning every ${SCAN_INTERVAL_MIN} min.`);

module.exports = { runDarkPoolScan, scanMarketForDarkPoolTrades }; 