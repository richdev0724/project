const axios = require('axios');

const POLYGON_API_TOKEN = process.env.POLYGON_API_KEY;
const POLYGON_BASE_URL = 'https://api.polygon.io/v3/snapshot/options';

// In-memory cache for PCR values
// PCR is calculated using volume-based method (industry standard):
// PCR = Total Put Volume / Total Call Volume (previous trading day)
const pcrCache = new Map();

/**
 * Calculate Put/Call Ratio for a ticker using Open Interest data
 * @param {string} ticker - Stock ticker symbol
 * @returns {Promise<number|null>} - PCR value or null if calculation fails
 */
const calculateTickerPCR_OI = async (ticker) => {
  try {
    if (!POLYGON_API_TOKEN) {
      console.log('POLYGON_API_KEY not configured, skipping OI PCR calculation');
      return null;
    }

    console.log(`Calculating OI PCR for ${ticker}...`);

    let totalPutOI = 0;
    let totalCallOI = 0;
    let nextUrl = null;
    let pageCount = 0;
    let totalContracts = 0;

    console.log(`Starting OI PCR calculation for ${ticker}...`);
    const maxPages = 500; // Very high limit to get all puts (debug showed 200+ pages needed for 1.85, need more for 2.75)

    // Fetch pages of options data
    do {
      const url = nextUrl || `${POLYGON_BASE_URL}/${ticker.toUpperCase()}`;
      
      const response = nextUrl 
        ? await axios.get(url, { params: { apikey: POLYGON_API_TOKEN } })
        : await axios.get(url, { params: { apikey: POLYGON_API_TOKEN } });

      if (!response.data || !response.data.results) {
        console.log(`No options data found for ${ticker} on page ${pageCount + 1}`);
        break;
      }

      const options = response.data.results;
      pageCount++;
      totalContracts += options.length;

      // Debug: Log first contract structure
      if (pageCount === 1 && options.length > 0) {
        console.log('First contract structure:', JSON.stringify(options[0], null, 2));
        console.log('Open interest field:', options[0].open_interest);
        console.log('Contract type:', options[0].details?.contract_type);
      }

      // Aggregate Open Interest by option type
      let putsInPage = 0;
      let callsInPage = 0;
      let putsWithOI = 0;
      let callsWithOI = 0;
      
      options.forEach(contract => {
        // Debug logging for first few contracts
        if (pageCount === 1 && putsInPage + callsInPage < 5) {
          console.log(`Contract ${putsInPage + callsInPage + 1}:`, {
            contract_type: contract.details?.contract_type,
            open_interest: contract.open_interest,
            has_open_interest: contract.open_interest > 0
          });
        }
        
        if (contract.open_interest > 0) {
          if (contract.details && contract.details.contract_type === 'call') {
            totalCallOI += contract.open_interest;
            callsInPage++;
            callsWithOI++;
          } else if (contract.details && contract.details.contract_type === 'put') {
            totalPutOI += contract.open_interest;
            putsInPage++;
            putsWithOI++;
          }
        }
      });
      
      console.log(`Page ${pageCount}: ${putsInPage} puts (${putsWithOI} with OI), ${callsInPage} calls (${callsWithOI} with OI)`);
      
      // Log when we find puts with OI
      if (putsWithOI > 0) {
        console.log(`Found ${putsWithOI} puts with open interest on page ${pageCount}`);
      }

      // Check for next page
      nextUrl = response.data.next_url;
      
      // Small delay to respect rate limits
      if (nextUrl) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }

      // Safety check to prevent infinite loops
      if (pageCount >= maxPages) {
        console.warn(`Reached maximum page limit (${maxPages}) for ${ticker}, stopping pagination`);
        break;
      }

    } while (nextUrl);

    console.log(`Completed ${pageCount} pages for ${ticker}, processed ${totalContracts} total contracts`);
    console.log(`Total Put OI: ${totalPutOI.toLocaleString()}, Total Call OI: ${totalCallOI.toLocaleString()}`);

    // Calculate PCR using Open Interest
    if (totalCallOI === 0) {
      console.log(`No call open interest found for ${ticker} - this means no call contracts have open_interest > 0`);
      console.log(`Total contracts processed: ${totalContracts}`);
      console.log(`Total Put OI: ${totalPutOI}, Total Call OI: ${totalCallOI}`);
      return null;
    }

    const pcr = totalPutOI / totalCallOI;
    console.log(`${ticker} OI PCR: ${totalPutOI.toLocaleString()} put OI / ${totalCallOI.toLocaleString()} call OI = ${pcr.toFixed(2)} (${pageCount} pages, ${totalContracts} contracts)`);

    return Number(pcr.toFixed(2));

  } catch (error) {
    console.error(`Error calculating OI PCR for ${ticker}:`, error.message);
    return null;
  }
};

