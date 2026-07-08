# Dark Pool & Lit Pool Levels Implementation

## Overview
This document explains how Dark Pool and Lit Pool levels are identified, aggregated, and displayed for individual tickers on the ticker dashboard.

---

## 1. How We Identify Dark Pool vs Lit Pool Trades

### Data Source: Polygon Trades API
We use the Polygon `/v3/trades/{ticker}` endpoint which provides detailed trade-level data including:
- `trf_id`: Trade Reporting Facility ID (indicates off-exchange reporting)
- `exchange`: Exchange code where the trade occurred
- `price`: Trade price
- `size`: Trade size (shares)
- `sip_timestamp`: Timestamp in nanoseconds

### Dark Pool Logic
**Location:** `backend/src/services/darkPoolScanner.js` (lines 111-115)

```javascript
if (t.trf_id && t.exchange === 4) {
  // This is a Dark Pool trade
  if (t.price < MIN_STOCK_PRICE) continue;
} else {
  continue;
}
```

**Criteria (Confirmed by Polygon Support):**
- Trade MUST have `trf_id` (Trade Reporting Facility ID) - NOT null
- AND exchange code MUST be `4` (TRF venue)
- **BOTH conditions must be true**
- `trf_id` shows the Trade Reporting Facility where the trade took place
- Exchange 4 represents the venue where dark pool trades are reported
- These are off-exchange trades executed in dark pools/private venues
- Filtered to only include stocks priced >= $50

### Lit Pool Logic
**Location:** `backend/src/services/litFlowScanner.js` (lines 108-110)

```javascript
// Skip dark pool trades (exchange 4 AND trf_id present)
if (t.trf_id && t.exchange === 4) continue;
if (t.price < MIN_STOCK_PRICE) continue;
```

**Criteria (Confirmed by Polygon Support):**
- Any trade that does NOT match BOTH dark pool conditions
- This includes:
  - Any exchange code other than 4 (NYSE, NASDAQ, etc.)
  - Exchange 4 trades WITHOUT trf_id
  - Any trade without trf_id
- These are public exchange trades with full price discovery
- Filtered to only include stocks priced >= $50

**Important Note:** Not all stocks have significant dark pool activity every day. Seeing 0 dark pool trades for a ticker on a given day is normal.

### Aggregation Logic
Both scanners aggregate trades by price levels with a tolerance of $0.15:
```javascript
const PRICE_TOLERANCE = 0.15;
const bucket = Math.round(t.price / PRICE_TOLERANCE);
const price = bucket * PRICE_TOLERANCE;
```

Each level tracks:
- `ticker`: Stock symbol
- `price`: Aggregated price level
- `size`: Total shares traded at this level
- `total_premium`: Total dollar value (size × price)
- `trades`: Number of trades at this level
- `market_session`: Pre Market, During Market, or After Market
- `trade_date`: Date of trades

---

## 2. Backend Endpoints

### New Ticker-Specific Endpoints
**Location:** `backend/src/index.js` (lines 272-334)

#### GET `/api/dark-pool/levels/:ticker`
Retrieves Dark Pool levels for a specific ticker.

**Parameters:**
- `ticker` (path): Stock symbol (e.g., META, TSLA)
- `limit` (query, optional): Number of levels to return (default: 10)

**Response:**
```json
{
  "ticker": "META",
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
  ],
  "count": 10
}
```

#### GET `/api/lit-pool/levels/:ticker`
Retrieves Lit Pool levels for a specific ticker.

**Parameters:**
- `ticker` (path): Stock symbol (e.g., META, TSLA)
- `limit` (query, optional): Number of levels to return (default: 10)

**Response:** Same structure as Dark Pool endpoint

---

## 3. Frontend Implementation

### React Hooks

#### `useTickerDarkPoolLevels`
**Location:** `src/hooks/use-ticker-dark-pool-levels.ts`

```typescript
const { data, isLoading, isError } = useTickerDarkPoolLevels(ticker, limit)
```

**Features:**
- Uses React Query for caching and state management
- 5-minute cache time
- Auto-disabled when ticker is empty
- Returns typed response with levels array

#### `useTickerLitPoolLevels`
**Location:** `src/hooks/use-ticker-lit-pool-levels.ts`

Same API as Dark Pool hook.

### UI Components

