const express = require('express');
const cors = require('cors');
const http = require('http');
const socketIo = require('socket.io');
const { runDarkPoolScan } = require('./services/darkPoolScanner');
const { runLitFlowScan } = require('./services/litFlowScanner');
const whaleFlowRouter = require('./routes/whaleFlow');
const whaleTradesRouter = require('./routes/whaleTrades');
const gammaExposureRouter = require('./routes/gammaExposure');
const newsRouter = require('./routes/news');
const flowRouter = require('./routes/flow');
const pcrRouter = require('./routes/pcr');
const netPremiumsRouter = require('./routes/netPremiums');
const liveOptionsRouter = require('./routes/liveOptions');
const dailyPremiumsRouter = require('./routes/dailyPremiums');
const politicianTradesRouter = require('./routes/politicianTrades');
const insiderTradesRouter = require('./routes/insiderTrades');
const analystRatingsRouter = require('./routes/analystRatings');
const earningsRouter = require('./routes/earnings');
const economicCalendarRouter = require('./routes/economicCalendar');
const topNetImpactRouter = require('./routes/topNetImpact');
const marketTideRouter = require('./routes/marketTide');
const tickerTideRouter = require('./routes/tickerTide');
const timeframeDataRouter = require('./routes/timeframeData');
const unusualWhalesNetPremiumsRouter = require('./routes/unusualWhalesNetPremiums');
const unusualWhalesComprehensiveRouter = require('./routes/unusualWhalesComprehensiveRoutes.cjs');
const screenshotRouter = require('./routes/screenshot');
const axios = require('axios');
const { fetchMarketPrices, fetchSectorSnapshots } = require('./services/marketPriceService');
const { getTickerDetails } = require('./services/tickerDetailsService');
const { logRawTradesFromFlowTable, testConnection } = require('./services/optionsFlowService');
const logoCacheService = require('./services/logoCacheService');
const allTickersService = require('./services/allTickersService');
const bulkCompanyProfileService = require('./services/bulkCompanyProfileService');
const { startTimeframeCronJobs, getCronJobStatus } = require('./services/timeframeCronService');

require('dotenv').config();

// Initialize express and HTTP server
const app = express();
const server = http.createServer(app);