/**
 * Calculate Put/Call Ratio for a ticker using Volume data (industry standard)
 * @param {string} ticker - Stock ticker symbol
 * @returns {Promise<number|null>} - PCR value or null if calculation fails
 */
const calculateTickerPCR = async (ticker) => {
  try {
    if (!POLYGON_API_TOKEN) {
      console.log('POLYGON_API_KEY not configured, skipping PCR calculation');
      return null;
    }

    console.log(`Calculating PCR for ${ticker}...`);

    let totalPutVolume = 0;
    let totalCallVolume = 0;
    let nextUrl = null;
    let pageCount = 0;
    const maxPages = 1000; // Safety limit to prevent infinite loops
    let totalContracts = 0;

    console.log(`Starting comprehensive PCR calculation for ${ticker}...`);

    // Fetch ALL pages of options data until no more data
    do {
      const url = nextUrl || `${POLYGON_BASE_URL}/${ticker.toUpperCase()}`;
      
      const response = nextUrl 
        ? await axios.get(url, { params: { apikey: POLYGON_API_TOKEN } }) // Add API key to next_url
        : await axios.get(url, { params: { apikey: POLYGON_API_TOKEN } });

      if (!response.data || !response.data.results) {
        console.log(`No options data found for ${ticker} on page ${pageCount + 1}`);
        break;
      }

      const options = response.data.results;
      pageCount++;
      totalContracts += options.length;

      // Aggregate Volume by option type (industry standard method)
      let putsInPage = 0;
      let callsInPage = 0;
      let putsWithVolume = 0;
      let callsWithVolume = 0;
      
      options.forEach(option => {
        const volume = option.day?.volume || 0;
        
        if (option.details && option.details.contract_type === 'put') {
          totalPutVolume += volume;
          putsInPage++;
          if (volume > 0) putsWithVolume++;
        } else if (option.details && option.details.contract_type === 'call') {
          totalCallVolume += volume;
          callsInPage++;
          if (volume > 0) callsWithVolume++;
        }
      });
      
      console.log(`Page ${pageCount}: ${putsInPage} puts (${putsWithVolume} with volume), ${callsInPage} calls (${callsWithVolume} with volume)`);

      // Check for next page
      nextUrl = response.data.next_url;
      
      // Small delay to respect rate limits
      if (nextUrl) {
        await new Promise(resolve => setTimeout(resolve, 100));
      }

      // Safety check to prevent infinite loops
      if (pageCount >= maxPages) {
        console.warn(`Reached maximum page limit (${maxPages}) for ${ticker}, stopping pagination`);
        break;
      }

    } while (nextUrl);

    console.log(`Completed ${pageCount} pages for ${ticker}, processed ${totalContracts} total contracts`);

    // Calculate PCR using volume (industry standard)
    if (totalCallVolume === 0) {
      console.log(`No call volume found for ${ticker}`);
      return null;
    }

    const pcr = totalPutVolume / totalCallVolume;
    console.log(`${ticker} Volume PCR: ${totalPutVolume.toLocaleString()} put volume / ${totalCallVolume.toLocaleString()} call volume = ${pcr.toFixed(2)} (${pageCount} pages, ${totalContracts} contracts)`);

    return Number(pcr.toFixed(2));

  } catch (error) {
    console.error(`Error calculating PCR for ${ticker}:`, error.message);
    return null;
  }
};

/**
 * Get PCR value for a ticker (from cache or calculate)
 * @param {string} ticker - Stock ticker symbol
 * @param {string} date - Date string (YYYY-MM-DD)
 * @param {string} method - 'volume' or 'oi' (default: 'volume')
 * @returns {Promise<number|null>} - PCR value or null
 */
const getTickerPCR = async (ticker, date = null, method = 'volume') => {
  // Default to today's date for end-of-day PCR calculations (cron runs after market close)
  const getTodayDate = () => {
    const today = new Date();
    
    // If it's weekend, get the last trading day (Friday)
    if (today.getDay() === 0) { // Sunday
      today.setDate(today.getDate() - 2); // Go back to Friday
    } else if (today.getDay() === 6) { // Saturday
      today.setDate(today.getDate() - 1); // Go back to Friday
    }
    
    return today.toISOString().slice(0, 10);
  };
  
  const targetDate = date || getTodayDate();
  const cacheKey = `${ticker.toUpperCase()}:${targetDate}:${method}`;

  // Check cache first
  if (pcrCache.has(cacheKey)) {
    console.log(`Using cached ${method} PCR for ${ticker} on ${targetDate}`);
    return pcrCache.get(cacheKey);
  }

  // Calculate and cache based on method
  let pcr;
  if (method === 'oi') {
    pcr = await calculateTickerPCR_OI(ticker);
  } else {
    pcr = await calculateTickerPCR(ticker);
  }
  
  if (pcr !== null) {
    pcrCache.set(cacheKey, pcr);
    console.log(`Cached ${method} PCR for ${ticker}: ${pcr}`);
  }

  return pcr;
};

