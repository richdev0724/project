const axios = require('axios');

const API_KEY = process.env.POLYGON_API_KEY;
const BASE_URL = 'https://api.polygon.io';

// In-memory cache for ticker details
const detailsCache = {
  data: {},
  timestamp: {},
  CACHE_DURATION: 24 * 60 * 60 * 1000 // 24 hours
};

async function getTickerDetails(ticker) {
  // Check cache first
  const now = Date.now();
  if (detailsCache.data[ticker] && 
      (now - detailsCache.timestamp[ticker] < detailsCache.CACHE_DURATION)) {
    console.log(`📦 Using cached details for ${ticker}`);
    return detailsCache.data[ticker];
  }

  try {
    console.log(`🔄 Fetching ticker details for ${ticker}`);
    const url = `${BASE_URL}/v3/reference/tickers/${ticker}?apiKey=${API_KEY}`;
    const response = await axios.get(url);
    
    if (response.data.status === 'OK' && response.data.results) {
      // Format the response
      const details = {
        ticker: response.data.results.ticker,
        name: response.data.results.name,
        description: response.data.results.description,
        logo_url: response.data.results.branding?.logo_url,
        icon_url: response.data.results.branding?.icon_url,
        total_employees: response.data.results.total_employees,
        ceo: null, // Polygon API doesn't provide CEO info
        address: response.data.results.address ? 
          `${response.data.results.address.city}, ${response.data.results.address.state}` : null,
        market_cap: response.data.results.market_cap,
        is_etf: response.data.results.type !== 'CS',
        sic_description: response.data.results.sic_description || null,
        homepage_url: response.data.results.homepage_url || null
      };

      // Update cache
      detailsCache.data[ticker] = details;
      detailsCache.timestamp[ticker] = now;

      console.log(`✅ Successfully fetched details for ${ticker}`);
      return details;
    } else {
      throw new Error('Invalid response from Polygon API');
    }
  } catch (error) {
    console.error(`❌ Error fetching ticker details for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = { getTickerDetails }; 