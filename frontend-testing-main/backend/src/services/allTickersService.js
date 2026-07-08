const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;
const FMP_API_KEY = process.env.FMP_API_KEY;

class AllTickersService {
  constructor() {
    this.cache = new Map();
    this.cacheExpiry = 24 * 60 * 60 * 1000; // 24 hours
  }

  /**
   * Get all US stock tickers from Polygon API
   * @param {number} limit - Number of tickers to fetch per page (max 1000)
   * @param {string} cursor - Pagination cursor
   * @returns {Promise<Object>} Response with tickers and pagination info
   */
  async getAllTickersFromPolygon(limit = 1000, cursor = null) {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY is not configured');
    }

    try {
      let url = `https://api.polygon.io/v3/reference/tickers?market=stocks&active=true&limit=${limit}&apiKey=${POLYGON_API_KEY}`;
      
      if (cursor) {
        url += `&cursor=${cursor}`;
      }

      console.log(`Fetching tickers from Polygon API...`);
      const response = await axios.get(url);
      
      return {
        tickers: response.data.results || [],
        nextCursor: response.data.next_url ? this.extractCursorFromUrl(response.data.next_url) : null,
        count: response.data.count || 0
      };
    } catch (error) {
      console.error('Error fetching tickers from Polygon:', error.response?.data || error.message);
      throw error;
    }
  }

  /**
   * Get all US stock tickers from FMP API
   * @returns {Promise<Array>} Array of ticker objects
   */
  async getAllTickersFromFMP() {
    if (!FMP_API_KEY) {
      throw new Error('FMP_API_KEY is not configured');
    }

    try {
      console.log(`Fetching tickers from FMP API...`);
      const response = await axios.get(`https://financialmodelingprep.com/api/v3/stock/list?apikey=${FMP_API_KEY}`);
      
      // Filter for US exchanges only
      const usTickers = (response.data || []).filter(ticker => {
        const exchange = ticker.exchange || '';
        return exchange.includes('NASDAQ') || 
               exchange.includes('NYSE') || 
               exchange.includes('AMEX') ||
               exchange.includes('OTC') ||
               exchange.includes('New York Stock Exchange') ||
               exchange.includes('Nasdaq Global Market') ||
               exchange.includes('Nasdaq Capital Market');
      });

      console.log(`Found ${usTickers.length} US tickers from FMP`);
      return usTickers;
    } catch (error) {
      console.error('Error fetching tickers from FMP:', error.response?.data || error.message);
      throw error;
    }
  }

  /**
   * Get all US stock tickers with caching
   * @param {boolean} forceRefresh - Force refresh cache
   * @returns {Promise<Array>} Array of ticker symbols
   */
  async getAllUSTickers(forceRefresh = false) {
    const cacheKey = 'all_us_tickers';
    const cached = this.cache.get(cacheKey);
    
    if (!forceRefresh && cached && Date.now() - cached.timestamp < this.cacheExpiry) {
      console.log(`Returning ${cached.tickers.length} cached US tickers`);
      return cached.tickers;
    }

    console.log('Fetching fresh US tickers from FMP...');
    
    try {
      // Use FMP as primary source (more reliable and comprehensive)
      const fmpTickers = await this.getAllTickersFromFMP();
      const tickerSymbols = fmpTickers.map(t => t.symbol).filter(symbol => symbol && symbol.length <= 5);
      
      // Cache the results
      this.cache.set(cacheKey, {
        tickers: tickerSymbols,
        timestamp: Date.now()
      });
      
      console.log(`FMP returned ${tickerSymbols.length} US tickers`);
      return tickerSymbols;
    } catch (fmpError) {
      console.error('FMP API failed:', fmpError.message);
      
      // Fallback to Polygon if FMP fails
      try {
        console.log('FMP failed, trying Polygon as fallback...');
        const polygonTickers = await this.getAllTickersFromPolygonComplete();
        
        // Cache the results
        this.cache.set(cacheKey, {
          tickers: polygonTickers,
          timestamp: Date.now()
        });
        
        console.log(`Polygon returned ${polygonTickers.length} tickers`);
        return polygonTickers;
      } catch (polygonError) {
        console.error('Both FMP and Polygon APIs failed:', polygonError.message);
        throw new Error('Failed to fetch tickers from both APIs');
      }
    }
  }

  /**
   * Get all tickers from Polygon with pagination
   * @returns {Promise<Array>} Array of ticker symbols
   */
  async getAllTickersFromPolygonComplete() {
    const allTickers = [];
    let cursor = null;
    let pageCount = 0;
    const maxPages = 50; // Safety limit to prevent infinite loops

    do {
      pageCount++;
      console.log(`Fetching Polygon page ${pageCount}...`);
      
      const result = await this.getAllTickersFromPolygon(1000, cursor);
      
      // Filter for US exchanges and extract symbols
      const usTickers = result.tickers
        .filter(ticker => {
          const exchange = ticker.primary_exchange || '';
          return exchange.includes('NASDAQ') || 
                 exchange.includes('NYSE') || 
                 exchange.includes('AMEX') ||
                 exchange.includes('OTC') ||
                 exchange.includes('New York Stock Exchange') ||
                 exchange.includes('Nasdaq Global Market') ||
                 exchange.includes('Nasdaq Capital Market');
        })
        .map(ticker => ticker.ticker)
        .filter(symbol => symbol && symbol.length <= 5); // Filter out complex symbols

      allTickers.push(...usTickers);
      cursor = result.nextCursor;
      
      console.log(`Page ${pageCount}: Found ${usTickers.length} US tickers (${allTickers.length} total)`);
      
      // Safety check
      if (pageCount >= maxPages) {
        console.warn(`Reached maximum page limit (${maxPages}), stopping pagination`);
        break;
      }
      
      // Small delay to be respectful to the API
      if (cursor) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }
      
    } while (cursor);

    console.log(`Polygon API returned ${allTickers.length} total US tickers across ${pageCount} pages`);
    return allTickers;
  }

  /**
   * Extract cursor from Polygon API next_url
   * @param {string} nextUrl - Next URL from Polygon API
   * @returns {string} Cursor value
   */
  extractCursorFromUrl(nextUrl) {
    try {
      const url = new URL(nextUrl);
      return url.searchParams.get('cursor');
    } catch (error) {
      console.error('Error extracting cursor from URL:', error);
      return null;
    }
  }

  /**
   * Get ticker statistics
   * @returns {Object} Statistics about cached tickers
   */
  getTickerStats() {
    const cached = this.cache.get('all_us_tickers');
    if (!cached) {
      return { totalTickers: 0, lastUpdated: null, cacheAge: null };
    }

    const cacheAge = Date.now() - cached.timestamp;
    return {
      totalTickers: cached.tickers.length,
      lastUpdated: new Date(cached.timestamp).toISOString(),
      cacheAge: Math.round(cacheAge / (60 * 1000)) // Age in minutes
    };
  }

  /**
   * Clear ticker cache
   */
  clearCache() {
    this.cache.clear();
    console.log('Ticker cache cleared');
  }

  /**
   * Get sample of tickers for testing
   * @param {number} count - Number of tickers to return
   * @returns {Promise<Array>} Array of ticker symbols
   */
  async getSampleTickers(count = 100) {
    const allTickers = await this.getAllUSTickers();
    return allTickers.slice(0, count);
  }
}

module.exports = new AllTickersService();
