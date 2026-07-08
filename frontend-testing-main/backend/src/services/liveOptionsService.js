const axios = require('axios');
require('dotenv').config();

const POLYGON_API_KEY = process.env.POLYGON_API_KEY;

/**
 * Get live options data for today from Polygon's Options Chain Snapshot API
 * @param {string} ticker - Stock ticker symbol (e.g., 'SPY')
 * @returns {Promise<object>} - Live options data with premiums
 */
async function getLiveOptionsData(ticker) {
  try {
    if (!POLYGON_API_KEY) {
      throw new Error('POLYGON_API_KEY not configured');
    }

    console.log(`Fetching live options data for ${ticker}...`);
    
    const response = await axios.get(`https://api.polygon.io/v3/snapshot/options/${ticker}`, {
      params: {
        apiKey: POLYGON_API_KEY
      }
    });

    if (response.data.status !== 'OK') {
      throw new Error(`Polygon API error: ${response.data.status}`);
    }

    const optionsData = response.data.results;
    
    if (!optionsData || optionsData.length === 0) {
      console.log(`No options data found for ${ticker}`);
      return {
        ticker,
        date: new Date().toISOString().split('T')[0],
        totalCalls: 0,
        totalPuts: 0,
        netPremium: 0,
        callVolume: 0,
        putVolume: 0,
        options: []
      };
    }

    // Calculate totals
    let totalCallPremium = 0;
    let totalPutPremium = 0;
    let totalCallVolume = 0;
    let totalPutVolume = 0;
    const options = [];

    optionsData.forEach(option => {
      const details = option.details;
      const lastQuote = option.last_quote;
      const day = option.day;
      const lastTrade = option.last_trade;
      
      if (!details || !lastQuote) return;

      // Use midpoint price as the premium (average of bid/ask)
      const premium = parseFloat(lastQuote.midpoint || 0) || 0;
      
      // Use day volume if available, otherwise use last trade size
      const volume = parseInt(day?.volume || lastTrade?.size || 0) || 0;
      const openInterest = parseInt(option.open_interest || 0) || 0;
      
      const optionData = {
        symbol: details.ticker,
        strike: details.strike_price,
        expiration: details.expiration_date,
        type: details.contract_type,
        premium: premium,
        volume: volume,
        openInterest: openInterest,
        lastTrade: lastTrade
      };

      options.push(optionData);

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

    console.log(`${ticker} Live Options Summary:`);
    console.log(`- Call Premium: $${totalCallPremium.toLocaleString()}`);
    console.log(`- Put Premium: $${totalPutPremium.toLocaleString()}`);
    console.log(`- Net Premium: $${netPremium.toLocaleString()}`);
    console.log(`- Call Volume: ${totalCallVolume.toLocaleString()}`);
    console.log(`- Put Volume: ${totalPutVolume.toLocaleString()}`);

    return {
      ticker,
      date: new Date().toISOString().split('T')[0],
      totalCalls: Math.round(totalCallPremium),
      totalPuts: Math.round(totalPutPremium),
      netPremium: Math.round(netPremium),
      callVolume: totalCallVolume,
      putVolume: totalPutVolume,
      options: options.slice(0, 100) // Limit to first 100 options for response size
    };

  } catch (error) {
    console.error(`Error fetching live options data for ${ticker}:`, error.message);
    throw error;
  }
}

/**
 * Get live net premiums for a ticker (simplified version for chart)
 * @param {string} ticker - Stock ticker symbol
 * @returns {Promise<object>} - Live net premiums data
 */
async function getLiveNetPremiums(ticker) {
  try {
    const liveData = await getLiveOptionsData(ticker);
    
    return {
      ticker,
      timeframe: '1D',
      data: [{
        name: 'Today',
        calls: liveData.totalCalls,
        puts: liveData.totalPuts,
        callVolume: liveData.callVolume,
        putVolume: liveData.putVolume,
        netPremium: liveData.netPremium
      }],
      totalCalls: liveData.totalCalls,
      totalPuts: liveData.totalPuts,
      netPremium: liveData.netPremium
    };

  } catch (error) {
    console.error(`Error getting live net premiums for ${ticker}:`, error.message);
    return {
      ticker,
      timeframe: '1D',
      data: [],
      totalCalls: 0,
      totalPuts: 0,
      netPremium: 0
    };
  }
}

module.exports = {
  getLiveOptionsData,
  getLiveNetPremiums
};
