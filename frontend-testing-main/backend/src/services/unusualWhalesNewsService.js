const axios = require('axios');

const UNUSUAL_WHALES_API_URL = 'https://api.unusualwhales.com/api/news/headlines';
const UNUSUAL_WHALES_API_KEY = process.env.UW_API;

/**
 * Fetch news headlines from Unusual Whales API
 * @returns {Promise<Object>} News headlines data
 */
const getUnusualWhalesNews = async () => {
  try {
    if (!UNUSUAL_WHALES_API_KEY) {
      console.error('UW_API_KEY is not set in environment variables');
      return {
        data: [],
        success: false,
        error: 'API key not configured'
      };
    }

    const response = await axios.get(UNUSUAL_WHALES_API_URL, {
      headers: {
        'Authorization': `Bearer ${UNUSUAL_WHALES_API_KEY}`,
      },
      timeout: 10000, // 10 second timeout
    });

    if (!response.data || !response.data.data) {
      throw new Error('Invalid response from Unusual Whales API');
    }

    // Return first 20 headlines
    return {
      data: response.data.data.slice(0, 20),
      success: true
    };
  } catch (error) {
    console.error('Error fetching Unusual Whales news:', error.message);
    
    // Return empty data on error
    return {
      data: [],
      success: false,
      error: error.message
    };
  }
};

module.exports = {
  getUnusualWhalesNews
};

