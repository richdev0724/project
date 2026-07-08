const { createClient } = require('@clickhouse/client');
const EventEmitter = require('events');
require('dotenv').config();

// Create ClickHouse client for stocks database
const clickhouse = createClient({
  url: process.env.CLICKHOUSE_URL || 'http://localhost:8123',
  username: process.env.CLICKHOUSE_USER || 'default',
  password: process.env.CLICKHOUSE_PASSWORD || '',
  database: 'stocks'
});

class WhaleTradesStreamer extends EventEmitter {
  constructor() {
    super();
    this.isStreaming = false;
    this.activeConnections = new Set();
    this.pollingInterval = null;
    this.lastProcessedTimestamp = null;
    this.processedTradeIds = new Set(); // Keep track of processed trades to avoid duplicates
  }

  /**
   * Start streaming whale trades using high-frequency polling for constant stream
   */
  async startStreaming() {
    if (this.isStreaming) {
      console.log('Whale trades streaming already active');
      return;
    }

    try {
      console.log('🚀 Starting high-frequency whale trades streaming...');
      this.isStreaming = true;

      // Initialize with the latest timestamp
      await this.initializeLastTimestamp();

      // Start high-frequency polling for constant stream (every 100ms)
      this.pollingInterval = setInterval(async () => {
        try {
          await this.checkForNewTrades();
        } catch (error) {
          console.error('Error checking for new trades:', error);
        }
      }, 100); // Poll every 100ms for high-frequency updates

      console.log('✅ High-frequency whale trades streaming started successfully');

    } catch (error) {
      console.error('❌ Failed to start whale trades streaming:', error);
      this.isStreaming = false;
      throw error;
    }
  }

  /**
   * Initialize the last processed timestamp
   */
  async initializeLastTimestamp() {
    try {
      const result = await clickhouse.query({
        query: `
          SELECT MAX(utc_timestamp) as latest_timestamp
          FROM trades 
          WHERE premium >= 10000
        `,
        format: 'JSONEachRow'
      });
      
      const data = await result.json();
      this.lastProcessedTimestamp = data[0]?.latest_timestamp || new Date().toISOString();
      
      console.log('📅 Initialized streaming from timestamp:', this.lastProcessedTimestamp);
    } catch (error) {
      console.error('Error initializing timestamp:', error);
      // Fallback to current time
      this.lastProcessedTimestamp = new Date().toISOString();
    }
  }

  /**
   * Check for new whale trades since the last processed timestamp
   */
  async checkForNewTrades() {
    if (!this.isStreaming) {
      return;
    }

    try {
      const query = `
        SELECT 
          symbol as ticker,
          price,
          size,
          premium,
          utc_timestamp,
          eastern_time as timestamp,
          session,
          trade_id
        FROM trades 
        WHERE premium >= 10000 
          AND utc_timestamp > '${this.lastProcessedTimestamp}'
        ORDER BY utc_timestamp ASC
        LIMIT 100
      `;

      const result = await clickhouse.query({
        query,
        format: 'JSONEachRow'
      });
      
      const newTrades = await result.json();

      // Process multiple trades but emit them one by one for smooth table updates
      if (newTrades.length > 0) {
        console.log(`📊 Fetched ${newTrades.length} new whale trades, streaming individually...`);
        
        let emissionDelay = 0;
        let processedCount = 0;
        
        for (const trade of newTrades) {
          // Skip if we've already processed this trade (prevent duplicates)
          if (this.processedTradeIds.has(trade.trade_id)) {
            continue;
          }

          // Schedule each trade to emit individually with proper delay
          setTimeout(() => {
            if (!this.isStreaming) return; // Stop if streaming was stopped
            
            const formattedTrade = this.formatTradeData(trade);
            console.log(`📈 [${processedCount + 1}/${newTrades.length}] ${formattedTrade.symbol} $${formattedTrade.total_premium}`);
            
            // Emit each trade individually so frontend table updates one by one
            this.emit('newWhaleTrade', formattedTrade);
          }, emissionDelay);
          
          // Update tracking immediately
          this.lastProcessedTimestamp = trade.utc_timestamp;
          this.processedTradeIds.add(trade.trade_id);
          processedCount++;
          
          // Space out emissions every 50ms for smooth individual updates
          emissionDelay += 50;
          
          // Clean up old trade IDs to prevent memory growth
          if (this.processedTradeIds.size > 10000) {
            const idsArray = Array.from(this.processedTradeIds);
            this.processedTradeIds = new Set(idsArray.slice(-5000)); // Keep last 5000
          }
        }
        
        if (processedCount > 0) {
          console.log(`⚡ Streaming ${processedCount} trades individually over ${emissionDelay}ms (50ms per trade)`);
        }
      }

    } catch (error) {
      console.error('Error checking for new trades:', error);
      this.emit('error', error);
    }
  }

  /**
   * Stop the streaming
   */
  async stopStreaming() {
    if (!this.isStreaming) {
      return;
    }

    console.log('🛑 Stopping whale trades streaming...');
    this.isStreaming = false;

    if (this.pollingInterval) {
      clearInterval(this.pollingInterval);
      this.pollingInterval = null;
    }

    // Reset tracking
    this.lastProcessedTimestamp = null;
    this.processedTradeIds.clear();

    this.emit('streamStopped');
    console.log('✅ Whale trades streaming stopped');
  }

  /**
   * Test database connection
   */
  async testConnection() {
    try {
      const result = await clickhouse.query({
        query: 'SELECT COUNT(*) as total FROM trades WHERE premium >= 10000 LIMIT 1',
        format: 'JSONEachRow'
      });
      const data = await result.json();
      console.log('✅ Database connection successful. Whale trades available:', data[0].total);
      return true;
    } catch (error) {
      console.error('❌ Database connection failed:', error);
      return false;
    }
  }

  /**
   * Format trade data for frontend consumption
   */
  formatTradeData(trade) {
    return {
      id: trade.trade_id || `${Date.now()}-${Math.random()}`,
      symbol: trade.ticker,
      price: parseFloat(trade.price).toFixed(2),
      size: trade.size,
      total_premium: parseFloat(trade.premium).toFixed(2),
      market_session: trade.session,
      trade_date: new Date(trade.timestamp).toLocaleString('en-US', {
        timeZone: 'America/New_York',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      }),
      timestamp: trade.timestamp
    };
  }

  /**
   * Add a client connection
   */
  addConnection(connectionId) {
    this.activeConnections.add(connectionId);
    console.log(`➕ Added whale trades connection: ${connectionId} (${this.activeConnections.size} total)`);

    // Start streaming if this is the first connection
    if (this.activeConnections.size === 1 && !this.isStreaming) {
      this.startStreaming().catch(error => {
        console.error('Failed to start streaming for new connection:', error);
      });
    }
  }

  /**
   * Remove a client connection
   */
  removeConnection(connectionId) {
    this.activeConnections.delete(connectionId);
    console.log(`➖ Removed whale trades connection: ${connectionId} (${this.activeConnections.size} total)`);

    // Stop streaming if no more connections
    if (this.activeConnections.size === 0 && this.isStreaming) {
      this.stopStreaming().catch(error => {
        console.error('Failed to stop streaming:', error);
      });
    }
  }

  /**
   * Get current status
   */
  getStatus() {
    return {
      isStreaming: this.isStreaming,
      activeConnections: this.activeConnections.size
    };
  }
}

// Create singleton instance
const whaleTradesStreamer = new WhaleTradesStreamer();

module.exports = {
  whaleTradesStreamer
};
