const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get comprehensive options data for a ticker using Options Chain Snapshot API with pagination
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @returns {Promise<object>} - Comprehensive options data with premiums
 */
async function getComprehensiveOptionsData(ticker) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Fetching comprehensive options data for ${ticker}...`);
    
    let allOptions = [];
    let nextUrl = null;
    let pageCount = 0;
    const maxPages = 200; // Safety limit

    // First request
    const initialResponse = await axios.get(`https://api.polygon.io/v3/snapshot/options/${ticker}`, {
      params: {
        apiKey: POLYGON_API_KEY,
        limit: 100 // Maximum limit per request
      }
    });

    if (initialResponse.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${initialResponse.data.status}`);
    }

    allOptions = allOptions.concat(initialResponse.data.results || []);
    nextUrl = initialResponse.data.next_url;
    pageCount++;

    console.log(`Page ${pageCount}: Found ${initialResponse.data.results?.length || 0} options`);

    // Paginate through all results
    while (nextUrl && pageCount < maxPages) {
      try {
        const response = await axios.get(nextUrl);
        
        if (response.data.status !== 'OK') {
          console.log(`API error on page ${pageCount + 1}: ${response.data.status}`);
          break;
        }

        const newOptions = response.data.results || [];
        allOptions = allOptions.concat(newOptions);
        nextUrl = response.data.next_url;
        pageCount++;

        console.log(`Page ${pageCount}: Found ${newOptions.length} options (Total: ${allOptions.length})`);

        // Small delay to avoid rate limiting
        await new Promise(resolve => setTimeout(resolve, 200));

      } catch (error) {
        console.error(`Error fetching page ${pageCount + 1}:`, error.message);
        break;
      }
    }

    console.log(`Total options fetched: ${allOptions.length}`);

    if (allOptions.length === 0) {
      return {
        ticker,
        date: new Date().toISOString().split('T')[0],
        totalCallPremium: 0,
        totalPutPremium: 0,
        netPremium: 0,
        totalCallVolume: 0,
        totalPutVolume: 0,
        totalOptions: 0
      };
    }

    // Calculate totals
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;

    allOptions.forEach(option => {
      const details = option.details;
      const lastQuote = option.last_quote;
      const day = option.day;
      const lastTrade = option.last_trade;
      
      if (!details || !lastQuote) return;

      // Use midpoint price as the premium (average of bid/ask)
      const premium = parseFloat(lastQuote.midpoint || 0) || 0;
      
      // Use day volume if available, otherwise use last trade size
      const volume = parseInt(day?.volume || lastTrade?.size || 0) || 0;
      
      // Calculate total premium (price * volume * 100 shares per contract)
      const totalPremium = premium * volume * 100;

      if (details.contract_type === 'call') {
        totalCallPremium += totalPremium;
        totalCallVolume += volume;
      } else if (details.contract_type === 'put') {
        totalPutPremium += totalPremium;
        totalPutVolume += volume;
      }
    });

    const netPremium = totalCallPremium - totalPutPremium;

    console.log(`${ticker} Comprehensive Options Summary:`);
    console.log(`- Total Options: ${allOptions.length}`);
    console.log(`- Call Premium: $${totalCallPremium.toLocaleString()}`);
    console.log(`- Put Premium: $${totalPutPremium.toLocaleString()}`);
    console.log(`- Net Premium: $${netPremium.toLocaleString()}`);
    console.log(`- Call Volume: ${totalCallVolume.toLocaleString()}`);
    console.log(`- Put Volume: ${totalPutVolume.toLocaleString()}`);

    return {
      ticker,
      date: new Date().toISOString().split('T')[0],
      totalCallPremium: Math.round(totalCallPremium),
      totalPutPremium: Math.round(totalPutPremium),
      netPremium: Math.round(netPremium),
      totalCallVolume,
      totalPutVolume,
      totalOptions: allOptions.length
    };

  } catch (error) {
    console.error(`Error fetching comprehensive options data for ${ticker}:`, error.message);
    throw error;
  }
}

module.exports = {
  getComprehensiveOptionsData
};
