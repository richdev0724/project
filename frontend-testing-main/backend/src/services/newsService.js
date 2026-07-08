const axios = require('axios');

const POLYGON_API_TOKEN = process.env.POLYGON_API_KEY;
const MARKETAUX_API_TOKEN = process.env.MARKETAUX_API_TOKEN;
const FMP_API_TOKEN = process.env.FMP_API_KEY;
const POLYGON_BASE_URL = 'https://api.polygon.io/v2/reference/news';
const MARKETAUX_BASE_URL = 'https://api.marketaux.com/v1/news/all';
const FMP_BASE_URL = 'https://financialmodelingprep.com/api/v3';

// Store cursors in memory for pagination
const cursors = new Map();

const getPolygonNews = async (page = 1) => {
  try {
    let url = POLYGON_BASE_URL;
    let params = {
      order: 'desc',
      limit: 20, // Fetch more to account for filtering
      sort: 'published_utc',
      apiKey: POLYGON_API_TOKEN
    };

    if (page > 1) {
      const cursor = cursors.get(page - 1);
      if (cursor) {
        url = cursor;
        params = { apiKey: POLYGON_API_TOKEN };
      }
    }

    const response = await axios.get(url, { params });

    // Store the next_url as cursor for next page
    if (response.data.next_url) {
      cursors.set(page, response.data.next_url);
    }

    // Return all articles for better coverage
    return {
      articles: response.data.results.map(article => ({
        title: article.title,
        snippet: article.description,
        image_url: article.image_url,
        published_at: article.published_utc,
        article_url: article.article_url,
        author: article.author,
        publisher: article.publisher,
        tickers: article.tickers,
        source: 'polygon'
      })),
      hasNextPage: !!response.data.next_url
    };
  } catch (error) {
    console.error('Error fetching Polygon news:', error);
    return { articles: [], hasNextPage: false };
  }
};

const getMarketauxNews = async (page = 1) => {
  try {
    // Focus on broader market topics instead of specific stocks
    const response = await axios.get(MARKETAUX_BASE_URL, {
      params: {
        categories: 'business,finance,markets',
        language: 'en',
        api_token: MARKETAUX_API_TOKEN,
        page: page,
        limit: 8
      }
    });

    return {
      articles: response.data.data.map(article => ({
        title: article.title,
        snippet: article.snippet,
        image_url: article.image_url,
        published_at: article.published_at,
        article_url: article.url,
        source: 'marketaux'
      })),
      hasNextPage: page * 8 < response.data.meta.found
    };
  } catch (error) {
    console.error('Error fetching Marketaux news:', error);
    return { articles: [], hasNextPage: false };
  }
};

const getFMPNews = async (page = 1) => {
  try {
    if (!FMP_API_TOKEN) {
      console.log('FMP_API_KEY not configured, skipping FMP news');
      return { articles: [], hasNextPage: false };
    }

    // Get general market news instead of individual stock news
    const params = {
      apikey: FMP_API_TOKEN,
      limit: 10,
      page: page
    };

    const response = await axios.get(`${FMP_BASE_URL}/general_news`, { params });

    return {
      articles: response.data.map(article => ({
        title: article.title,
        snippet: article.text || article.title,
        image_url: article.image || '',
        published_at: article.publishedDate,
        article_url: article.url,
        author: article.author || 'FMP',
        publisher: {
          name: 'Financial Modeling Prep',
          logo_url: '',
          favicon_url: '',
          homepage_url: 'https://financialmodelingprep.com'
        },
        tickers: [], // General market news doesn't have specific tickers
        source: 'fmp'
      })),
      hasNextPage: response.data.length === 10 // Assume has next page if we got full limit
    };
  } catch (error) {
    console.error('Error fetching FMP news:', error);
    return { articles: [], hasNextPage: false };
  }
};

