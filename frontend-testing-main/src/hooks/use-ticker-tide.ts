import { tickerTideService } from '@/api/ticker-tide.service'
import { useState, useEffect, useMemo } from 'react'

// Helper function to get last N weekdays including today
function getLastNWeekdays(n: number): string[] {
  const dates: string[] = []
  const today = new Date()

  // Convert to EST/EDT timezone
  const estDate = new Date(
    today.toLocaleString('en-US', { timeZone: 'America/New_York' })
  )

  const currentDate = new Date(estDate)

  // Go back to find N weekdays including today (get extra to account for missing data)
  const maxDaysToCheck = n * 3 // Check up to 3x the number of days needed
  let daysChecked = 0

  while (dates.length < maxDaysToCheck && daysChecked < 30) {
    const dayOfWeek = currentDate.getDay()

    // Skip weekends (0 = Sunday, 6 = Saturday)
    if (dayOfWeek !== 0 && dayOfWeek !== 6) {
      dates.push(currentDate.toISOString().split('T')[0])
    }

    currentDate.setDate(currentDate.getDate() - 1)
    daysChecked++
  }

  // Keep dates in reverse chronological order (newest to oldest)
  // This way we'll fetch the most recent dates first
  return dates
}

interface TickerTideChartData {
  date: string
  displayDate: string
  net_call_premium: number
  net_put_premium: number
  net_delta: number
  call_volume: number
  put_volume: number
  tape_time: string
}

export function useTickerTide(ticker: string, days: number = 5) {
  const [chartData, setChartData] = useState<TickerTideChartData[]>([])
  const [isLoadingData, setIsLoadingData] = useState(false)

  // Get the last N weekdays
  const weekdays = getLastNWeekdays(days)

  // Memoize weekdays string to avoid complex expression in dependency array
  const weekdaysKey = useMemo(() => weekdays.join(','), [weekdays.join(',')])

  // Fetch data for all weekdays
  useEffect(() => {
    if (!ticker || weekdays.length === 0) return

    const fetchAllData = async () => {
      setIsLoadingData(true)
      try {
        const validData: TickerTideChartData[] = []

        // Helper function to add delay between API calls
        const delay = (ms: number) =>
          new Promise((resolve) => setTimeout(resolve, ms))

        // Try to get N days of data, fetching more dates if needed
        for (let i = 0; i < weekdays.length; i++) {
          const date = weekdays[i]

          // Stop if we already have enough data
          if (validData.length >= days) break

          try {
            // Add delay between requests to avoid rate limiting (10ms between calls)
            if (i > 0) {
              await delay(10)
            }

            const response = await tickerTideService.getTickerTide(ticker, date)

            if (response.data && response.data.length > 0) {
              // Get the last response for this date (most recent tape_time)
              const lastEntry = response.data[response.data.length - 1]

              // Format date for display (MM/DD)
              const dateObj = new Date(date + 'T12:00:00Z')
              const displayDate = `${dateObj.getMonth() + 1}/${dateObj.getDate()}`

              validData.push({
                date,
                displayDate,
                net_call_premium: lastEntry.call_premium || 0, // Keep actual sign
                net_put_premium: lastEntry.put_premium || 0, // Keep actual sign (usually negative)
                net_delta: parseFloat(lastEntry.net_delta as any) || 0,
                call_volume: lastEntry.call_volume || 0,
                put_volume: lastEntry.put_volume || 0,
                tape_time: lastEntry.original_timestamp || lastEntry.date,
              } as TickerTideChartData)
            }
          } catch (error) {
            // Continue to next date if error occurs
            continue
          }
        }

        // Reverse for display so most recent date is on the right
        setChartData(validData.reverse())
      } catch (error) {
        // Error handling
        setChartData([])
      } finally {
        setIsLoadingData(false)
      }
    }

    fetchAllData()
  }, [ticker, days, weekdaysKey])

  return {
    data: chartData,
    isLoading: isLoadingData,
    weekdays,
  }
}
