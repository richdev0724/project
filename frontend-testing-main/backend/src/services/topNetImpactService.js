const axios = require('axios');

/**
 * Get top net impact data from Unusual Whales API
 * Returns top 10 bullish and top 10 bearish tickers by net premium
 * @returns {Promise<object>} - Top net impact data
 */
async function getTopNetImpact() {
  try {
    const UNUSUAL_WHALES_API_KEY = process.env.UW_API;
    
    if (!UNUSUAL_WHALES_API_KEY) {
      console.warn('UNUSUAL_WHALES_API_KEY not configured');
      return {
        success: false,
        error: 'API key not configured'
      };
    }

    console.log('Fetching top net impact data from Unusual Whales API...');
    
    const response = await axios.get('https://api.unusualwhales.com/api/market/top-net-impact', {
      params: {
        limit: 20  // Get 20 results total
      },
      headers: {
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`,
        'Accept': 'application/json'
      }
    });

    console.log('✅ Received response from Unusual Whales API');
    
    // API returns { "data": [...] } where each item has ticker and net_premium
    const data = response.data?.data || [];
    
    if (!Array.isArray(data)) {
      console.error('❌ Expected data to be an array, got:', typeof data);
      return {
        success: false,
        error: 'Unexpected API response structure'
      };
    }
    
    console.log(`📊 Processing ${data.length} items from API`);
    
    // Separate into bullish (positive net_premium) and bearish (negative net_premium)
    const bullish = data
      .filter(item => item.net_premium > 0)
      .sort((a, b) => b.net_premium - a.net_premium)
      .slice(0, 10);  // Top 10 bullish
    
    const bearish = data
      .filter(item => item.net_premium < 0)
      .sort((a, b) => a.net_premium - b.net_premium)
      .slice(0, 10);  // Top 10 bearish

    console.log(`✅ Separated: ${bullish.length} bullish, ${bearish.length} bearish tickers`);
    if (bullish.length > 0) console.log('   Top bullish:', bullish[0].ticker, `$${(bullish[0].net_premium / 1000000).toFixed(2)}M`);
    if (bearish.length > 0) console.log('   Top bearish:', bearish[0].ticker, `$${(bearish[0].net_premium / 1000000).toFixed(2)}M`);

    return {
      success: true,
      bullish,
      bearish,
      timestamp: new Date().toISOString()
    };

  } catch (error) {
    console.error('❌ Error fetching top net impact data:', error.message);
    if (error.response) {
      console.error('Response status:', error.response.status);
      console.error('Response data:', error.response.data);
      console.error('Response headers:', error.response.headers);
    }
    
    // Return error details
    return {
      success: false,
      error: error.response?.data?.message || error.message,
      statusCode: error.response?.status
    };
  }
}

module.exports = {
  getTopNetImpact
};

