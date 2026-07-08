# 🚀 Quick Start - Dark Pool & Lit Pool with ClickHouse

## ⚡ Get Running in 3 Steps

### Step 1: Start ClickHouse (Choose One)

**Option A - Docker (Recommended)**
```bash
docker run -d --name clickhouse-local -p 8123:8123 -p 9000:9000 -e CLICKHOUSE_DB=options clickhouse/clickhouse-server:latest
```

**Option B - Homebrew (Already Installed)**
Open a new terminal and run:
```bash
clickhouse server
```
Leave this terminal running.

**Option C - Docker Compose**
```bash
docker-compose up clickhouse -d
```

### Step 2: Test Connection
```bash
curl http://localhost:8123/ping
# Should return: Ok.
```

### Step 3: Import META Data
```bash
node backend/scripts/import-historical-dp-lp.js META 15
```

This will:
- Fetch 15 days of META trades from Polygon ✅
- Aggregate into price levels ✅  
- Store in ClickHouse ✅
- Takes ~5-6 minutes ⏱️

---

## ✅ Verify It's Working

### Test the API:
```bash
# Dark Pool data
curl "http://localhost:8080/api/dark-pool/levels/META?limit=5"

# Lit Pool data
curl "http://localhost:8080/api/lit-pool/levels/META?limit=5"
```

### Test the Frontend:
1. Open browser: http://localhost:5173
2. Navigate to META ticker page
3. Click on Dark Pool modal - should show real data!
4. Click on Lit Pool modal - should show real data!

---

## 🎯 That's It!

Your DP/LP system is now:
- ✅ Storing data in ClickHouse
- ✅ Serving via API endpoints
- ✅ Displaying in the frontend

---

## 📖 Next Steps

### Start Continuous Collection:
```bash
node backend/scripts/collect-dp-lp-continuous.js 5
```
This runs every 5 minutes during market hours (4am-8pm ET).

### Import More Tickers:
```bash
node backend/scripts/import-historical-dp-lp.js AAPL 15
node backend/scripts/import-historical-dp-lp.js GOOGL 15
node backend/scripts/import-historical-dp-lp.js TSLA 15
```

---

## 📚 Full Documentation

- **Detailed Setup**: See `CLICKHOUSE_SETUP_GUIDE.md`
- **Implementation Summary**: See `DP_LP_IMPLEMENTATION_SUMMARY.md`

---

## 🆘 Troubleshooting

**Problem**: `ECONNREFUSED` error  
**Fix**: ClickHouse isn't running. Go back to Step 1.

**Problem**: Import script hangs  
**Fix**: Normal! Polygon API is slow. Each day takes ~20-30 seconds.

**Problem**: No data in frontend  
**Fix**: Check API endpoints first (curl commands above).

---

🎊 **You're all set!** The system is production-ready.

