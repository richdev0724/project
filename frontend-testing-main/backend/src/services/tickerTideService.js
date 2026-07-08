const axios = require('axios');

/**
 * Get ticker-specific tide data (intraday premiums and volume)
 * Returns time series data for net call premium, net put premium, net volume, and ticker price
 * @param {string} ticker - Stock ticker symbol
 * @param {string} date - Optional date parameter (YYYY-MM-DD format)
 * @param {string} endDate - Optional end date parameter (YYYY-MM-DD format)
 * @returns {Promise<object>} - Ticker tide data
 */
async function getTickerTide(ticker, date = null, endDate = null) {
  try {
    const UW_API_KEY = process.env.UW_API;
    
    if (!UW_API_KEY) {
      console.warn('UW_API key not configured');
      return {
        success: false,
        error: 'API key not configured'
      };
    }

    console.log(`🌊 Fetching ticker tide data for ${ticker} from Unusual Whales API...`);
    
    // If we have a date range, fetch each day individually
    let allTickerTideData = [];
    let allTickerOhlcData = [];
    
    if (date && endDate) {
      console.log(`   Fetching data from ${date} to ${endDate}`);
      
      // Generate array of dates between start and end, excluding weekends
      const dates = [];
      const startDate = new Date(date);
      const endDateObj = new Date(endDate);
      
      for (let d = new Date(startDate); d <= endDateObj; d.setDate(d.getDate() + 1)) {
        const dayOfWeek = d.getDay();
        // Skip Saturday (6) and Sunday (0)
        if (dayOfWeek !== 0 && dayOfWeek !== 6) {
          dates.push(d.toISOString().split('T')[0]);
        }
      }
      
      console.log(`   Will fetch data for ${dates.length} trading days:`, dates);
      
      // Fetch data for each date
      for (const dateStr of dates) {
        try {
          console.log(`   📅 Fetching data for ${ticker} on ${dateStr}...`);
          
          // Fetch ticker-specific net premium ticks data for this date
          const tickerTideResponse = await axios.get(`https://api.unusualwhales.com/api/stock/${ticker}/net-prem-ticks`, {
            params: {
              date: dateStr
            },
            headers: {
              'Authorization': `Bearer ${UW_API_KEY}`,
              'Accept': 'application/json'
            }
          });
          
          // Fetch ticker OHLC for this date
          const tickerOhlcResponse = await axios.get(`https://api.unusualwhales.com/api/stock/${ticker}/ohlc/1m`, {
            params: {
              date: dateStr
            },
            headers: {
              'Authorization': `Bearer ${UW_API_KEY}`,
              'Accept': 'application/json'
            }
          });
          
          const dayTickerTideData = tickerTideResponse.data?.data || [];
          const dayTickerOhlcData = tickerOhlcResponse.data?.data || [];
          
          console.log(`      ✅ ${dateStr}: ${dayTickerTideData.length} ticker tide points, ${dayTickerOhlcData.length} ${ticker} price points`);
          
          allTickerTideData = allTickerTideData.concat(dayTickerTideData);
          allTickerOhlcData = allTickerOhlcData.concat(dayTickerOhlcData);
          
        } catch (error) {
          console.error(`      ❌ Error fetching data for ${dateStr}:`, error.message);
          // Continue with next date even if one fails
        }
      }
      
      console.log(`✅ Total: ${allTickerTideData.length} ticker tide points, ${allTickerOhlcData.length} ${ticker} price points`);
      
    } else {
      // Single date or no date - fetch once
      if (date) console.log(`   Date: ${date}`);
      
      const tickerTideParams = {};
      if (date) tickerTideParams.date = date;
      
      const tickerTideResponse = await axios.get(`https://api.unusualwhales.com/api/stock/${ticker}/net-prem-ticks`, {
        params: tickerTideParams,
        headers: {
          'Authorization': `Bearer ${UW_API_KEY}`,
          'Accept': 'application/json'
        }
      });
      
      const tickerOhlcParams = {};
      if (date) tickerOhlcParams.date = date;
      
      const tickerOhlcResponse = await axios.get(`https://api.unusualwhales.com/api/stock/${ticker}/ohlc/1m`, {
        params: tickerOhlcParams,
        headers: {
          'Authorization': `Bearer ${UW_API_KEY}`,
          'Accept': 'application/json'
        }
      });
      
      allTickerTideData = tickerTideResponse.data?.data || [];
      allTickerOhlcData = tickerOhlcResponse.data?.data || [];
      
      console.log(`✅ Received ${allTickerTideData.length} ticker tide points, ${allTickerOhlcData.length} ${ticker} price points`);
    }
    
    // Process the combined data
    const tickerTideData = allTickerTideData;
    const tickerOhlcData = allTickerOhlcData;
    
    if (!Array.isArray(tickerTideData) || !Array.isArray(tickerOhlcData)) {
      console.error('❌ Expected data to be arrays');
      return {
        success: false,
        error: 'Unexpected API response structure'
      };
    }
    
    console.log(`📊 Processing ${tickerTideData.length} ticker tide data points and ${tickerOhlcData.length} ${ticker} OHLC data points`);
    
    if (tickerTideData.length > 0) {
      console.log('   Sample ticker tide timestamp:', tickerTideData[0].tape_time);
    }
    if (tickerOhlcData.length > 0) {
      console.log('   Sample ticker OHLC timestamp:', tickerOhlcData[0].start_time);
    }
    
    // Create a map of ticker prices - use a more flexible timestamp matching
    const tickerPriceMap = new Map();
    tickerOhlcData.forEach(item => {
      const timestamp = new Date(item.start_time).getTime();
      tickerPriceMap.set(timestamp, parseFloat(item.close));
      
      // Also add rounded timestamps for better matching (round to nearest minute)
      const roundedTimestamp = Math.floor(timestamp / 60000) * 60000;
      if (!tickerPriceMap.has(roundedTimestamp)) {
        tickerPriceMap.set(roundedTimestamp, parseFloat(item.close));
      }
    });
    
    console.log(`   Created ${ticker} price map with ${tickerPriceMap.size} entries`);
    
    // Transform ticker tide data and merge with ticker prices
    // Each tick needs to be accumulated with previous ticks to build a daily chart
    const transformedData = [];
    let cumulativeCallPremium = 0;
    let cumulativePutPremium = 0;
    let cumulativeCallVolume = 0;
    let cumulativePutVolume = 0;
    
    tickerTideData.forEach((item, idx) => {
      const timestamp = new Date(item.tape_time).getTime();
      const roundedTimestamp = Math.floor(timestamp / 60000) * 60000;
      
      // Try exact match first, then rounded timestamp
      let tickerClose = tickerPriceMap.get(timestamp) || tickerPriceMap.get(roundedTimestamp);
      
      // If still no match, find closest timestamp within 2 minutes
      if (!tickerClose) {
        for (let offset = -120000; offset <= 120000; offset += 60000) {
          const testTimestamp = timestamp + offset;
          if (tickerPriceMap.has(testTimestamp)) {
            tickerClose = tickerPriceMap.get(testTimestamp);
            break;
          }
        }
      }
      
      // Parse current tick values
      const currentCallPremium = parseFloat(item.net_call_premium) || 0;
      const currentPutPremium = parseFloat(item.net_put_premium) || 0;
      const currentCallVolume = parseInt(item.net_call_volume) || 0;
      const currentPutVolume = parseInt(item.net_put_volume) || 0;
      
      // Accumulate values (add current tick to running total)
      cumulativeCallPremium += currentCallPremium;
      cumulativePutPremium += currentPutPremium;
      cumulativeCallVolume += currentCallVolume;
      cumulativePutVolume += currentPutVolume;
      
      // Calculate net volume from cumulative call and put volumes
      const netVolume = cumulativeCallVolume + cumulativePutVolume;
      
      transformedData.push({
        timestamp,
        date: item.date,
        call_premium: cumulativeCallPremium,
        put_premium: cumulativePutPremium,
        net_volume: netVolume,
        ticker_close: tickerClose || null,
        original_timestamp: item.tape_time
      });
    });

    console.log(`✅ Transformed ${transformedData.length} data points for ${ticker} tide with ${ticker} prices`);
    
    // Count how many have ticker prices
    const withTickerPrice = transformedData.filter(d => d.ticker_close !== null).length;
    console.log(`   ${withTickerPrice} out of ${transformedData.length} have ${ticker} prices`);
    
    if (transformedData.length > 0) {
      console.log('   First data point:', {
        timestamp: transformedData[0].original_timestamp,
        call_premium: `$${(transformedData[0].call_premium / 1000000).toFixed(2)}M`,
        put_premium: `$${(transformedData[0].put_premium / 1000000).toFixed(2)}M`,
        net_volume: transformedData[0].net_volume,
        ticker_close: transformedData[0].ticker_close
      });
      
      console.log('   Last data point:', {
        timestamp: transformedData[transformedData.length - 1].original_timestamp,
        call_premium: `$${(transformedData[transformedData.length - 1].call_premium / 1000000).toFixed(2)}M`,
        put_premium: `$${(transformedData[transformedData.length - 1].put_premium / 1000000).toFixed(2)}M`,
        net_volume: transformedData[transformedData.length - 1].net_volume,
        ticker_close: transformedData[transformedData.length - 1].ticker_close
      });
    }

    return {
      success: true,
      data: transformedData,
      timestamp: new Date().toISOString()
    };

  } catch (error) {
    console.error(`❌ Error fetching ticker tide data for ${ticker}:`, error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
    }
    
    return {
      success: false,
      error: error.response?.data?.message || error.message,
      statusCode: error.response?.status
    };
  }
}

module.exports = {
  getTickerTide
};

