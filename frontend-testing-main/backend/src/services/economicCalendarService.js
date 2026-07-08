const axios = require('axios');

class EconomicCalendarService {
  constructor() {
    this.baseURL = 'https://api.unusualwhales.com/api';
    this.apiKey = process.env.UW_API;
  }

  /**
   * Get economic calendar events
   * @param {string} startDate - Start date in YYYY-MM-DD format (optional)
   * @param {string} endDate - End date in YYYY-MM-DD format (optional)
   * @param {number} days - Number of days ahead to fetch (default 7)
   */
  async getEconomicCalendar(startDate, endDate, days = 7) {
    try {
      if (!this.apiKey) {
        console.warn('⚠️ UW_API_KEY not configured');
        return {
          success: false,
          data: [],
          error: 'API key not configured'
        };
      }

      const params = {};
      
      if (startDate) {
        params.start_date = startDate;
      }
      
      if (endDate) {
        params.end_date = endDate;
      } else if (!startDate) {
        // If no dates provided, get upcoming events for next N days
        const today = new Date();
        const futureDate = new Date(today);
        futureDate.setDate(today.getDate() + days);
        
        params.start_date = today.toISOString().split('T')[0];
        params.end_date = futureDate.toISOString().split('T')[0];
      }

      console.log('📅 Fetching economic calendar:', params);

      const response = await axios.get(`${this.baseURL}/market/economic-calendar`, {
        headers: {
          'Authorization': `Bearer ${this.apiKey}`,
          'Accept': 'application/json'
        },
        params
      });

      if (!response.data || !response.data.data) {
        console.warn('No economic calendar data received');
        return {
          success: true,
          data: []
        };
      }

      // Group events by date
      const groupedByDate = this.groupEventsByDate(response.data.data);

      return {
        success: true,
        data: groupedByDate
      };
    } catch (error) {
      console.error('Error fetching economic calendar:', error.message);
      
      if (error.response) {
        console.error('Response status:', error.response.status);
        console.error('Response data:', error.response.data);
      }
      
      return {
        success: false,
        data: [],
        error: error.message
      };
    }
  }

  /**
   * Group events by date and format them for the frontend
   * @param {Array} events - Raw events from API
   */
  groupEventsByDate(events) {
    const grouped = {};

    events.forEach(event => {
      const eventDate = new Date(event.time);
      const dateKey = eventDate.toISOString().split('T')[0];
      
      // Format date as "Wednesday, February 5th"
      const formattedDate = this.formatDateString(eventDate);
      
      // Format time as HH:MM
      const hours = String(eventDate.getHours()).padStart(2, '0');
      const minutes = String(eventDate.getMinutes()).padStart(2, '0');
      const formattedTime = `${hours}:${minutes}`;
      
      // Determine priority based on event importance (you can adjust this logic)
      const priority = this.determinePriority(event);
      
      // Create title from event name and reported period
      const title = event.reported_period 
        ? `${event.event} (${event.reported_period})`
        : event.event;

      if (!grouped[dateKey]) {
        grouped[dateKey] = {
          date: formattedDate,
          dateKey: dateKey,
          events: []
        };
      }

      grouped[dateKey].events.push({
        time: formattedTime,
        title: title,
        priority: priority,
        forecast: event.forecast,
        prev: event.prev,
        type: event.type,
        rawTime: event.time
      });
    });

    // Convert to array and sort by date
    const result = Object.values(grouped).sort((a, b) => 
      a.dateKey.localeCompare(b.dateKey)
    );

    // Sort events within each day by time
    result.forEach(day => {
      day.events.sort((a, b) => a.rawTime.localeCompare(b.rawTime));
    });

    return result;
  }

  /**
   * Format date as "Wednesday, February 5th"
   * @param {Date} date 
   */
  formatDateString(date) {
    const weekday = date.toLocaleDateString('en-US', { weekday: 'long' });
    const month = date.toLocaleDateString('en-US', { month: 'long' });
    const day = date.getDate();
    
    // Add ordinal suffix (st, nd, rd, th)
    const suffix = this.getOrdinalSuffix(day);
    
    return `${weekday}, ${month} ${day}${suffix}`;
  }

  /**
   * Get ordinal suffix for day
   * @param {number} day 
   */
  getOrdinalSuffix(day) {
    if (day > 3 && day < 21) return 'th';
    switch (day % 10) {
      case 1: return 'st';
      case 2: return 'nd';
      case 3: return 'rd';
      default: return 'th';
    }
  }

  /**
   * Determine priority of event (LOW, MEDIUM, HIGH)
   * You can customize this logic based on event types or names
   * @param {Object} event 
   */
  determinePriority(event) {
    const highPriorityKeywords = [
      'gdp', 'employment', 'jobless', 'nonfarm', 'cpi', 'pce',
      'fomc', 'interest rate', 'fed chair', 'unemployment'
    ];
    
    const mediumPriorityKeywords = [
      'speaks', 'housing', 'retail', 'manufacturing', 'durable goods'
    ];

    const eventLower = event.event.toLowerCase();
    
    // Check for high priority keywords
    if (highPriorityKeywords.some(keyword => eventLower.includes(keyword))) {
      return 'HIGH';
    }
    
    // Check for medium priority keywords
    if (mediumPriorityKeywords.some(keyword => eventLower.includes(keyword))) {
      return 'MEDIUM';
    }
    
    // Default to LOW
    return 'LOW';
  }
}

module.exports = new EconomicCalendarService();

