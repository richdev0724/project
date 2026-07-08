import { monthlyUpdateService } from '@/api/monthly-update.service'

class BackgroundScheduler {
  private checkInterval: NodeJS.Timeout | null = null
  private readonly CHECK_INTERVAL = 5 * 60 * 1000 // Check every 5 minutes
  private isRunning = false

  constructor() {
    this.start()
  }

  /**
   * Start the background scheduler
   */
  start(): void {
    if (this.isRunning) {
      return
    }

    this.isRunning = true

    // Check immediately
    this.checkForUpdate()

    // Set up interval
    this.checkInterval = setInterval(() => {
      this.checkForUpdate()
    }, this.CHECK_INTERVAL)
  }

  /**
   * Stop the background scheduler
   */
  stop(): void {
    if (!this.isRunning) {
      return
    }

    this.isRunning = false

    if (this.checkInterval) {
      clearInterval(this.checkInterval)
      this.checkInterval = null
    }
  }

  /**
   * Check if update should run
   */
  private async checkForUpdate(): Promise<void> {
    try {
      const status = monthlyUpdateService.getUpdateStatus()

      // Skip if already running
      if (status.isRunning) {
        return
      }

      // Check if update should run
      await monthlyUpdateService.checkAndRunUpdate()
    } catch (_error) {
      // Silent fail - will retry on next check
    }
  }

  /**
   * Get scheduler status
   */
  getStatus(): { isRunning: boolean; checkInterval: number } {
    return {
      isRunning: this.isRunning,
      checkInterval: this.CHECK_INTERVAL,
    }
  }

  /**
   * Force check for update
   */
  async forceCheck(): Promise<void> {
    await this.checkForUpdate()
  }
}

// Create singleton instance
export const backgroundScheduler = new BackgroundScheduler()

// Auto-start scheduler when module loads
if (typeof window !== 'undefined') {
  // Only run in browser environment
}