/**
 * Get both Volume and OI PCR values for a ticker
 * @param {string} ticker - Stock ticker symbol
 * @param {string} date - Date string (YYYY-MM-DD)
 * @returns {Promise<Object>} - Object with both PCR values
 */
const getTickerPCRBoth = async (ticker, date = null) => {
  // Default to today's date for end-of-day PCR calculations (cron runs after market close)
  const getTodayDate = () => {
    const today = new Date();
    
    // If it's weekend, get the last trading day (Friday)
    if (today.getDay() === 0) { // Sunday
      today.setDate(today.getDate() - 2); // Go back to Friday
    } else if (today.getDay() === 6) { // Saturday
      today.setDate(today.getDate() - 1); // Go back to Friday
    }
    
    return today.toISOString().slice(0, 10);
  };
  
  const targetDate = date || getTodayDate();
  
  console.log(`Calculating both Volume and OI PCR for ${ticker} on ${targetDate}`);
  
  const [volumePCR, oiPCR] = await Promise.all([
    getTickerPCR(ticker, targetDate, 'volume'),
    getTickerPCR(ticker, targetDate, 'oi')
  ]);
  
  return {
    volume: volumePCR,
    oi: oiPCR,
    ticker: ticker.toUpperCase(),
    date: targetDate
  };
};

/**
 * Calculate PCR for multiple tickers
 * @param {string[]} tickers - Array of ticker symbols
 * @param {string} date - Date string (YYYY-MM-DD)
 * @returns {Promise<Object>} - Object with ticker as key and PCR as value
 */
const calculateMultiplePCR = async (tickers, date = null) => {
  const results = {};
  const targetDate = date || new Date().toISOString().slice(0, 10);

  console.log(`Calculating PCR for ${tickers.length} tickers on ${targetDate}`);

  // Process tickers in parallel with rate limiting
  const batchSize = 5; // Process 5 tickers at a time to avoid rate limits
  for (let i = 0; i < tickers.length; i += batchSize) {
    const batch = tickers.slice(i, i + batchSize);
    
    const batchPromises = batch.map(async (ticker) => {
      const pcr = await getTickerPCR(ticker, targetDate);
      return { ticker, pcr };
    });

    const batchResults = await Promise.all(batchPromises);
    
    batchResults.forEach(({ ticker, pcr }) => {
      results[ticker] = pcr;
    });

    // Small delay between batches to respect rate limits
    if (i + batchSize < tickers.length) {
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
  }

  console.log(`PCR calculation complete. Results:`, Object.keys(results).length);
  return results;
};

/**
 * Get cached PCR value without calculation
 * @param {string} ticker - Stock ticker symbol
 * @param {string} date - Date string (YYYY-MM-DD)
 * @returns {number|null} - Cached PCR value or null
 */
const getCachedPCR = (ticker, date = null) => {
  const targetDate = date || new Date().toISOString().slice(0, 10);
  const cacheKey = `${ticker.toUpperCase()}:${targetDate}`;
  return pcrCache.get(cacheKey) || null;
};

/**
 * Check if PCR is cached for a ticker and date
 * @param {string} ticker - Stock ticker symbol
 * @param {string} date - Date string (YYYY-MM-DD)
 * @returns {boolean} - True if cached
 */
const hasPCRForDate = (ticker, date = null) => {
  const targetDate = date || new Date().toISOString().slice(0, 10);
  const cacheKey = `${ticker.toUpperCase()}:${targetDate}`;
  return pcrCache.has(cacheKey);
};

/**
 * Clear PCR cache for a specific date or all dates
 * @param {string} date - Date string (YYYY-MM-DD), if null clears all
 */
const clearPCRCache = (date = null) => {
  if (date) {
    // Clear cache for specific date
    const keysToDelete = [];
    for (const key of pcrCache.keys()) {
      if (key.endsWith(`:${date}`)) {
        keysToDelete.push(key);
      }
    }
    keysToDelete.forEach(key => pcrCache.delete(key));
    console.log(`Cleared PCR cache for ${date}`);
  } else {
    // Clear all cache
    pcrCache.clear();
    console.log('Cleared all PCR cache');
  }
};

/**
 * Get cache statistics
 * @returns {Object} - Cache statistics
 */
const getCacheStats = () => {
  return {
    size: pcrCache.size,
    keys: Array.from(pcrCache.keys())
  };
};

module.exports = {
  calculateTickerPCR,
  calculateTickerPCR_OI,
  getTickerPCR,
  getTickerPCRBoth,
  calculateMultiplePCR,
  getCachedPCR,
  hasPCRForDate,
  clearPCRCache,
  getCacheStats
};