const getTickerNews = async (ticker, page = 1) => {
  try {
    if (!ticker) {
      throw new Error('Ticker symbol is required');
    }

    // Fetch from all three sources in parallel
    const [polygonData, marketauxData, fmpData] = await Promise.all([
      getPolygonTickerNews(ticker, page),
      getMarketauxTickerNews(ticker, page),
      getFMPTickerNews(ticker, page)
    ]);

    // Create a Set to track unique titles
    const seenTitles = new Set();

    // Combine and sort articles by date, removing duplicates based on title
    const allArticles = [...polygonData.articles, ...marketauxData.articles, ...fmpData.articles]
      .sort((a, b) => new Date(b.published_at) - new Date(a.published_at))
      .filter(article => {
        // Normalize the title by removing whitespace and converting to lowercase
        const normalizedTitle = article.title.toLowerCase().trim();
        if (seenTitles.has(normalizedTitle)) {
          return false;
        }
        seenTitles.add(normalizedTitle);
        
        // Strict filtering: only return articles that explicitly mention the ticker in the title
        const titleContainsTicker = normalizedTitle.includes(ticker.toLowerCase());
        
        // For now, only return articles that have the ticker in the title
        // This ensures we only get META-specific news
        return titleContainsTicker;
      })
      .slice(0, 10); // Take 10 most recent unique articles

    return {
      data: allArticles,
      meta: {
        found: 100, // Approximate total
        returned: allArticles.length,
        limit: 10,
        page: page,
        hasNextPage: polygonData.hasNextPage || marketauxData.hasNextPage || fmpData.hasNextPage
      }
    };
  } catch (error) {
    console.error('Error fetching ticker news:', error);
    throw error;
  }
};

const getPolygonTickerNews = async (ticker, page = 1) => {
  try {
    let url = POLYGON_BASE_URL;
    let params = {
      ticker: ticker.toUpperCase(),
      order: 'desc',
      limit: 20,
      sort: 'published_utc',
      apiKey: POLYGON_API_TOKEN
    };

    if (page > 1) {
      const cursor = cursors.get(`${ticker}-${page - 1}`);
      if (cursor) {
        url = cursor;
        params = { apiKey: POLYGON_API_TOKEN };
      }
    }

    const response = await axios.get(url, { params });

    // Store the next_url as cursor for next page
    if (response.data.next_url) {
      cursors.set(`${ticker}-${page}`, response.data.next_url);
    }

    return {
      articles: response.data.results.map(article => ({
        title: article.title,
        snippet: article.description,
        image_url: article.image_url,
        published_at: article.published_utc,
        article_url: article.article_url,
        author: article.author,
        publisher: article.publisher,
        tickers: article.tickers,
        source: 'polygon'
      })),
      hasNextPage: !!response.data.next_url
    };
  } catch (error) {
    console.error('Error fetching Polygon ticker news:', error);
    return { articles: [], hasNextPage: false };
  }
};

const getMarketauxTickerNews = async (ticker, page = 1) => {
  try {
    const response = await axios.get(MARKETAUX_BASE_URL, {
      params: {
        symbols: ticker.toUpperCase(),
        language: 'en',
        api_token: MARKETAUX_API_TOKEN,
        page: page,
        limit: 8
      }
    });

    return {
      articles: response.data.data.map(article => ({
        title: article.title,
        snippet: article.snippet,
        image_url: article.image_url,
        published_at: article.published_at,
        article_url: article.url,
        source: 'marketaux'
      })),
      hasNextPage: page * 8 < response.data.meta.found
    };
  } catch (error) {
    console.error('Error fetching Marketaux ticker news:', error);
    return { articles: [], hasNextPage: false };
  }
};

const getFMPTickerNews = async (ticker, page = 1) => {
  try {
    if (!FMP_API_TOKEN) {
      console.log('FMP_API_KEY not configured, skipping FMP ticker news');
      return { articles: [], hasNextPage: false };
    }

    const params = {
      apikey: FMP_API_TOKEN,
      limit: 10,
      page: page
    };

    const response = await axios.get(`${FMP_BASE_URL}/stock_news`, { 
      params: { ...params, symbol: ticker.toUpperCase() }
    });

    return {
      articles: response.data.map(article => ({
        title: article.title,
        snippet: article.text || article.title,
        image_url: article.image || '',
        published_at: article.publishedDate,
        article_url: article.url,
        author: article.author || 'FMP',
        publisher: {
          name: 'Financial Modeling Prep',
          logo_url: '',
          favicon_url: '',
          homepage_url: 'https://financialmodelingprep.com'
        },
        tickers: [ticker.toUpperCase()],
        source: 'fmp'
      })),
      hasNextPage: response.data.length === 10
    };
  } catch (error) {
    console.error('Error fetching FMP ticker news:', error);
    return { articles: [], hasNextPage: false };
  }
};