#### `TickerDarkPool`
**Location:** `src/app/pages/options/components/ticker-dark-pool/ticker-dark-pool.tsx`

**Features:**
- Fetches real-time Dark Pool levels for the current ticker
- Displays top 10 levels sorted by total premium
- Shows price, progress bar, and formatted premium
- Handles loading and error states
- Progress bars are relative to the highest premium level

#### `TickerLitLevels`
**Location:** `src/app/pages/options/components/ticker-lit-levels/ticker-lit-levels.tsx`

Same features as TickerDarkPool.

**Display Format:**
- Ticker symbol
- Price level (e.g., $594.52)
- Visual progress bar (percentage of max premium)
- Total premium (formatted: $46M, $1.2B, etc.)

---

## 4. How It Works (Data Flow)

1. **Backend Scanners** (run every 5 minutes):
   - `darkPoolScanner.js` scans top tickers for dark pool trades
   - `litFlowScanner.js` scans top tickers for lit pool trades
   - Results are cached and stored in `scanResult` and `litFlowScanResult`

2. **API Request** (when user visits ticker page):
   - Frontend makes request to `/api/dark-pool/levels/META`
   - Backend filters cached results for META ticker
   - Returns top 10 levels sorted by premium

3. **Frontend Display**:
   - Hook fetches data and caches for 5 minutes
   - Component renders table with levels
   - Progress bars show relative strength of each level

---

## 5. Testing

### Test Dark Pool Endpoint
```bash
curl http://localhost:3001/api/dark-pool/levels/META
```

### Test Lit Pool Endpoint
```bash
curl http://localhost:3001/api/lit-pool/levels/META
```

### Test in Browser
1. Navigate to ticker page: `http://localhost:5173/options/META`
2. Scroll down to "META Dark Pool" and "META Lit Levels" sections
3. Check for real data (not hardcoded $594.52)
4. Open DevTools Network tab to see API calls

### Expected Data
- **If data exists**: You'll see real price levels and premiums
- **If no data**: "No dark pool/lit pool data available" message
- **While loading**: Spinner animation

---

## 6. Configuration

### Minimum Stock Price
Both scanners filter stocks below $100:
```javascript
const MIN_STOCK_PRICE = 100;
```

### Price Tolerance
Trades are bucketed within $0.15:
```javascript
const PRICE_TOLERANCE = 0.15;
```

### Priority Tickers
These tickers are always scanned:
```javascript
const PRIORITY_TICKERS = [
  'SPY', 'QQQ', 'IWM', 'DIA', 'VOO',
  'AAPL', 'NVDA', 'TSLA', 'META', 'NFLX',
  'AMZN', 'MSFT'
];
```

### Cache Duration
- Backend: 10 minutes (NodeCache stdTTL: 600)
- Frontend: 5 minutes (React Query staleTime: 5 * 60 * 1000)

---

## 7. Important Notes

### Data Availability
- Data is only available for tickers that were scanned
- Scanners run every 5 minutes
- If a ticker is not in the priority list and not in top 10 by volume, it may not have data

### Performance
- Endpoints use cached results (no new scans on each request)
- First request may trigger a scan if cache is empty
- Subsequent requests are instant (cached)

### Polygon API Limits
- Each scan fetches up to 50,000 trades per ticker
- Rate limiting is applied (200ms delay between pagination requests)
- Up to 20 concurrent ticker scans

---

## 8. Future Enhancements

Potential improvements:
1. **On-demand ticker scanning**: Trigger scan for specific ticker if not in cache
2. **Historical levels**: Store and display historical price levels
3. **Level strength indicators**: Show volume trends at each level
4. **Alerts**: Notify when price approaches significant dark pool levels
5. **Chart integration**: Plot levels directly on the main price chart
6. **Filtering**: Filter by market session (pre/during/after market)
7. **Export data**: Download levels as CSV/JSON

---

## Summary

✅ **Dark Pool trades**: `trf_id` present AND `exchange === 4`  
✅ **Lit Pool trades**: NO `trf_id` AND `exchange !== 4`  
✅ **Backend endpoints**: `/api/dark-pool/levels/:ticker` and `/api/lit-pool/levels/:ticker`  
✅ **Frontend components**: Real-time data now displayed on ticker dashboard  
✅ **Ready to test**: Both backend and frontend are fully implemented and integrated

