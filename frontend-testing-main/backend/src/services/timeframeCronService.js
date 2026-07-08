const cron = require('node-cron')
const { fetchAndStoreAllTimeframes } = require('../services/timeframeDataService')
const axios = require('axios')

/**
 * Cron job for daily timeframe updates after AH close
 * Runs every day at 8:00 PM EST (after AH trading closes)
 */
const dailyTimeframeUpdateJob = cron.schedule('0 20 * * 1-5', async () => {
  console.log('🕐 Starting daily timeframe update job...')
  
  try {
    // List of popular tickers to update daily
    const popularTickers = [
      'SPY', 'QQQ', 'IWM', 'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA',
      'NFLX', 'AMD', 'INTC', 'CRM', 'ADBE', 'PYPL', 'UBER', 'SQ', 'ROKU', 'ZM',
      'PTON', 'PELOTON', 'DOCU', 'SNOW', 'PLTR', 'RBLX', 'COIN', 'HOOD', 'SOFI', 'AFRM'
    ]
    
    console.log(`📊 Updating timeframes for ${popularTickers.length} tickers`)
    
    // Process tickers in batches to avoid overwhelming the API
    const batchSize = 5
    for (let i = 0; i < popularTickers.length; i += batchSize) {
      const batch = popularTickers.slice(i, i + batchSize)
      
      console.log(`🔄 Processing batch ${Math.floor(i/batchSize) + 1}/${Math.ceil(popularTickers.length/batchSize)}: ${batch.join(', ')}`)
      
      // Process batch in parallel
      const promises = batch.map(ticker => 
        fetchAndStoreAllTimeframes(ticker).catch(error => {
          console.error(`❌ Failed to update ${ticker}:`, error.message)
          return { ticker, error: error.message }
        })
      )
      
      await Promise.all(promises)
      
      // Add delay between batches to respect rate limits
      if (i + batchSize < popularTickers.length) {
        console.log('⏳ Waiting 30 seconds before next batch...')
        await new Promise(resolve => setTimeout(resolve, 30000))
      }
    }
    
    console.log('✅ Daily timeframe update job completed successfully')
    
  } catch (error) {
    console.error('❌ Daily timeframe update job failed:', error)
  }
}, {
  scheduled: false, // Don't start automatically
  timezone: 'America/New_York'
})

/**
 * Cron job for live chart updates (1D and 5D)
 * Runs every 5 minutes during market hours
 */
const liveChartUpdateJob = cron.schedule('*/5 9-16 * * 1-5', async () => {
  console.log('🔄 Starting live chart update job...')
  
  try {
    // Only update 1D and 5D timeframes during market hours
    const liveTimeframes = ['1D', '5D']
    const popularTickers = [
      'SPY', 'QQQ', 'IWM', 'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA'
    ]
    
    console.log(`📊 Updating live timeframes (${liveTimeframes.join(', ')}) for ${popularTickers.length} tickers`)
    
    // Process tickers in smaller batches for live updates
    const batchSize = 3
    for (let i = 0; i < popularTickers.length; i += batchSize) {
      const batch = popularTickers.slice(i, i + batchSize)
      
      console.log(`🔄 Processing live batch ${Math.floor(i/batchSize) + 1}/${Math.ceil(popularTickers.length/batchSize)}: ${batch.join(', ')}`)
      
      // Process each ticker's live timeframes
      const promises = batch.map(async ticker => {
        for (const timeframe of liveTimeframes) {
          try {
            await fetchAndStoreTimeframeData(ticker, timeframe)
            console.log(`✅ Updated ${ticker} ${timeframe}`)
          } catch (error) {
            console.error(`❌ Failed to update ${ticker} ${timeframe}:`, error.message)
          }
        }
      })
      
      await Promise.all(promises)
      
      // Shorter delay for live updates
      if (i + batchSize < popularTickers.length) {
        await new Promise(resolve => setTimeout(resolve, 10000))
      }
    }
    
    console.log('✅ Live chart update job completed')
    
  } catch (error) {
    console.error('❌ Live chart update job failed:', error)
  }
}, {
  scheduled: false, // Don't start automatically
  timezone: 'America/New_York'
})

/**
 * Cron job for pre-market data updates
 * Runs every 30 minutes during pre-market hours (4:00 AM - 9:30 AM EST)
 */
const preMarketUpdateJob = cron.schedule('0,30 4-9 * * 1-5', async () => {
  console.log('🌅 Starting pre-market update job...')
  
  try {
    // Only update 1D timeframe during pre-market
    const preMarketTickers = [
      'SPY', 'QQQ', 'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA'
    ]
    
    console.log(`📊 Updating pre-market data for ${preMarketTickers.length} tickers`)
    
    const promises = preMarketTickers.map(ticker => 
      fetchAndStoreTimeframeData(ticker, '1D').catch(error => {
        console.error(`❌ Failed to update pre-market ${ticker}:`, error.message)
        return { ticker, error: error.message }
      })
    )
    
    await Promise.all(promises)
    
    console.log('✅ Pre-market update job completed')
    
  } catch (error) {
    console.error('❌ Pre-market update job failed:', error)
  }
}, {
  scheduled: false, // Don't start automatically
  timezone: 'America/New_York'
})

/**
 * Cron job for after-hours data updates
 * Runs every 30 minutes during after-hours (4:00 PM - 8:00 PM EST)
 */
const afterHoursUpdateJob = cron.schedule('0,30 16-20 * * 1-5', async () => {
  console.log('🌙 Starting after-hours update job...')
  
  try {
    // Only update 1D timeframe during after-hours
    const afterHoursTickers = [
      'SPY', 'QQQ', 'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA'
    ]
    
    console.log(`📊 Updating after-hours data for ${afterHoursTickers.length} tickers`)
    
    const promises = afterHoursTickers.map(ticker => 
      fetchAndStoreTimeframeData(ticker, '1D').catch(error => {
        console.error(`❌ Failed to update after-hours ${ticker}:`, error.message)
        return { ticker, error: error.message }
      })
    )
    
    await Promise.all(promises)
    
    console.log('✅ After-hours update job completed')
    
  } catch (error) {
    console.error('❌ After-hours update job failed:', error)
  }
}, {
  scheduled: false, // Don't start automatically
  timezone: 'America/New_York'
})

/**
 * Start all cron jobs
 */
function startTimeframeCronJobs() {
  console.log('🚀 Starting timeframe cron jobs...')
  
  dailyTimeframeUpdateJob.start()
  liveChartUpdateJob.start()
  preMarketUpdateJob.start()
  afterHoursUpdateJob.start()
  
  console.log('✅ All timeframe cron jobs started')
}

/**
 * Stop all cron jobs
 */
function stopTimeframeCronJobs() {
  console.log('🛑 Stopping timeframe cron jobs...')
  
  dailyTimeframeUpdateJob.stop()
  liveChartUpdateJob.stop()
  preMarketUpdateJob.stop()
  afterHoursUpdateJob.stop()
  
  console.log('✅ All timeframe cron jobs stopped')
}

/**
 * Get cron job status
 */
function getCronJobStatus() {
  return {
    dailyUpdate: dailyTimeframeUpdateJob.running,
    liveChart: liveChartUpdateJob.running,
    preMarket: preMarketUpdateJob.running,
    afterHours: afterHoursUpdateJob.running
  }
}

module.exports = {
  startTimeframeCronJobs,
  stopTimeframeCronJobs,
  getCronJobStatus,
  dailyTimeframeUpdateJob,
  liveChartUpdateJob,
  preMarketUpdateJob,
  afterHoursUpdateJob
}
