const express = require('express');
const router = express.Router();
const newsService = require('../services/newsService');
const unusualWhalesNewsService = require('../services/unusualWhalesNewsService');

router.get('/', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    const news = await newsService.getNews(page);
    res.json(news);
  } catch (error) {
    console.error('Error in news route:', error);
    res.status(500).json({ error: 'Failed to fetch news' });
  }
});

router.get('/headlines', async (req, res) => {
  try {
    const news = await unusualWhalesNewsService.getUnusualWhalesNews();
    res.json(news);
  } catch (error) {
    console.error('Error in headlines route:', error);
    res.status(500).json({ error: 'Failed to fetch headlines' });
  }
});

router.get('/ticker/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    const page = parseInt(req.query.page) || 1;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const news = await newsService.getTickerNews(ticker, page);
    res.json(news);
  } catch (error) {
    console.error('Error in ticker news route:', error);
    res.status(500).json({ error: 'Failed to fetch ticker news' });
  }
});

router.get('/company-profile/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const profile = await newsService.getFMPCompanyProfile(ticker);
    res.json(profile);
  } catch (error) {
    console.error('Error in company profile route:', error);
    res.status(500).json({ error: 'Failed to fetch company profile' });
  }
});

router.get('/key-metrics/:ticker', async (req, res) => {
  try {
    const { ticker } = req.params;
    
    if (!ticker) {
      return res.status(400).json({ error: 'Ticker parameter is required' });
    }

    const metrics = await newsService.getFMPKeyMetrics(ticker);
    res.json(metrics);
  } catch (error) {
    console.error('Error in key metrics route:', error);
    res.status(500).json({ error: 'Failed to fetch key metrics' });
  }
});

module.exports = router; 