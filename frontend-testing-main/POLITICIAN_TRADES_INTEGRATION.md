# Politician Trades Integration

## Overview
Successfully integrated 18,947 historical politician trading records from 2012-2024 using the Capitol Gains open-source project.

## Data Statistics

### Volume
- **Total Trades**: 18,947
- **Unique Politicians**: 193
- **Unique Tickers**: 2,055
- **Date Range**: September 13, 2012 to June 6, 2024

### Top Politicians by Trade Count
1. Josh Gottheimer - 1,874 trades
2. Thomas Tuberville - 1,183 trades
3. Susie Lee - 1,011 trades
4. Daniel Goldman - 962 trades
5. Gilbert Cisneros - 850 trades

### Most Traded Tickers
1. AAPL - 339 trades
2. MSFT - 286 trades
3. NVDA - 189 trades
4. AMZN - 161 trades
5. DIS - 154 trades

## API Endpoints

### GET /api/politician-trades
Get all politician trades with optional pagination.

**Query Parameters:**
- `limit` (optional): Number of records to return (default: all)
- `offset` (optional): Starting position (default: 0)

**Response:**
```json
{
  "success": true,
  "data": [...],
  "count": 100,
  "total": 18947,
  "hasMore": true
}
```

**Performance:** ~11ms with caching

### GET /api/politician-trades/:politician
Get trades for a specific politician.

**Example:** `/api/politician-trades/Nancy%20Pelosi`

### GET /api/politician-trades/:politician/portfolio
Get portfolio analysis for a politician.

**Returns:**
- Holdings breakdown
- Sector allocation
- Trading metrics
- Historical value chart data

### GET /api/politician-trades/statistics
Get overall statistics about the dataset.

### POST /api/politician-trades/reload
Reload data from JSON file (admin use).

### POST /api/politician-trades/clear-cache
Clear cache and reload data.

## Frontend Features

### React Query Hooks

#### `useAllPoliticianTrades()`
Loads all trades at once. Use for full dataset access.

```typescript
const { data: trades, isLoading } = useAllPoliticianTrades();
```

#### `usePaginatedPoliticianTrades(limit)`
Infinite query for loading trades in batches.

```typescript
const { 
  data, 
  fetchNextPage, 
  hasNextPage 
} = usePaginatedPoliticianTrades(100);
```

#### `usePoliticianTrades(politicianName)`
Load trades for a specific politician.

```typescript
const { data: trades } = usePoliticianTrades('Nancy Pelosi');
```

#### `usePoliticianPortfolio(politicianName)`
Load portfolio analysis for a politician.

```typescript
const { data: portfolio } = usePoliticianPortfolio('Nancy Pelosi');
```

### Caching
- **Stale Time**: 6 hours
- **Garbage Collection**: 24 hours
- **Backend Cache**: In-memory, loads on startup
- **API Response Time**: ~11ms (cached)

## Data Source

### Capitol Gains Project
- **Repository**: https://github.com/sourcesync/capitol-gains
- **License**: Open source
- **Data Files**: 
  - `house.json` - 14,099 House trades
  - `senate.json` - 4,848 Senate trades

### Import Process
1. Clone Capitol Gains repository
2. Copy data files to `backend/data/`
3. Run import script: `node backend/scripts/import-politician-trades.js`
4. Data is transformed and saved as `politician-trades.json`
5. Backend loads data on startup

### Data Structure
Each trade includes:
- Politician name, position
- Ticker symbol, asset name
- Trade type (Purchase/Sale)
- Amount range (min/max)
- Trade date, disclosure date
- Stock price at trade time
- Document ID for reference

## Performance Optimizations

### Backend
✅ File-based storage (18,947 trades in JSON)
✅ In-memory caching after first load
✅ Pagination support (limit/offset)
✅ ~11ms API response time

### Frontend
✅ React Query for automatic caching
✅ 6-hour stale time (reduces API calls)
✅ Infinite scroll support
✅ Selective loading (only load what's needed)

## Future Enhancements

### Data Enrichment
- [ ] Add party affiliation data
- [ ] Add committee memberships
- [ ] Add stock performance since trade
- [ ] Add disclosure lag analysis

### Live Updates
- [ ] Schedule daily updates from Capitol Gains
- [ ] Web scraping from capitoltrades.com
- [ ] Real-time notifications for new trades

### Analytics
- [ ] Trading performance vs S&P 500
- [ ] Sector bias analysis
- [ ] Timing analysis (buy before/after events)
- [ ] Correlation with committee membership

## Updating Data

### Manual Update
```bash
# 1. Pull latest data
cd /tmp/capitol-gains
git pull origin main

# 2. Re-run import
cd ~/Projects/frontend-testing/backend
node scripts/import-politician-trades.js

# 3. Restart backend
# Backend will auto-reload data on startup
```

### Automated Updates (Future)
Set up a cron job to:
1. Pull latest Capitol Gains data
2. Run import script
3. Trigger backend reload via API

## Files Modified

### Backend
- `backend/src/services/politicianTradesService.js` - Main service
- `backend/src/routes/politicianTrades.js` - API routes
- `backend/scripts/import-politician-trades.js` - Import script
- `backend/data/politician-trades.json` - Data file (18,947 trades)
- `backend/data/politician-trades-stats.json` - Statistics

### Frontend
- `src/api/politician-trades.service.ts` - API client
- `src/hooks/use-politician-trades.ts` - React Query hooks
- `src/app/pages/politicians/index.tsx` - Main page
- `src/app/pages/politicians/components/PoliticianPortfolio.tsx` - Portfolio view

## Troubleshooting

### Slow Loading
- Use pagination: `?limit=100&offset=0`
- Check if backend cache is loaded
- Verify React Query cache settings

### Portfolio Errors
- Ensure politician name matches exactly
- Check if politician has any trades
- Verify portfolio data includes required fields

### Missing Data
- Run reload endpoint: `POST /api/politician-trades/reload`
- Check backend logs for load errors
- Verify JSON files exist in `backend/data/`

## Support

For issues or questions:
1. Check backend logs
2. Verify API endpoints with curl
3. Check React Query DevTools
4. Review import script logs

