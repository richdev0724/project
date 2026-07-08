import { companyProfileService } from './company-profile.service'
import { apiService } from './api.service'

interface UpdateStatus {
  isRunning: boolean
  lastUpdate: string | null
  nextUpdate: string | null
  totalTickers: number
  updatedTickers: number
  failedTickers: number
  currentTicker: string | null
  progress: number
  errors: string[]
}

interface UpdateResult {
  success: boolean
  ticker: string
  error?: string
  timestamp: string
}

class MonthlyUpdateService {
  private updateStatus: UpdateStatus = {
    isRunning: false,
    lastUpdate: null,
    nextUpdate: null,
    totalTickers: 0,
    updatedTickers: 0,
    failedTickers: 0,
    currentTicker: null,
    progress: 0,
    errors: [],
  }

  private readonly UPDATE_STATUS_KEY = 'company-profile-update-status'
  // Fallback common tickers for when API is unavailable
  private readonly FALLBACK_TICKERS = [
    // Major indices
    'SPY',
    'QQQ',
    'IWM',
    'VTI',
    'VOO',
    'ARKK',
    'TQQQ',
    'SQQQ',

    // Magnificent 7
    'AAPL',
    'MSFT',
    'GOOGL',
    'AMZN',
    'TSLA',
    'META',
    'NVDA',

    // Other major tech
    'NFLX',
    'AMD',
    'INTC',
    'CRM',
    'ADBE',
    'PYPL',
    'UBER',
    'LYFT',
    'SQ',

    // Financial
    'JPM',
    'BAC',
    'WFC',
    'GS',
    'MS',
    'C',
    'AXP',
    'V',
    'MA',

    // Healthcare
    'JNJ',
    'PFE',
    'UNH',
    'ABBV',
    'MRK',
    'TMO',
    'ABT',
    'DHR',

    // Consumer
    'KO',
    'PEP',
    'WMT',
    'PG',
    'JNJ',
    'HD',
    'MCD',
    'NKE',
    'SBUX',

    // Energy
    'XOM',
    'CVX',
    'COP',
    'EOG',
    'SLB',
    'KMI',
    'WMB',

    // Industrial
    'BA',
    'CAT',
    'GE',
    'HON',
    'MMM',
    'UPS',
    'FDX',
    'LMT',

    // Communication
    'VZ',
    'T',
    'CMCSA',
    'DIS',
    'NFLX',
    'GOOGL',
    'META',

    // Utilities
    'NEE',
    'SO',
    'DUK',
    'AEP',
    'EXC',
    'XEL',
    'WEC',
    'ES',

    // Real Estate
    'AMT',
    'PLD',
    'CCI',
    'EQIX',
    'PSA',
    'O',
    'SPG',
    'WELL',

    // Materials
    'LIN',
    'APD',
    'SHW',
    'ECL',
    'DD',
    'DOW',
    'PPG',
    'NEM',

    // Consumer Staples
    'PG',
    'KO',
    'PEP',
    'WMT',
    'COST',
    'CL',
    'KMB',
    'GIS',
  ]

  private allTickers: string[] = []

  constructor() {
    this.loadUpdateStatus()
    this.calculateNextUpdate()
  }

  /**
   * Get the last Friday of the current month
   */
  private getLastFridayOfMonth(date: Date = new Date()): Date {
    const year = date.getFullYear()
    const month = date.getMonth()

    // Get the last day of the month
    const lastDay = new Date(year, month + 1, 0)

    // Find the last Friday
    const lastFriday = new Date(lastDay)
    while (lastFriday.getDay() !== 5) {
      // 5 = Friday
      lastFriday.setDate(lastFriday.getDate() - 1)
    }

    return lastFriday
  }

  /**
   * Check if market is closed (after 4 PM ET)
   */
  private isMarketClosed(): boolean {
    const now = new Date()
    const etTime = new Date(
      now.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )
    const hour = etTime.getHours()

    // Market closes at 4 PM ET
    return hour >= 16
  }

  /**
   * Check if it's time for monthly update
   */
  private shouldRunUpdate(): boolean {
    const now = new Date()
    const lastFriday = this.getLastFridayOfMonth()

    // Check if it's the last Friday of the month
    const isLastFriday = now.toDateString() === lastFriday.toDateString()

    // Check if market is closed
    const marketClosed = this.isMarketClosed()

    // Check if we haven't updated this month
    const lastUpdate = this.updateStatus.lastUpdate
    if (lastUpdate) {
      const lastUpdateDate = new Date(lastUpdate)
      const sameMonth =
        lastUpdateDate.getMonth() === now.getMonth() &&
        lastUpdateDate.getFullYear() === now.getFullYear()
      if (sameMonth) {
        return false // Already updated this month
      }
    }

    return isLastFriday && marketClosed
  }

  /**
   * Calculate next update date
   */
  private calculateNextUpdate(): void {
    const now = new Date()
    const currentMonthLastFriday = this.getLastFridayOfMonth(now)

    // If we're past the last Friday of this month, get next month's last Friday
    let nextUpdate = currentMonthLastFriday
    if (now > currentMonthLastFriday) {
      nextUpdate = this.getLastFridayOfMonth(
        new Date(now.getFullYear(), now.getMonth() + 1, 1)
      )
    }

    this.updateStatus.nextUpdate = nextUpdate.toISOString()
    this.saveUpdateStatus()
  }

