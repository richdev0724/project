const express = require('express');
const router = express.Router();
const economicCalendarService = require('../services/economicCalendarService');

// Get economic calendar events - no parameters required
router.get('/', async (req, res) => {
  try {
    console.log('📅 Economic calendar route hit');
    
    const calendar = await economicCalendarService.getEconomicCalendar();
    
    console.log('✅ Economic calendar fetched:', calendar.data?.length || 0, 'days');
    
    res.json(calendar);
  } catch (error) {
    console.error('❌ Error in economic calendar route:', error);
    res.status(500).json({ 
      error: 'Failed to fetch economic calendar',
      message: error.message 
    });
  }
});

module.exports = router;