// Configure CORS for both Express and Socket.IO
const corsOptions = {
  origin: function (origin, callback) {
    // Allow requests with no origin (like mobile apps or curl requests)
    if (!origin) return callback(null, true);
    
    // Get allowed origins from environment variable or use defaults
    const envOrigins = process.env.ALLOWED_ORIGINS ? process.env.ALLOWED_ORIGINS.split(',') : [];
    const allowedOrigins = [
      'http://localhost:5173',      // Vite default dev server
      'http://localhost:3000',      // Alternative local development
      'https://localhost:5173',     // HTTPS local
      'https://localhost:3000',     // HTTPS local alternative
      ...envOrigins                 // Production domains from environment
    ];
    
    // Allow any ngrok.io or ngrok-free.app subdomain, Railway, Render, and Vercel domains
    if (
      origin.match(/^https?:\/\/[a-zA-Z0-9-]+\.ngrok(?:-free)?\.(?:io|app)$/) ||
      origin.match(/^https:\/\/[a-zA-Z0-9-]+\.up\.railway\.app$/) ||
      origin.match(/^https:\/\/[a-zA-Z0-9-]+\.onrender\.com$/) ||
      origin.match(/^https:\/\/[a-zA-Z0-9-]+\.vercel\.app$/) ||
      allowedOrigins.indexOf(origin) !== -1
    ) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  methods: ['GET', 'POST', 'OPTIONS'],
  credentials: true,
  optionsSuccessStatus: 204
};

// Apply CORS to Express
app.use(cors(corsOptions));
app.use(express.json());

// Initialize Socket.IO with CORS
const io = socketIo(server, {
  cors: corsOptions
});

// Import real-time flow monitoring service
const { startFlowMonitoring, stopFlowMonitoring } = require('./services/realTimeFlowService');

// Socket.IO connection handling
io.on('connection', (socket) => {
  console.log('Client connected:', socket.id);
  
  socket.on('startFlowMonitoring', () => {
    console.log('Starting real-time flow monitoring for client:', socket.id);
    startFlowMonitoring(io, socket.id);
  });
  
  socket.on('stopFlowMonitoring', () => {
    console.log('Stopping real-time flow monitoring for client:', socket.id);
    stopFlowMonitoring(socket.id);
  });
  
  socket.on('disconnect', () => {
    console.log('Client disconnected:', socket.id);
    // Auto-stop monitoring when client disconnects
    stopFlowMonitoring(socket.id);
  });
});

// Routes
app.use('/api/whale-flow', whaleFlowRouter);
app.use('/api/whale-trades', whaleTradesRouter);
app.use('/api/gamma-exposure', gammaExposureRouter);
app.use('/api/news', newsRouter);
app.use('/api/flow', flowRouter);
app.use('/api/pcr', pcrRouter);
app.use('/api/net-premiums', netPremiumsRouter);
app.use('/api/live-options', liveOptionsRouter);
app.use('/api/daily-premiums', dailyPremiumsRouter);
app.use('/api/politician-trades', politicianTradesRouter);
app.use('/api/insider-trades', insiderTradesRouter);
app.use('/api/analyst-ratings', analystRatingsRouter);
app.use('/api/earnings', earningsRouter);
app.use('/api/economic-calendar', economicCalendarRouter);
app.use('/api/top-net-impact', topNetImpactRouter);
app.use('/api/market-tide', marketTideRouter);
app.use('/api/ticker-tide', tickerTideRouter);
app.use('/api/timeframe-data', timeframeDataRouter);
app.use('/api/unusual-whales/net-premiums', unusualWhalesNetPremiumsRouter);
app.use('/api/unusual-whales/comprehensive-premiums', unusualWhalesComprehensiveRouter);
app.use('/api/screenshot', screenshotRouter);

// Temporary comprehensive premiums endpoint for testing
app.get('/api/unusual-whales/comprehensive-premiums-test/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { date } = req.query;
    
    if (!date) {
      return res.status(400).json({
        success: false,
        error: 'Date parameter is required (YYYY-MM-DD format)'
      });
    }
    
    console.log(`📊 API Request: Comprehensive premiums test for ${ticker} on ${date}`);
    
    const { getComprehensivePremiumData } = require('./services/unusualWhalesComprehensiveService');
    const data = await getComprehensivePremiumData(ticker.toUpperCase(), date);
    
    if (data.success) {
      res.json({
        success: true,
        data: {
          ticker: data.ticker,
          date: data.date,
          totalCallPremium: data.totalCallPremium,
          totalPutPremium: data.totalPutPremium,
          netCallPremium: data.netCallPremium,
          netPutPremium: data.netPutPremium,
          calculatedNetPremium: data.calculatedNetPremium,
          totalTicks: data.totalTicks
        }
      });
    } else {
      res.status(404).json({
        success: false,
        error: data.error || 'No data available for the specified date'
      });
    }
    
  } catch (error) {
    console.error('❌ Error in comprehensive-premiums-test endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

/**
 * POST /api/unusual-whales/comprehensive-premiums/daily-update
 * Daily endpoint to fetch and store comprehensive premium data for all tickers
 * This runs at the end of each trading day
 */
app.post('/api/unusual-whales/comprehensive-premiums/daily-update', async (req, res) => {
  try {
    const { tickers, date } = req.body;
    
    // Use today's date if not provided
    const targetDate = date || new Date().toISOString().split('T')[0];
    
    console.log(`📊 Daily comprehensive premiums update for ${targetDate}`);
    console.log(`📊 Tickers to process: ${tickers ? tickers.length : 'all'}`);
    
    // Import services
    const { getComprehensivePremiumData } = require('./services/unusualWhalesComprehensiveService');
    const comprehensivePremiumsStorageService = require('./services/comprehensivePremiumsStorageService');
    
    // Test ClickHouse connection
    const isConnected = await comprehensivePremiumsStorageService.testConnection();
    if (!isConnected) {
      return res.status(503).json({
        success: false,
        error: 'ClickHouse is not available. Please ensure ClickHouse is running.'
      });
    }
    
    // Create table if it doesn't exist
    await comprehensivePremiumsStorageService.createTable();
    
    // Default tickers if none provided (you can expand this list)
    const defaultTickers = ['META', 'AAPL', 'GOOGL', 'MSFT', 'TSLA', 'NVDA', 'AMZN', 'NFLX', 'AMD', 'INTC'];
    const tickersToProcess = tickers || defaultTickers;
    
    const results = [];
    let successCount = 0;
    let errorCount = 0;
    
    // Process each ticker
    for (const ticker of tickersToProcess) {
      try {
        console.log(`🔄 Processing ${ticker}...`);
        
        // Fetch comprehensive premium data
        const data = await getComprehensivePremiumData(ticker.toUpperCase(), targetDate);
        
        if (data.success) {
          // Clear existing data for this ticker and date
          await comprehensivePremiumsStorageService.clearTickerData(ticker);
          
          // Store new data
          await comprehensivePremiumsStorageService.storeComprehensivePremiumData(data);
          
          results.push({
            ticker: ticker.toUpperCase(),
            date: targetDate,
            success: true,
            data: {
              totalCallPremium: data.totalCallPremium,
              totalPutPremium: data.totalPutPremium,
              netCallPremium: data.netCallPremium,
              netPutPremium: data.netPutPremium,
              calculatedNetPremium: data.calculatedNetPremium,
              totalTicks: data.totalTicks
            }
          });
          
          successCount++;
          console.log(`✅ Successfully processed ${ticker}`);
        } else {
          results.push({
            ticker: ticker.toUpperCase(),
            date: targetDate,
            success: false,
            error: data.error || 'No data available'
          });
          errorCount++;
          console.log(`❌ Failed to process ${ticker}: ${data.error}`);
        }
        
        // Add small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 500));
        
      } catch (error) {
        console.error(`❌ Error processing ${ticker}:`, error.message);
        results.push({
          ticker: ticker.toUpperCase(),
          date: targetDate,
          success: false,
          error: error.message
        });
        errorCount++;
      }
    }
    
    // Get storage statistics
    const stats = await comprehensivePremiumsStorageService.getStorageStatistics();
    
    res.json({
      success: true,
      message: `Daily update completed for ${targetDate}`,
      summary: {
        date: targetDate,
        totalTickers: tickersToProcess.length,
        successCount,
        errorCount,
        totalRecordsInDB: stats.totalRecords
      },
      results
    });
    
  } catch (error) {
    console.error('❌ Error in daily update endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error',
      details: error.message
    });
  }
});

/**
 * GET /api/unusual-whales/comprehensive-premiums/:ticker/history
 * Get comprehensive premium data from ClickHouse storage for the modal
 */
app.get('/api/unusual-whales/comprehensive-premiums/:ticker/history', async (req, res) => {
  try {
    const { ticker } = req.params;
    const { days = 15 } = req.query;
    
    console.log(`📊 API Request: Get comprehensive premiums history for ${ticker} - Last ${days} days`);
    
    // Import storage service
    const comprehensivePremiumsStorageService = require('./services/comprehensivePremiumsStorageService');
    
    // Test ClickHouse connection first
    const isConnected = await comprehensivePremiumsStorageService.testConnection();
    if (!isConnected) {
      return res.status(503).json({
        success: false,
        error: 'ClickHouse is not available. Please ensure ClickHouse is running.'
      });
    }
    
    const data = await comprehensivePremiumsStorageService.getComprehensivePremiumData(ticker.toUpperCase(), parseInt(days));
    
    if (data.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'No data found in storage for this ticker'
      });
    }
    
    // Format data for the modal
    const formattedData = data.map(item => ({
      date: item.date,
      ncp: item.netCallPremium,
      npp: item.netPutPremium,
      callPremium: item.totalCallPremium,
      putPremium: item.totalPutPremium,
      netPremium: item.calculatedNetPremium,
      totalTicks: item.totalTicks
    }));
    
    res.json({
      success: true,
      ticker: ticker.toUpperCase(),
      data: formattedData,
      count: formattedData.length
    });
    
  } catch (error) {
    console.error('❌ Error in history endpoint:', error.message);
    res.status(500).json({
      success: false,
      error: 'Internal server error',
      details: error.message
    });
  }
});