  /**
   * Get all US tickers from backend API
   */
  private async getAllUSTickers(): Promise<string[]> {
    try {
      const response = await apiService.get<{
        tickers: string[]
        count: number
      }>('/tickers/all')

      if (response && response.tickers) {
        this.allTickers = response.tickers
        return response.tickers
      } else {
        throw new Error('Invalid response from tickers API')
      }
    } catch (error) {
      this.allTickers = [...this.FALLBACK_TICKERS]
      return this.FALLBACK_TICKERS
    }
  }

  /**
   * Run monthly update for all tickers
   */
  async runMonthlyUpdate(): Promise<void> {
    if (this.updateStatus.isRunning) {
      return
    }

    if (!this.shouldRunUpdate()) {
      return
    }

    // Get all US tickers
    const tickersToUpdate = await this.getAllUSTickers()

    this.updateStatus.isRunning = true
    this.updateStatus.totalTickers = tickersToUpdate.length
    this.updateStatus.updatedTickers = 0
    this.updateStatus.failedTickers = 0
    this.updateStatus.errors = []
    this.updateStatus.progress = 0
    this.saveUpdateStatus()

    const results: UpdateResult[] = []

    try {
      for (let i = 0; i < tickersToUpdate.length; i++) {
        const ticker = tickersToUpdate[i]

        this.updateStatus.currentTicker = ticker
        this.updateStatus.progress = Math.round(
          (i / tickersToUpdate.length) * 100
        )
        this.saveUpdateStatus()

        try {
          await companyProfileService.getCompanyProfile(ticker)

          this.updateStatus.updatedTickers++
          results.push({
            success: true,
            ticker,
            timestamp: new Date().toISOString(),
          })

          // Add small delay to avoid overwhelming the API
          await new Promise((resolve) => setTimeout(resolve, 100))
        } catch (error) {
          const errorMessage = `Failed to update ${ticker}: ${error}`

          this.updateStatus.failedTickers++
          this.updateStatus.errors.push(errorMessage)
          results.push({
            success: false,
            ticker,
            error: errorMessage,
            timestamp: new Date().toISOString(),
          })
        }
      }

      // Update completed
      this.updateStatus.isRunning = false
      this.updateStatus.lastUpdate = new Date().toISOString()
      this.updateStatus.currentTicker = null
      this.updateStatus.progress = 100
      this.calculateNextUpdate()
    } catch (error) {
      this.updateStatus.isRunning = false
      this.updateStatus.errors.push(`Update process failed: ${error}`)
    }

    this.saveUpdateStatus()
  }

  /**
   * Force run update (bypasses time checks)
   */
  async forceUpdate(): Promise<void> {
    // Get all US tickers
    const tickersToUpdate = await this.getAllUSTickers()

    this.updateStatus.isRunning = true
    this.updateStatus.totalTickers = tickersToUpdate.length
    this.updateStatus.updatedTickers = 0
    this.updateStatus.failedTickers = 0
    this.updateStatus.errors = []
    this.updateStatus.progress = 0
    this.saveUpdateStatus()

    try {
      await this.runMonthlyUpdate()
    } finally {
      this.updateStatus.isRunning = false
      this.saveUpdateStatus()
    }
  }

  /**
   * Get update status
   */
  getUpdateStatus(): UpdateStatus {
    return { ...this.updateStatus }
  }

  /**
   * Get list of common tickers (fallback)
   */
  getCommonTickers(): string[] {
    return [...this.FALLBACK_TICKERS]
  }

  /**
   * Get all US tickers (if available)
   */
  getAllTickers(): string[] {
    return this.allTickers.length > 0
      ? [...this.allTickers]
      : [...this.FALLBACK_TICKERS]
  }

  /**
   * Add custom ticker to update list
   */
  addTickerToUpdateList(ticker: string): void {
    const upperTicker = ticker.toUpperCase()
    if (!this.allTickers.includes(upperTicker)) {
      this.allTickers.push(upperTicker)
    }
  }

  /**
   * Remove ticker from update list
   */
  removeTickerFromUpdateList(ticker: string): void {
    const upperTicker = ticker.toUpperCase()
    const index = this.allTickers.indexOf(upperTicker)
    if (index > -1) {
      this.allTickers.splice(index, 1)
    }
  }

  /**
   * Check if update should run (for external scheduling)
   */
  checkAndRunUpdate(): Promise<void> {
    return this.runMonthlyUpdate()
  }

  // Private helper methods

  private loadUpdateStatus(): void {
    try {
      const saved = localStorage.getItem(this.UPDATE_STATUS_KEY)
      if (saved) {
        const parsed = JSON.parse(saved)
        this.updateStatus = { ...this.updateStatus, ...parsed }
      }
    } catch (error) {
      // Silently fail
    }
  }

  private saveUpdateStatus(): void {
    try {
      localStorage.setItem(
        this.UPDATE_STATUS_KEY,
        JSON.stringify(this.updateStatus)
      )
    } catch (error) {
      // Silently fail
    }
  }
}

export const monthlyUpdateService = new MonthlyUpdateService()
