const axios = require('axios');
require('dotenv').config();


async function fetchMarketPrices(tickerList, multiplier, timespan, fromDate, toDate) {
  const apiKey = process.env.POLYGON_API_KEY;
  
  if (!apiKey) {
    console.error('POLYGON_API_KEY is not set in environment variables');
    throw new Error('Polygon API key is not configured');
  }

  const results = {};
  await Promise.all(tickerList.map(async (ticker) => {
    let limit = 50000;
    if (timespan === 'day') {
      limit = 120;
    }
    console.log(`Ticker: ${ticker}, fromDate: ${fromDate}, toDate: ${toDate}, timespan: ${timespan}, multiplier: ${multiplier}`);
    const url = `https://api.polygon.io/v2/aggs/ticker/${ticker}/range/${multiplier}/${timespan}/${fromDate}/${toDate}` +
      `?adjusted=true&sort=asc&limit=50000&apiKey=${apiKey}`;

    console.log(`Fetching data for ${ticker}: ${url}`);
    try {
      const response = await axios.get(url);
      let data = response.data;
      // Filter out weekend data and ensure market hours, but only for minute/hour/day data
      if (data.results && Array.isArray(data.results)) {
        // For weekly data, don't filter by day of week since weekly aggregates represent full weeks
        if (timespan === 'week' || timespan === 'month' || timespan === 'quarter' || timespan === 'year') {
          // Keep all data for longer timeframes
          console.log(`Keeping all ${data.results.length} results for ${ticker} (${timespan} timespan)`);
        } else {
          // Apply day-of-week and market hours filtering for shorter timeframes
          data.results = data.results.filter(item => {
            // Polygon 't' is in milliseconds UTC
            const date = new Date(item.t);
            // Convert to EST (UTC-5 or UTC-4 for DST, but Polygon uses New York time for US stocks)
            const estDate = new Date(date.toLocaleString('en-US', { timeZone: 'America/New_York' }));
            const day = estDate.getDay();
            
            // Skip weekends (0 = Sunday, 6 = Saturday)
            if (day === 0 || day === 6) {
              return false;
            }
            
            // For minute data, also check market hours
            if (timespan === 'minute') {
              const hours = estDate.getHours();
              const minutes = estDate.getMinutes();
              // Market opens at 9:30am (9*60+30=570), closes at 4:00pm (16*60=960)
              const totalMinutes = hours * 60 + minutes;
              return totalMinutes >= 570 && totalMinutes <= 960;
            }
            
            return true;
          });
          console.log(`After filtering: ${data.results.length} results for ${ticker} (${timespan} timespan)`);
        }
      }
      console.log(`Data for ${ticker}:`, JSON.stringify(data).slice(0, 500));
      results[ticker] = data;
    } catch (err) {
      console.error(`Error fetching ${ticker}:`, err.response?.data || err.message);
      results[ticker] = { error: err.response?.data || err.message };
    }
  }));
  return results;
}

async function fetchSectorSnapshots(tickers) {
  const apiKey = process.env.POLYGON_API_KEY;
  
  if (!apiKey) {
    console.error('POLYGON_API_KEY is not set in environment variables');
    throw new Error('Polygon API key is not configured');
  }

  const results = {};
  await Promise.all(tickers.map(async (ticker) => {
    const url = `https://api.polygon.io/v2/snapshot/locale/us/markets/stocks/tickers/${ticker}?apiKey=${apiKey}`;

    console.log(`Fetching snapshot for ${ticker}`);
    try {
      const response = await axios.get(url);
      results[ticker] = response.data.ticker;
    } catch (err) {
      console.error(`Error fetching ${ticker} snapshot:`, err.response?.data || err.message);
      results[ticker] = { error: err.response?.data || err.message };
    }
  }));
  return results;
}

module.exports = { fetchMarketPrices, fetchSectorSnapshots };