app.post('/api/timeframe-cron/start', (req, res) => {
  try {
    startTimeframeCronJobs()
    res.json({
      success: true,
      message: 'Timeframe cron jobs started successfully',
      status: getCronJobStatus()
    })
  } catch (error) {
    console.error('Error starting timeframe cron jobs:', error)
    res.status(500).json({
      success: false,
      error: 'Failed to start timeframe cron jobs',
      message: error.message
    })
  }
})

app.get('/api/timeframe-cron/status', (req, res) => {
  try {
    const status = getCronJobStatus()
    res.json({
      success: true,
      status
    })
  } catch (error) {
    console.error('Error getting cron job status:', error)
    res.status(500).json({
      success: false,
      error: 'Failed to get cron job status',
      message: error.message
    })
  }
})

// Serve cached logo files
app.get('/api/logo-file/:ticker', (req, res) => {
  try {
    const { ticker } = req.params;
    const fs = require('fs');
    const path = require('path');
    const logoPath = `/Users/joelbodker/Projects/frontend-testing/backend/cache/logos/${ticker.toUpperCase()}.png`;
    
    console.log('Looking for logo at:', logoPath);
    
    if (fs.existsSync(logoPath)) {
      console.log('Logo found, sending file');
      const logoBuffer = fs.readFileSync(logoPath);
      res.set('Content-Type', 'image/png');
      res.send(logoBuffer);
    } else {
      console.log('Logo not found at:', logoPath);
      res.status(404).json({ error: 'Logo file not found' });
    }
  } catch (error) {
    console.error('Error serving logo file:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});
app.use('/api/ticker-tide', tickerTideRouter);
app.use('/api/timeframe-data', timeframeDataRouter);
app.use('/api/unusual-whales/net-premiums', unusualWhalesNetPremiumsRouter);
app.use('/api/unusual-whales/comprehensive-premiums', unusualWhalesComprehensiveRouter);

// Logo routes
app.get('/api/logo/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const logoUrl = await logoCacheService.getLogo(ticker);
    
    if (logoUrl) {
      // If logoUrl is a file path, serve the actual image
      if (logoUrl.startsWith('/api/logo-file/')) {
        const logoPath = `/Users/joelbodker/Projects/frontend-testing/backend/cache/logos/${ticker.toUpperCase()}.png`;
        if (fs.existsSync(logoPath)) {
          const logoBuffer = fs.readFileSync(logoPath);
          res.set('Content-Type', 'image/png');
          res.send(logoBuffer);
          return;
        }
      }
      res.json({ logoUrl });
    } else {
      res.status(404).json({ error: 'Logo not found' });
    }
  } catch (error) {
    console.error('Error fetching logo:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.get('/api/logo-cache/stats', (req, res) => {
  try {
    const stats = logoCacheService.getCacheStats();
    res.json(stats);
  } catch (error) {
    console.error('Error getting cache stats:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

app.post('/api/logo-cache/clear/:ticker', (req, res) => {
  try {
    const ticker = req.params.ticker.toUpperCase();
    const fs = require('fs');
    const path = require('path');
    const cachePath = path.join(__dirname, '../cache/logos', `${ticker}.png`);
    
    if (fs.existsSync(cachePath)) {
      fs.unlinkSync(cachePath);
      res.json({ message: `Cleared logo cache for ${ticker}` });
    } else {
      res.json({ message: `No cached logo found for ${ticker}` });
    }
  } catch (error) {
    console.error('Error clearing logo cache:', error);
    res.status(500).json({ error: 'Failed to clear logo cache' });
  }
});

app.post('/api/logo-cache/clear', (req, res) => {
  try {
    const clearedCount = logoCacheService.clearExpiredLogos();
    res.json({ message: `Cleared ${clearedCount} expired logos` });
  } catch (error) {
    console.error('Error clearing cache:', error);
    res.status(500).json({ error: 'Internal server error' });
  }
});

// Health check endpoint for hosting platforms
app.get('/api/health', (req, res) => {
  res.status(200).json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    uptime: process.uptime(),
    environment: process.env.NODE_ENV || 'development'
  });
});

// Root endpoint
app.get('/', (req, res) => {
  res.json({
    message: 'Market Mono Backend API',
    version: '1.0.0',
    status: 'running'
  });
});

// In-memory scan state
let scanStatus = 'idle'; // 'idle' | 'running' | 'done'
let scanResult = null;
let lastScanTime = null;
let lastScanDate = null;

// Lit flow scan state
let litFlowScanStatus = 'idle';
let litFlowScanResult = null;
let litFlowLastScanTime = null;
let litFlowLastScanDate = null;

// Helper to start dark pool scan if not running
async function triggerDarkPoolScan(forDate) {
  if (scanStatus === 'running' && lastScanDate === forDate) return;
  scanStatus = 'running';
  scanResult = null;
  lastScanDate = forDate;
  try {
    const result = await runDarkPoolScan(forDate);
    scanResult = result;
    scanStatus = 'done';
    lastScanTime = new Date();
  } catch (e) {
    console.error('Error in dark pool scan:', e);
    scanStatus = 'idle';
    scanResult = null;
  }
}

// Helper to start lit flow scan if not running
async function triggerLitFlowScan(forDate) {
  if (litFlowScanStatus === 'running' && litFlowLastScanDate === forDate) return;
  litFlowScanStatus = 'running';
  litFlowScanResult = null;
  litFlowLastScanDate = forDate;
  try {
    const result = await runLitFlowScan(forDate);
    litFlowScanResult = result;
    litFlowScanStatus = 'done';
    litFlowLastScanTime = new Date();
  } catch (e) {
    console.error('Error in lit flow scan:', e);
    litFlowScanStatus = 'idle';
    litFlowScanResult = null;
  }
}

// API endpoints
app.get('/api/dark-pool/latest', (req, res) => {
  res.json({
    status: scanStatus,
    lastScanTime,
    data: scanResult || []
  });
});

app.get('/api/lit-flow/latest', (req, res) => {
  res.json({
    status: litFlowScanStatus,
    lastScanTime: litFlowLastScanTime,
    data: litFlowScanResult || []
  });
});

// Scanner endpoints
app.get('/api/dark-pool/scanner', async (req, res) => {
  const date = req.query.date;
  if (!date) {
    return res.status(400).json({ error: 'Date parameter is required' });
  }
  
  if (scanStatus === 'running' && lastScanDate === date) {
    return res.json({ status: 'running' });
  }

  await triggerDarkPoolScan(date);
  res.json({
    status: scanStatus,
    lastScanTime,
    data: scanResult || []
  });
});

app.get('/api/lit-flow/scanner', async (req, res) => {
  const date = req.query.date;
  if (!date) {
    return res.status(400).json({ error: 'Date parameter is required' });
  }
  
  if (litFlowScanStatus === 'running' && litFlowLastScanDate === date) {
    return res.json({ status: 'running' });
  }

  await triggerLitFlowScan(date);
  res.json({
    status: litFlowScanStatus,
    lastScanTime: litFlowLastScanTime,
    data: litFlowScanResult || []
  });
});

// Ticker-specific Dark Pool levels endpoint (from ClickHouse)
app.get('/api/dark-pool/levels/:ticker', async (req, res) => {
  try {
    const ticker = req.params.ticker.toUpperCase();
    const limit = parseInt(req.query.limit) || 10;
    const days = parseInt(req.query.days) || 15; // Default to 15 days for historical data
    
    // Import the storage service
    const storageService = require('./services/darkPoolLitPoolStorageService');
    
    // Get levels from ClickHouse
    const tickerLevels = await storageService.getDarkPoolLevels(ticker, days, limit);
    
    res.json({
      ticker,
      levels: tickerLevels,
      count: tickerLevels.length
    });
  } catch (error) {
    console.error('Error fetching dark pool levels:', error);
    res.status(500).json({ error: 'Failed to fetch dark pool levels' });
  }
});

// Ticker-specific Lit Pool levels endpoint (from ClickHouse)
app.get('/api/lit-pool/levels/:ticker', async (req, res) => {
  try {
    const ticker = req.params.ticker.toUpperCase();
    const limit = parseInt(req.query.limit) || 10;
    const days = parseInt(req.query.days) || 15; // Default to 15 days for historical data
    
    // Import the storage service
    const storageService = require('./services/darkPoolLitPoolStorageService');
    
    // Get levels from ClickHouse
    const tickerLevels = await storageService.getLitPoolLevels(ticker, days, limit);
    
    res.json({
      ticker,
      levels: tickerLevels,
      count: tickerLevels.length
    });
  } catch (error) {
    console.error('Error fetching lit pool levels:', error);
    res.status(500).json({ error: 'Failed to fetch lit pool levels' });
  }
});

// Historical Dark Pool data endpoint
app.get('/api/dark-pool/historical/:ticker', async (req, res) => {
  try {
    const ticker = req.params.ticker.toUpperCase();
    const days = parseInt(req.query.days) || 15;
    
    const { fetchHistoricalDarkPool } = require('./services/historicalDarkPoolService');
    
    console.log(`Fetching ${days} days of historical dark pool data for ${ticker}...`);
    const levels = await fetchHistoricalDarkPool(ticker, days);
    
    res.json({
      ticker,
      days,
      total_levels: levels.length,
      levels: levels
    });
  } catch (error) {
    console.error('Error fetching historical dark pool data:', error);
    res.status(500).json({ error: 'Failed to fetch historical dark pool data' });
  }
});

// Historical Lit Pool data endpoint
app.get('/api/lit-pool/historical/:ticker', async (req, res) => {
  try {
    const ticker = req.params.ticker.toUpperCase();
    const days = parseInt(req.query.days) || 15;
    
    const { fetchHistoricalLitPool } = require('./services/historicalDarkPoolService');
    
    console.log(`Fetching ${days} days of historical lit pool data for ${ticker}...`);
    const levels = await fetchHistoricalLitPool(ticker, days);
    
    res.json({
      ticker,
      days,
      total_levels: levels.length,
      levels: levels
    });
  } catch (error) {
    console.error('Error fetching historical lit pool data:', error);
    res.status(500).json({ error: 'Failed to fetch historical lit pool data' });
  }
});

// Helper function to get market status from Polygon API
const getMarketStatus = async () => {
  const apiKey = process.env.POLYGON_API_KEY;
  if (!apiKey) {
    console.warn('POLYGON_API_KEY not available for market status check');
    return null;
  }
  
  try {
    const response = await axios.get(`https://api.polygon.io/v1/marketstatus/now?apiKey=${apiKey}`);
    return response.data;
  } catch (error) {
    console.error('Error fetching market status:', error.message);
    return null;
  }
};

// Helper function to check if market was open on a specific date
const checkMarketOpenOnDate = async (dateStr) => {
  const apiKey = process.env.POLYGON_API_KEY;
  if (!apiKey) return false;
  
  try {
    // Use a simple test - try to fetch any ticker data for that date
    // If we get data, the market was likely open
    const response = await axios.get(`https://api.polygon.io/v2/aggs/ticker/SPY/range/1/minute/${dateStr}/${dateStr}?adjusted=true&sort=asc&limit=1&apiKey=${apiKey}`);
    return response.data.results && response.data.results.length > 0;
  } catch (error) {
    console.error(`Error checking market status for ${dateStr}:`, error.message);
    return false;
  }
};

// Helper function to get the Nth trading day back from a given date
const getNthTradingDayBack = async (fromDate, tradingDaysBack) => {
  const checkDate = new Date(fromDate);
  let tradingDaysFound = 0;
  
  // Go back day by day until we find the Nth trading day
  for (let daysBack = 1; daysBack <= 30; daysBack++) { // Max 30 days to prevent infinite loop
    const testDate = new Date(checkDate);
    testDate.setDate(testDate.getDate() - daysBack);
    
    // Skip weekends
    const day = testDate.getDay();
    if (day === 0 || day === 6) continue;
    
    const dateStr = testDate.toISOString().slice(0, 10);
    const wasOpen = await checkMarketOpenOnDate(dateStr);
    
    if (wasOpen) {
      tradingDaysFound++;
      if (tradingDaysFound === tradingDaysBack) {
        return dateStr;
      }
    }
  }
  
  // Fallback: return a date that's approximately the right number of calendar days back
  const fallbackDate = new Date(checkDate);
  fallbackDate.setDate(fallbackDate.getDate() - (tradingDaysBack * 1.4)); // Approximate trading days
  return fallbackDate.toISOString().slice(0, 10);
};

// Helper function to get the last trading day using market status
const getLastTradingDay = async () => {
  const marketStatus = await getMarketStatus();
  const now = new Date();
  const estDate = new Date(now.toLocaleString('en-US', { timeZone: 'America/New_York' }));
  
  // If market is open today, use today
  if (marketStatus && marketStatus.exchanges && marketStatus.exchanges.nasdaq === 'open') {
    console.log('Market is open today, using current date');
    return estDate.toISOString().slice(0, 10);
  }
  
  console.log('Market is closed, searching for last trading day...');
  
  // Market is closed, so we need to find the last day it was actually open
  // Go back up to 10 days to find the last trading day
  for (let daysBack = 1; daysBack <= 10; daysBack++) {
    const checkDate = new Date(estDate);
    checkDate.setDate(checkDate.getDate() - daysBack);
    
    // Skip weekends as a basic optimization
    const day = checkDate.getDay();
    if (day === 0 || day === 6) continue;
    
    const dateStr = checkDate.toISOString().slice(0, 10);
    console.log(`Checking if market was open on ${dateStr}...`);
    
    // Check if market was actually open on this date by testing for data
    const wasOpen = await checkMarketOpenOnDate(dateStr);
    if (wasOpen) {
      console.log(`Found last trading day: ${dateStr}`);
      return dateStr;
    }
  }
  
  // Fallback logic if we can't determine from API
  console.log('Could not determine last trading day from API, using fallback logic');
  let fallbackDate = new Date(estDate);
  
  // Go back to last weekday
  do {
    fallbackDate.setDate(fallbackDate.getDate() - 1);
  } while (fallbackDate.getDay() === 0 || fallbackDate.getDay() === 6);
  
  return fallbackDate.toISOString().slice(0, 10);
};

// Market status endpoint
app.get('/api/polygon/market-status', async (req, res) => {
  try {
    const marketStatus = await getMarketStatus();
    if (marketStatus) {
      res.json(marketStatus);
    } else {
      res.status(503).json({ error: 'Market status unavailable' });
    }
  } catch (error) {
    console.error('Error in market status endpoint:', error);
    res.status(500).json({ error: 'Failed to fetch market status' });
  }
});

// Last trading day endpoint (for debugging)
app.get('/api/polygon/last-trading-day', async (req, res) => {
  try {
    const lastTradingDay = await getLastTradingDay();
    const marketStatus = await getMarketStatus();
    res.json({
      lastTradingDay,
      currentMarketStatus: marketStatus?.exchanges?.nasdaq || 'unknown',
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    console.error('Error in last trading day endpoint:', error);
    res.status(500).json({ error: 'Failed to determine last trading day' });
  }
});

// Market prices endpoint
app.get('/api/polygon/market-prices', async (req, res) => {
  try {
    const { tickers, timeframe, date } = req.query;
    
    if (!tickers || !timeframe) {
      return res.status(400).json({ error: 'Tickers and timeframe parameters are required' });
    }

    const tickerList = tickers.split(',');
    
    // Determine timespan and date range based on timeframe
    let timespan = 'minute';
    let multiplier = 1;
    let fromDate, toDate;
    
    const now = new Date();
    const targetDate = date ? new Date(date) : now;
    
    // Convert to EST/EDT for proper date calculation
    const estDate = new Date(targetDate.toLocaleString('en-US', { timeZone: 'America/New_York' }));
    
    // Helper function to check if a date is a market day (not weekend)
    const isMarketDay = (date) => {
      const day = date.getDay();
      return day !== 0 && day !== 6; // Not Sunday (0) or Saturday (6)
    };
    
    switch (timeframe) {
      case '1D':
        timespan = 'minute';
        multiplier = 1;
        
        // For 1D timeframe, explicitly request the current NY date (if weekday) so we always attempt to
        // load same-day data while the market is open. The backend already contains fallback logic that
        // rolls back to the previous trading session if no data is returned for the supplied date, so
        // passing today's date is safe.
        if (date) {
          // If a specific date is requested, use it (for fallback scenarios)
          fromDate = estDate.toISOString().slice(0, 10);
          toDate = estDate.toISOString().slice(0, 10);
        } else {
          // Use market status to get the appropriate trading day
          const tradingDay = await getLastTradingDay();
          fromDate = tradingDay;
          toDate = tradingDay;
        }
        
        console.log(`1D timeframe: Using trading day ${fromDate} for market data`);
        break;
      case '30min':
        timespan = 'minute';
        multiplier = 30;
        
        // For 30min EMA calculations, we need historical data going back multiple days
        // EMA 200 needs 200 periods = 100 hours = ~4+ trading days
        // EMA 50 needs 50 periods = 25 hours = ~1+ trading days
        // Let's get 5 trading days back to ensure we have enough data
        const fiveTradingDaysAgoForEMA = await getNthTradingDayBack(estDate.toISOString().slice(0, 10), 5);
        
        if (date) {
          fromDate = estDate.toISOString().slice(0, 10);
          toDate = estDate.toISOString().slice(0, 10);
        } else {
          fromDate = fiveTradingDaysAgoForEMA;
          toDate = estDate.toISOString().slice(0, 10);
        }
        
        console.log(`30min timeframe: Using date range ${fromDate} to ${toDate} for EMA calculations`);
        break;
      case '5D':
        timespan = 'day';
        multiplier = 1;
        // Get 5 trading days back
        const fiveTradingDaysAgo = await getNthTradingDayBack(estDate.toISOString().slice(0, 10), 5);
        fromDate = fiveTradingDaysAgo;
        toDate = estDate.toISOString().slice(0, 10);
        break;
      case '1M':
        timespan = 'day';
        multiplier = 1;
        // Get approximately 22 trading days back (1 month of trading days)
        const oneMonthTradingDaysAgo = await getNthTradingDayBack(estDate.toISOString().slice(0, 10), 22);
        fromDate = oneMonthTradingDaysAgo;
        toDate = estDate.toISOString().slice(0, 10);
        break;
      case '3M':
        timespan = 'day';
        multiplier = 1;
        // Get approximately 66 trading days back (3 months of trading days)
        const threeMonthTradingDaysAgo = await getNthTradingDayBack(estDate.toISOString().slice(0, 10), 66);
        fromDate = threeMonthTradingDaysAgo;
        toDate = estDate.toISOString().slice(0, 10);
        break;
      case '6M':
        timespan = 'day';
        multiplier = 1;
        // Get approximately 132 trading days back (6 months of trading days)
        const sixMonthTradingDaysAgo = await getNthTradingDayBack(estDate.toISOString().slice(0, 10), 132);
        fromDate = sixMonthTradingDaysAgo;
        toDate = estDate.toISOString().slice(0, 10);
        break;
      case 'YTD':
        timespan = 'day';
        multiplier = 1;
        // Get the first trading day of the current year
        const yearStart = new Date(estDate.getFullYear(), 0, 1);
        // Find the first trading day of the year
        let firstTradingDay = yearStart.toISOString().slice(0, 10);
        for (let day = 0; day < 7; day++) {
          const testDate = new Date(yearStart);
          testDate.setDate(testDate.getDate() + day);
          const dayOfWeek = testDate.getDay();
          if (dayOfWeek !== 0 && dayOfWeek !== 6) { // Not weekend
            const dateStr = testDate.toISOString().slice(0, 10);
            const wasOpen = await checkMarketOpenOnDate(dateStr);
            if (wasOpen) {
              firstTradingDay = dateStr;
              break;
            }
          }
        }
        fromDate = firstTradingDay;
        toDate = estDate.toISOString().slice(0, 10);
        break;
      case '1Y':
        timespan = 'day';
        multiplier = 1;
        const oneYearAgo = new Date(estDate);
        oneYearAgo.setFullYear(oneYearAgo.getFullYear() - 1);
        fromDate = oneYearAgo.toISOString().slice(0, 10);
        toDate = estDate.toISOString().slice(0, 10);
        break;
      case '5Y':
        timespan = 'day';
        multiplier = 1;
        const fiveYearsAgo = new Date(estDate);
        fiveYearsAgo.setFullYear(fiveYearsAgo.getFullYear() - 5);
        fromDate = fiveYearsAgo.toISOString().slice(0, 10);
        toDate = estDate.toISOString().slice(0, 10);
        break;
      default:
        timespan = 'minute';
        multiplier = 1;
        fromDate = estDate.toISOString().slice(0, 10);
        toDate = estDate.toISOString().slice(0, 10);
    }

    console.log(`Fetching market prices for ${tickerList.join(', ')} from ${fromDate} to ${toDate} with timespan ${timespan} and multiplier ${multiplier}`);
    
    let data = await fetchMarketPrices(tickerList, multiplier, timespan, fromDate, toDate);
    
    // For 1D timeframe, if no data is found, try previous market days
    if (timeframe === '1D') {
      const hasData = tickerList.some(ticker => 
        data[ticker]?.results && data[ticker].results.length > 0
      );
      
      if (!hasData) {
        console.log(`No 1D data found for ${fromDate}, trying previous market days...`);
        
        // Try up to 5 previous market days
        for (let daysBack = 1; daysBack <= 5; daysBack++) {
          const fallbackDate = new Date(estDate);
          fallbackDate.setDate(fallbackDate.getDate() - daysBack);
          
          // Skip non-market days (weekends)
          if (!isMarketDay(fallbackDate)) continue;
          
          const fallbackDateStr = fallbackDate.toISOString().slice(0, 10);
          console.log(`Trying fallback date: ${fallbackDateStr}`);
          
          try {
            const fallbackData = await fetchMarketPrices(tickerList, 1, timespan, fallbackDateStr, fallbackDateStr);
            const hasFallbackData = tickerList.some(ticker => 
              fallbackData[ticker]?.results && fallbackData[ticker].results.length > 0
            );
            
            if (hasFallbackData) {
              console.log(`Found 1D data for fallback date: ${fallbackDateStr}`);
              data = fallbackData;
              break;
            }
          } catch (error) {
            console.error(`Error fetching fallback data for ${fallbackDateStr}:`, error.message);
            // Continue to next date
          }
        }
      }
    }
    
    res.json(data);
  } catch (error) {
    console.error('Error fetching market prices:', error);
    res.status(500).json({ error: 'Failed to fetch market prices' });
  }
});

// Sector snapshots endpoint
app.get('/api/polygon/sector-snapshots', async (req, res) => {
  try {
    const { tickers } = req.query;
    if (!tickers) {
      return res.status(400).json({ error: 'Tickers parameter is required' });
    }

    const tickerList = tickers.split(',');
    const data = await fetchSectorSnapshots(tickerList);
    res.json(data);
  } catch (error) {
    console.error('Error fetching sector snapshots:', error);
    res.status(500).json({ error: error.message });
  }
});

// Ticker details endpoint
app.get('/api/polygon/ticker-details/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const data = await getTickerDetails(ticker.toUpperCase());
    res.json(data);
  } catch (error) {
    console.error('Error fetching ticker details:', error);
    res.status(500).json({ error: 'Failed to fetch ticker details' });
  }
});

// Ticker search endpoint
app.get('/api/ticker-search', async (req, res) => {
  try {
    const { query } = req.query;
    
    if (!query || query.length < 1) {
      return res.json([]);
    }

    const FMP_API_KEY = process.env.FMP_API_KEY;
    if (!FMP_API_KEY) {
      return res.status(500).json({ error: 'FMP API key not configured' });
    }

    // Search by ticker symbol
    const tickerResponse = await axios.get(`https://financialmodelingprep.com/api/v3/search-ticker?query=${encodeURIComponent(query)}&limit=10&apikey=${FMP_API_KEY}`);
    
    // Search by company name
    const companyResponse = await axios.get(`https://financialmodelingprep.com/api/v3/search?query=${encodeURIComponent(query)}&limit=10&apikey=${FMP_API_KEY}`);
    
    // Combine and deduplicate results
    const tickerResults = tickerResponse.data || [];
    const companyResults = companyResponse.data || [];
    
    const combinedResults = [...tickerResults, ...companyResults];
    const uniqueResults = combinedResults.filter((result, index, self) => 
      index === self.findIndex(r => r.symbol === result.symbol)
    );

    // Filter to only US exchanges
    const usOnlyResults = uniqueResults.filter(result => {
      const stockExchange = result.stockExchange || '';
      return stockExchange.includes('NASDAQ') || 
             stockExchange.includes('NYSE') || 
             stockExchange.includes('AMEX') ||
             stockExchange.includes('OTC') ||
             stockExchange.includes('New York Stock Exchange') ||
             stockExchange.includes('Nasdaq Global Market') ||
             stockExchange.includes('Nasdaq Capital Market');
    });

    // Sort results to prioritize main tickers
    const sortedResults = usOnlyResults.sort((a, b) => {
      // Priority 1: Main ticker symbols (no dots or suffixes)
      const aIsMainTicker = !a.symbol.includes('.');
      const bIsMainTicker = !b.symbol.includes('.');
      
      if (aIsMainTicker && !bIsMainTicker) return -1;
      if (!aIsMainTicker && bIsMainTicker) return 1;
      
      // Priority 2: Alphabetical by symbol
      return a.symbol.localeCompare(b.symbol);
    });

    res.json(sortedResults.slice(0, 15)); // Limit to 15 results
  } catch (error) {
    console.error('Error in ticker search:', error);
    res.status(500).json({ error: 'Failed to search tickers' });
  }
});

// Ticker search by company name endpoint
app.get('/api/ticker-search/company', async (req, res) => {
  try {
    const { query } = req.query;
    
    if (!query || query.length < 1) {
      return res.json([]);
    }

    const FMP_API_KEY = process.env.FMP_API_KEY;
    if (!FMP_API_KEY) {
      return res.status(500).json({ error: 'FMP API key not configured' });
    }

    const response = await axios.get(`https://financialmodelingprep.com/api/v3/search?query=${encodeURIComponent(query)}&limit=15&apikey=${FMP_API_KEY}`);
    res.json(response.data || []);
  } catch (error) {
    console.error('Error in company search:', error);
    res.status(500).json({ error: 'Failed to search companies' });
  }
});

// All tickers endpoints
app.get('/api/tickers/all', async (req, res) => {
  try {
    const { forceRefresh, sample } = req.query;
    const forceRefreshBool = forceRefresh === 'true';
    const sampleCount = sample ? parseInt(sample) : null;
    
    let tickers;
    if (sampleCount && sampleCount > 0) {
      tickers = await allTickersService.getSampleTickers(sampleCount);
    } else {
      tickers = await allTickersService.getAllUSTickers(forceRefreshBool);
    }
    
    res.json({
      success: true,
      tickers,
      count: tickers.length,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    console.error('Error fetching all tickers:', error);
    res.status(500).json({ 
      success: false,
      error: 'Failed to fetch tickers',
      message: error.message 
    });
  }
});

app.get('/api/tickers/stats', (req, res) => {
  try {
    const stats = allTickersService.getTickerStats();
    res.json({
      success: true,
      ...stats
    });
  } catch (error) {
    console.error('Error getting ticker stats:', error);
    res.status(500).json({ 
      success: false,
      error: 'Failed to get ticker stats',
      message: error.message 
    });
  }
});

app.post('/api/tickers/clear-cache', (req, res) => {
  try {
    allTickersService.clearCache();
    res.json({
      success: true,
      message: 'Ticker cache cleared successfully'
    });
  } catch (error) {
    console.error('Error clearing ticker cache:', error);
    res.status(500).json({ 
      success: false,
      error: 'Failed to clear ticker cache',
      message: error.message 
    });
  }
});

// Bulk company profile update endpoints
app.post('/api/company-profiles/bulk-update', async (req, res) => {
  try {
    const { 
      forceRefresh = false, 
      batchSize = 50, 
      delayBetweenBatches = 2000,
      maxConcurrentRequests = 10,
      sampleSize = null 
    } = req.body;

    // Check if update is already running
    const currentStatus = bulkCompanyProfileService.getUpdateStatus();
    if (currentStatus.isRunning) {
      return res.status(409).json({
        success: false,
        error: 'Bulk update is already running',
        currentStatus
      });
    }

    // Start update in background
    bulkCompanyProfileService.updateAllCompanyProfiles({
      forceRefresh,
      batchSize,
      delayBetweenBatches,
      maxConcurrentRequests,
      sampleSize
    }).then(result => {
      console.log('Bulk update completed:', result);
    }).catch(error => {
      console.error('Bulk update failed:', error);
    });

    res.json({
      success: true,
      message: 'Bulk update started',
      status: bulkCompanyProfileService.getUpdateStatus()
    });

  } catch (error) {
    console.error('Error starting bulk update:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to start bulk update',
      message: error.message
    });
  }
});

app.get('/api/company-profiles/bulk-update/status', (req, res) => {
  try {
    const status = bulkCompanyProfileService.getUpdateStatus();
    res.json({
      success: true,
      ...status
    });
  } catch (error) {
    console.error('Error getting bulk update status:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to get update status',
      message: error.message
    });
  }
});

app.post('/api/company-profiles/bulk-update/stop', (req, res) => {
  try {
    bulkCompanyProfileService.stopUpdate();
    res.json({
      success: true,
      message: 'Bulk update stopped',
      status: bulkCompanyProfileService.getUpdateStatus()
    });
  } catch (error) {
    console.error('Error stopping bulk update:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to stop bulk update',
      message: error.message
    });
  }
});

app.get('/api/company-profiles/bulk-update/statistics', (req, res) => {
  try {
    const stats = bulkCompanyProfileService.getStatistics();
    res.json({
      success: true,
      ...stats
    });
  } catch (error) {
    console.error('Error getting bulk update statistics:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to get update statistics',
      message: error.message
    });
  }
});

// ClickHouse storage endpoints
app.get('/api/company-profiles/storage/statistics', async (req, res) => {
  try {
    const stats = await bulkCompanyProfileService.getStorageStatistics();
    res.json({
      success: true,
      ...stats
    });
  } catch (error) {
    console.error('Error getting storage statistics:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to get storage statistics',
      message: error.message
    });
  }
});

app.get('/api/company-profiles/storage/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const profile = await bulkCompanyProfileService.getCompanyProfileFromStorage(ticker);
    
    if (profile) {
      res.json({
        success: true,
        profile
      });
    } else {
      res.status(404).json({
        success: false,
        error: 'Company profile not found',
        ticker
      });
    }
  } catch (error) {
    console.error('Error getting company profile from storage:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to get company profile',
      message: error.message
    });
  }
});