const getFMPCompanyProfile = async (ticker) => {
  try {
    if (!FMP_API_TOKEN) {
      console.log('FMP_API_KEY not configured, skipping FMP company profile');
      return null;
    }

    const response = await axios.get(`${FMP_BASE_URL}/profile/${ticker.toUpperCase()}`, {
      params: { apikey: FMP_API_TOKEN }
    });

    if (response.data && response.data.length > 0) {
      const profile = response.data[0];
      return {
        companyName: profile.companyName,
        description: profile.description,
        industry: profile.industry,
        sector: profile.sector,
        ceo: profile.ceo,
        website: profile.website,
        employees: profile.fullTimeEmployees,
        marketCap: profile.mktCap,
        country: profile.country,
        city: profile.city,
        state: profile.state,
        address: profile.address,
        phone: profile.phone,
        exchange: profile.exchangeShortName,
        currency: profile.currency,
        isEtf: profile.isEtf,
        isActivelyTrading: profile.isActivelyTrading
      };
    }
    return null;
  } catch (error) {
    console.error('Error fetching FMP company profile:', error);
    return null;
  }
};

const getFMPKeyMetrics = async (ticker) => {
  try {
    if (!FMP_API_TOKEN) {
      console.log('FMP_API_KEY not configured, skipping FMP key metrics');
      return null;
    }

    const response = await axios.get(`${FMP_BASE_URL}/key-metrics/${ticker.toUpperCase()}`, {
      params: { 
        apikey: FMP_API_TOKEN,
        limit: 1
      }
    });

    if (response.data && response.data.length > 0) {
      const metrics = response.data[0];
      return {
        peRatio: metrics.peRatio,
        priceToBookRatio: metrics.priceToBookRatio,
        priceToSalesRatio: metrics.priceToSalesRatio,
        enterpriseValue: metrics.enterpriseValue,
        enterpriseValueOverEBITDA: metrics.enterpriseValueOverEBITDA,
        returnOnEquity: metrics.returnOnEquity,
        returnOnAssets: metrics.returnOnAssets,
        debtToEquity: metrics.debtToEquity,
        currentRatio: metrics.currentRatio,
        dividendYield: metrics.dividendYield,
        payoutRatio: metrics.payoutRatio,
        revenuePerShare: metrics.revenuePerShare,
        bookValuePerShare: metrics.bookValuePerShare,
        operatingCashFlowPerShare: metrics.operatingCashFlowPerShare,
        freeCashFlowPerShare: metrics.freeCashFlowPerShare,
        grossProfitMargin: metrics.grossProfitMargin,
        operatingProfitMargin: metrics.operatingProfitMargin,
        netProfitMargin: metrics.netProfitMargin,
        ebitda: metrics.ebitda,
        ebitdaMargin: metrics.ebitdaMargin,
        date: metrics.date
      };
    }
    return null;
  } catch (error) {
    console.error('Error fetching FMP key metrics:', error);
    return null;
  }
};

const getNews = async (page = 1) => {
  try {
    // Fetch from all three sources in parallel
    const [polygonData, marketauxData, fmpData] = await Promise.all([
      getPolygonNews(page),
      getMarketauxNews(page),
      getFMPNews(page)
    ]);

    // Create a Set to track unique titles
    const seenTitles = new Set();

    // Combine and sort articles by date, removing duplicates based on title
    const allArticles = [...polygonData.articles, ...marketauxData.articles, ...fmpData.articles]
      .sort((a, b) => new Date(b.published_at) - new Date(a.published_at))
      .filter(article => {
        // Normalize the title by removing whitespace and converting to lowercase
        const normalizedTitle = article.title.toLowerCase().trim();
        if (seenTitles.has(normalizedTitle)) {
          return false;
        }
        seenTitles.add(normalizedTitle);
        return true;
      })
      .slice(0, 10); // Take 10 most recent unique articles for scrollable modal

    return {
      data: allArticles,
      meta: {
        found: 100, // Approximate total
        returned: allArticles.length,
        limit: 10,
        page: page,
        hasNextPage: polygonData.hasNextPage || marketauxData.hasNextPage || fmpData.hasNextPage
      }
    };
  } catch (error) {
    console.error('Error fetching news:', error);
    throw error;
  }
};

module.exports = {
  getNews,
  getTickerNews,
  getFMPCompanyProfile,
  getFMPKeyMetrics
}; 