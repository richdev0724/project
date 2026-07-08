const axios = require('axios');

/**
 * Get market tide data from Unusual Whales API
 * Returns time series data for net call premium, net put premium, and net volume
 * @param {string} date - Optional date parameter (YYYY-MM-DD format)
 * @param {string} endDate - Optional end date parameter (YYYY-MM-DD format)
 * @returns {Promise<object>} - Market tide data
 */
async function getMarketTide(date = null, endDate = null) {
  try {
    const UW_API_KEY = process.env.UW_API;
    
    if (!UW_API_KEY) {
      console.warn('UW_API key not configured');
      return {
        success: false,
        error: 'API key not configured'
      };
    }

    console.log('🌊 Fetching market tide data from Unusual Whales API...');
    
    // If we have a date range, fetch each day individually
    let allMarketTideData = [];
    let allSpyOhlcData = [];
    
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
          console.log(`   📅 Fetching data for ${dateStr}...`);
          
          // Fetch market tide for this date
          const marketTideResponse = await axios.get('https://api.unusualwhales.com/api/market/market-tide', {
            params: {
              interval_5m: false,
              otm_only: true,
              date: dateStr
            },
            headers: {
              'Authorization': `Bearer ${UW_API_KEY}`,
              'Accept': 'application/json'
            }
          });
          
          // Fetch SPY OHLC for this date
          const spyOhlcResponse = await axios.get('https://api.unusualwhales.com/api/stock/SPY/ohlc/1m', {
            params: {
              date: dateStr
            },
            headers: {
              'Authorization': `Bearer ${UW_API_KEY}`,
              'Accept': 'application/json'
            }
          });
          
          const dayMarketTideData = marketTideResponse.data?.data || [];
          const daySpyOhlcData = spyOhlcResponse.data?.data || [];
          
          console.log(`      ✅ ${dateStr}: ${dayMarketTideData.length} market tide points, ${daySpyOhlcData.length} SPY points`);
          
          allMarketTideData = allMarketTideData.concat(dayMarketTideData);
          allSpyOhlcData = allSpyOhlcData.concat(daySpyOhlcData);
          
        } catch (error) {
          console.error(`      ❌ Error fetching data for ${dateStr}:`, error.message);
          // Continue with next date even if one fails
        }
      }
      
      console.log(`✅ Total: ${allMarketTideData.length} market tide points, ${allSpyOhlcData.length} SPY points`);
      
    } else {
      // Single date or no date - fetch once
      if (date) console.log(`   Date: ${date}`);
      
      const marketTideParams = {
        interval_5m: false,
        otm_only: true
      };
      if (date) marketTideParams.date = date;
      
      const marketTideResponse = await axios.get('https://api.unusualwhales.com/api/market/market-tide', {
        params: marketTideParams,
        headers: {
          'Authorization': `Bearer ${UW_API_KEY}`,
          'Accept': 'application/json'
        }
      });
      
      const spyOhlcParams = {};
      if (date) spyOhlcParams.date = date;
      
      const spyOhlcResponse = await axios.get('https://api.unusualwhales.com/api/stock/SPY/ohlc/1m', {
        params: spyOhlcParams,
        headers: {
          'Authorization': `Bearer ${UW_API_KEY}`,
          'Accept': 'application/json'
        }
      });
      
      allMarketTideData = marketTideResponse.data?.data || [];
      allSpyOhlcData = spyOhlcResponse.data?.data || [];
      
      console.log(`✅ Received ${allMarketTideData.length} market tide points, ${allSpyOhlcData.length} SPY points`);
    }
    
    // Process the combined data
    const marketTideData = allMarketTideData;
    const spyOhlcData = allSpyOhlcData;
    
    if (!Array.isArray(marketTideData) || !Array.isArray(spyOhlcData)) {
      console.error('❌ Expected data to be arrays');
      return {
        success: false,
        error: 'Unexpected API response structure'
      };
    }
    
    console.log(`📊 Processing ${marketTideData.length} market tide data points and ${spyOhlcData.length} SPY OHLC data points`);
    
    if (marketTideData.length > 0) {
      console.log('   Sample market tide timestamp:', marketTideData[0].timestamp);
    }
    if (spyOhlcData.length > 0) {
      console.log('   Sample SPY OHLC timestamp:', spyOhlcData[0].start_time);
    }
    
    // Create a map of SPY prices - use a more flexible timestamp matching
    const spyPriceMap = new Map();
    spyOhlcData.forEach(item => {
      const timestamp = new Date(item.start_time).getTime();
      spyPriceMap.set(timestamp, parseFloat(item.close));
      
      // Also add rounded timestamps for better matching (round to nearest minute)
      const roundedTimestamp = Math.floor(timestamp / 60000) * 60000;
      if (!spyPriceMap.has(roundedTimestamp)) {
        spyPriceMap.set(roundedTimestamp, parseFloat(item.close));
      }
    });
    
    console.log(`   Created SPY price map with ${spyPriceMap.size} entries`);
    
    // Transform market tide data and merge with SPY prices
    const transformedData = marketTideData.map(item => {
      const timestamp = new Date(item.timestamp).getTime();
      const roundedTimestamp = Math.floor(timestamp / 60000) * 60000;
      
      // Try exact match first, then rounded timestamp
      let spyClose = spyPriceMap.get(timestamp) || spyPriceMap.get(roundedTimestamp);
      
      // If still no match, find closest timestamp within 2 minutes
      if (!spyClose) {
        for (let offset = -120000; offset <= 120000; offset += 60000) {
          const testTimestamp = timestamp + offset;
          if (spyPriceMap.has(testTimestamp)) {
            spyClose = spyPriceMap.get(testTimestamp);
            break;
          }
        }
      }
      
      return {
        timestamp,
        date: item.date,
        call_premium: parseFloat(item.net_call_premium),
        put_premium: parseFloat(item.net_put_premium),
        net_volume: parseInt(item.net_volume),
        spy_close: spyClose || null,
        original_timestamp: item.timestamp
      };
    });

    console.log(`✅ Transformed ${transformedData.length} data points for market tide with SPY prices`);
    
    // Count how many have SPY prices
    const withSpyPrice = transformedData.filter(d => d.spy_close !== null).length;
    console.log(`   ${withSpyPrice} out of ${transformedData.length} have SPY prices`);
    
    if (transformedData.length > 0) {
      console.log('   First data point:', {
        timestamp: transformedData[0].original_timestamp,
        call_premium: `$${(transformedData[0].call_premium / 1000000).toFixed(2)}M`,
        put_premium: `$${(transformedData[0].put_premium / 1000000).toFixed(2)}M`,
        net_volume: transformedData[0].net_volume,
        spy_close: transformedData[0].spy_close
      });
      
      console.log('   Last data point:', {
        timestamp: transformedData[transformedData.length - 1].original_timestamp,
        call_premium: `$${(transformedData[transformedData.length - 1].call_premium / 1000000).toFixed(2)}M`,
        put_premium: `$${(transformedData[transformedData.length - 1].put_premium / 1000000).toFixed(2)}M`,
        net_volume: transformedData[transformedData.length - 1].net_volume,
        spy_close: transformedData[transformedData.length - 1].spy_close
      });
    }

    return {
      success: true,
      data: transformedData,
      timestamp: new Date().toISOString()
    };

  } catch (error) {
    console.error('❌ Error fetching market tide data:', error.message);
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
  getMarketTide
};

