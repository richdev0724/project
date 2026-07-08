# Historical Dark Pool & Lit Pool Data Guide

## Overview
This guide explains how to download and analyze historical Dark Pool and Lit Pool trading data for any ticker.

---

## Changes Made

### 1. ✅ Lowered Minimum Price Filter
**Files Updated:**
- `backend/src/services/darkPoolScanner.js`
- `backend/src/services/litFlowScanner.js`

**Change:** `MIN_STOCK_PRICE` lowered from `$100` to `$50`

This means stocks priced >= $50 will now be included in scans.

---

### 2. ✅ New Historical Data Service
**File:** `backend/src/services/historicalDarkPoolService.js`

This service fetches Dark Pool and Lit Pool trades for a specific ticker across multiple trading days.

**Key Features:**
- Fetches data for the last N trading days (skips weekends)
- Aggregates trades by price levels (same $0.15 tolerance)
- Separates Dark Pool (`trf_id` + `exchange === 4`) from Lit Pool trades
- Detailed console logging of progress
- Rate limiting to respect Polygon API limits

---

### 3. ✅ New API Endpoints

#### Historical Dark Pool Data
```
GET /api/dark-pool/historical/:ticker?days=15
```

**Example:**
```bash
curl http://localhost:3001/api/dark-pool/historical/META?days=15
```

**Response:**
```json
{
  "ticker": "META",
  "days": 15,
  "total_levels": 1250,
  "levels": [
    {
      "ticker": "META",
      "price": 594.52,
      "size": 125000,
      "total_premium": 74315000,
      "trades": 45,
      "market_session": "During Market",
      "trade_date": "2025-10-14"
    },
    ...
  ]
}
```

#### Historical Lit Pool Data
```
GET /api/lit-pool/historical/:ticker?days=15
```

**Example:**
```bash
curl http://localhost:3001/api/lit-pool/historical/META?days=15
```

**Response:** Same structure as Dark Pool endpoint

---

### 4. ✅ Download Script

**File:** `backend/scripts/download-historical-dp-lp.js`

This script downloads historical data and saves it to JSON files with comprehensive summaries.

---

## How to Download Historical Data for META

### Option 1: Using the Download Script (Recommended)

```bash
cd /Users/joelbodker/Projects/frontend-testing
node backend/scripts/download-historical-dp-lp.js META 15
```

**What it does:**
1. Fetches 15 days of Dark Pool trades for META
2. Fetches 15 days of Lit Pool trades for META
3. Saves two JSON files in `backend/data/`:
   - `META_dark_pool_15days_2025-10-14.json`
   - `META_lit_pool_15days_2025-10-14.json`
4. Displays detailed summary with:
   - Total levels, trades, volume, premium
   - Daily breakdown by date
   - File paths

**Console Output Example:**
```
============================================================
Downloading Historical Dark Pool & Lit Pool Data
============================================================
Ticker: META
Days: 15
============================================================

📊 Fetching Dark Pool data...

Fetching Dark Pool data for META on 2025-09-30...
  META 2025-09-30: 45230 total trades, 3452 dark pool trades, 125 price levels
Fetching Dark Pool data for META on 2025-10-01...
  META 2025-10-01: 48120 total trades, 3789 dark pool trades, 132 price levels
...

Total Dark Pool levels collected: 1,845

📊 Fetching Lit Pool data...

Fetching Lit Pool data for META on 2025-09-30...
  META 2025-09-30: 45230 total trades, 41778 lit pool trades, 412 price levels
...

============================================================
✅ Download Complete!
============================================================

📁 Dark Pool Data:
   File: META_dark_pool_15days_2025-10-14.json
   Path: /Users/joelbodker/Projects/frontend-testing/backend/data/META_dark_pool_15days_2025-10-14.json
   Levels: 1,845
   Trades: 54,320
   Volume: 12,450,000 shares
   Premium: $7,410.5M

📁 Lit Pool Data:
   File: META_lit_pool_15days_2025-10-14.json
   Path: /Users/joelbodker/Projects/frontend-testing/backend/data/META_lit_pool_15days_2025-10-14.json
   Levels: 5,234
   Trades: 625,890
   Volume: 145,678,000 shares
   Premium: $86,520.3M

============================================================

📊 Daily Breakdown:

Date         | DP Levels | DP Premium    | LP Levels | LP Premium
----------------------------------------------------------------------
2025-09-30   |       125 |      $512.3M  |       412 |    $5,680.2M
2025-10-01   |       132 |      $548.7M  |       428 |    $6,120.5M
...
```

### Option 2: Using the API Directly

```bash
# Dark Pool
curl http://localhost:3001/api/dark-pool/historical/META?days=15 > META_darkpool.json

# Lit Pool
curl http://localhost:3001/api/lit-pool/historical/META?days=15 > META_litpool.json
```

---

## Understanding the Data

### Dark Pool Trades
**Criteria:** `trf_id` present AND `exchange === 4`

**What it means:**
- Trades executed off public exchanges
- Reported through Trade Reporting Facilities (TRF)
- Typically institutional block trades
- Often indicate significant institutional activity

**Common characteristics:**
- Larger average trade sizes
- Less impact on public price discovery
- Popular among institutional investors avoiding slippage

### Lit Pool Trades
**Criteria:** NO `trf_id` AND `exchange !== 4`