app.get('/api/company-profiles/storage', async (req, res) => {
  try {
    const { limit = 100, offset = 0, orderBy = 'ticker' } = req.query;
    const profiles = await bulkCompanyProfileService.getAllCompanyProfilesFromStorage({
      limit: parseInt(limit),
      offset: parseInt(offset),
      orderBy
    });
    
    res.json({
      success: true,
      profiles,
      count: profiles.length,
      limit: parseInt(limit),
      offset: parseInt(offset)
    });
  } catch (error) {
    console.error('Error getting all company profiles from storage:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to get company profiles',
      message: error.message
    });
  }
});

// Raw trades logging endpoint - for debugging
app.get('/api/debug/raw-trades', async (req, res) => {
  try {
    const limit = parseInt(req.query.limit) || 10;
    console.log('\n🔍 Manual raw trades logging triggered...');
    const trades = await logRawTradesFromFlowTable(limit);
    res.json({
      success: true,
      message: `Logged ${trades.length} raw trades to console`,
      tradesCount: trades.length,
      trades: trades
    });
  } catch (error) {
    console.error('Error in raw trades endpoint:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to log raw trades',
      details: error.message
    });
  }
});

// Error handling middleware
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).send('Something broke!');
});

const PORT = process.env.PORT || 3001;
server.listen(PORT, async () => {
  console.log(`Server is running on port ${PORT}`);
  console.log(`Socket.IO server is ready for real-time connections`);
  
  // === CONSOLE LOG RAW TRADES FROM FLOW TABLE ===
  console.log('\n🚀 STARTING RAW TRADE LOGGING...');
  try {
    // Test connection first
    const isConnected = await testConnection();
    if (isConnected) {
      console.log('✅ ClickHouse connection successful');
      // Log some raw trades from the flow table
      await logRawTradesFromFlowTable(15); // Log 15 raw trades
    } else {
      console.log('❌ ClickHouse connection failed');
    }
  } catch (error) {
    console.error('Error logging raw trades:', error);
  }
  console.log('🔄 Raw trade logging completed\n');
  
  // Start timeframe cron jobs after server starts
  setTimeout(() => {
    console.log('🕐 Initializing timeframe cron jobs...')
    startTimeframeCronJobs()
  }, 5000) // Wait 5 seconds for server to fully start
}); 