**What it means:**
- Trades executed on public exchanges (NYSE, NASDAQ, etc.)
- Full transparency and price discovery
- Visible in Level 2 order book
- Retail and institutional trades

**Common characteristics:**
- Wide range of trade sizes
- Direct impact on price discovery
- Higher frequency of trades

### Data Fields Explained

Each level in the JSON includes:

```json
{
  "ticker": "META",           // Stock symbol
  "price": 594.52,            // Price level (rounded to $0.15 buckets)
  "size": 125000,             // Total shares traded at this level
  "total_premium": 74315000,  // Total $ value (size × price)
  "trades": 45,               // Number of individual trades
  "market_session": "During Market",  // Pre Market, During Market, After Market
  "trade_date": "2025-10-14"  // Date of trades
}
```

---

## Analyzing the Data

### Find Highest Volume Levels
```javascript
const data = require('./backend/data/META_dark_pool_15days_2025-10-14.json');

// Sort by total premium (volume × price)
const topByPremium = data.levels
  .sort((a, b) => b.total_premium - a.total_premium)
  .slice(0, 10);

console.log('Top 10 Dark Pool Levels by Premium:');
topByPremium.forEach((level, i) => {
  console.log(`${i+1}. $${level.price} - ${level.trades} trades, $${(level.total_premium/1e6).toFixed(1)}M`);
});
```

### Find Most Active Price Levels
```javascript
// Sort by number of trades
const topByTrades = data.levels
  .sort((a, b) => b.trades - a.trades)
  .slice(0, 10);

console.log('Most Active Price Levels:');
topByTrades.forEach((level, i) => {
  console.log(`${i+1}. $${level.price} - ${level.trades} trades`);
});
```

### Analyze by Market Session
```javascript
const bySession = data.levels.reduce((acc, level) => {
  if (!acc[level.market_session]) {
    acc[level.market_session] = { trades: 0, premium: 0, volume: 0 };
  }
  acc[level.market_session].trades += level.trades;
  acc[level.market_session].premium += level.total_premium;
  acc[level.market_session].volume += level.size;
  return acc;
}, {});

console.log('Activity by Market Session:', bySession);
```

### Compare Dark Pool vs Lit Pool Ratio
```javascript
const dpData = require('./backend/data/META_dark_pool_15days_2025-10-14.json');
const lpData = require('./backend/data/META_lit_pool_15days_2025-10-14.json');

const dpPremium = dpData.total_premium;
const lpPremium = lpData.total_premium;
const dpRatio = (dpPremium / (dpPremium + lpPremium) * 100).toFixed(1);

console.log(`Dark Pool: $${(dpPremium/1e6).toFixed(1)}M (${dpRatio}%)`);
console.log(`Lit Pool: $${(lpPremium/1e6).toFixed(1)}M (${(100-dpRatio).toFixed(1)}%)`);
```

---

## Performance Notes

### Time to Download
- **Single day:** ~30-60 seconds per ticker
- **15 days:** ~8-15 minutes for META (depends on trading volume)
- **Polygon API rate limits:** 200ms delay between pagination requests

### Data Volume
For a high-volume ticker like META over 15 days:
- **Dark Pool:** ~1,000-2,000 price levels
- **Lit Pool:** ~4,000-6,000 price levels
- **File sizes:** 500KB - 2MB each (JSON)

### API Limits
- Polygon fetches up to 50,000 trades per request
- Automatic pagination for more trades
- Rate limited to prevent API throttling

---

## Use Cases

### 1. Identify Support/Resistance Levels
Look for price levels with high trade counts - these indicate significant interest and may act as support/resistance.

### 2. Track Institutional Activity
High Dark Pool activity at specific levels suggests institutional accumulation or distribution.

### 3. Compare Volume Distribution
Analyze how volume is distributed between Dark Pool and Lit Pool to understand market dynamics.

### 4. Session Analysis
Identify patterns in pre-market vs. during-market vs. after-market trading.

### 5. Historical Price Level Analysis
Track which price levels consistently see high activity over time.

---

## Next Steps

1. **Run the download for META:**
   ```bash
   node backend/scripts/download-historical-dp-lp.js META 15
   ```

2. **Examine the JSON files** in `backend/data/`

3. **Verify the logic:**
   - Check that Dark Pool trades have the expected characteristics
   - Confirm Lit Pool trades represent public exchange activity
   - Review daily breakdown to ensure data completeness

4. **Analyze patterns:**
   - Find the most significant price levels
   - Compare Dark vs. Lit Pool ratios
   - Identify session-based patterns

---

## Troubleshooting

### "No data found"
- The date might be a weekend or market holiday
- Polygon API might not have data for that date yet
- Check console logs for specific errors

### "API rate limit"
- Reduce concurrent requests
- Increase delay between pagination (currently 200ms)
- Consider spreading downloads over time

### "Out of memory"
- Reduce the number of days
- Process data in smaller chunks
- Increase Node.js memory: `node --max-old-space-size=4096 ...`

---

## Summary

✅ Minimum price filter lowered to **$50**  
✅ Historical data service created  
✅ API endpoints for historical Dark Pool & Lit Pool data  
✅ Download script with comprehensive analysis  
✅ Ready to test with META for 15 days  

**To get started:**
```bash
node backend/scripts/download-historical-dp-lp.js META 15
```

This will fetch, analyze, and save 15 days of Dark Pool and Lit Pool data for META! 🚀

