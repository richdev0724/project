import { Layout } from '@/components/custom/layout'
import Seo from '@/components/seo'
import Events from './events'
import { Calendar } from '@/components/ui/calendar'
import { useEffect, useState } from 'react'
import WeekView from './week-view'
import DayView from './day-view'
import {
  earningsService,
  type EarningsByDateResponse,
} from '@/api/earnings.service'
import { type DateRange } from 'react-day-picker'
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react'

export default function Earnings() {
  const [weekEarnings, setWeekEarnings] = useState<EarningsByDateResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [weekOffset, setWeekOffset] = useState(0) // 0 = upcoming/current week, 1 = next week, -1 = previous week
  const [dateRange, setDateRange] = useState<DateRange | undefined>(undefined)
  const [highlightedDates, setHighlightedDates] = useState<Date[]>([])
  const [isNavigating, setIsNavigating] = useState(false) // Prevent rapid navigation
  const [earningsCache, setEarningsCache] = useState<
    Map<number, EarningsByDateResponse[]>
  >(new Map())
  const [selectedDate, setSelectedDate] = useState<Date | null>(null)
  const [selectedDayEarnings, setSelectedDayEarnings] =
    useState<EarningsByDateResponse | null>(null)
  const [viewMode, setViewMode] = useState<'week' | 'day'>('week')

  // Helper function to get the upcoming week's Monday (if weekend, get next week)
  const getUpcomingWeekMonday = (): Date => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const dayOfWeek = today.getDay()

    // If it's Saturday (6) or Sunday (0), get next week's Monday
    if (dayOfWeek === 0 || dayOfWeek === 6) {
      const daysUntilMonday = dayOfWeek === 0 ? 1 : 2 // Sunday = 1 day, Saturday = 2 days
      const nextMonday = new Date(today)
      nextMonday.setDate(today.getDate() + daysUntilMonday)
      return nextMonday
    }

    // Otherwise, get this week's Monday
    const diff = 1 - dayOfWeek // Days since Monday
    const monday = new Date(today)
    monday.setDate(today.getDate() + diff)
    return monday
  }

  // Helper function to get all trading days in a week (5 days)
  const getWeekTradingDays = (startDate: Date): Date[] => {
    const dates: Date[] = []
    const currentDate = new Date(startDate)

    for (let i = 0; i < 5; i++) {
      dates.push(new Date(currentDate))
      currentDate.setDate(currentDate.getDate() + 1)
    }

    return dates
  }

  // Fetch earnings for the current week offset
  const fetchWeekEarnings = async (offset: number) => {
    try {
      // Check cache first
      if (earningsCache.has(offset)) {
        const cachedData = earningsCache.get(offset)!
        setWeekEarnings(cachedData)
        setLoading(false)

        // Update calendar highlighting from cached data
        const dates = cachedData.map((d) => {
          const [year, month, day] = d.date.split('-').map(Number)
          return new Date(year, month - 1, day)
        })
        setHighlightedDates(dates)
        if (dates.length > 0) {
          setDateRange({
            from: dates[0],
            to: dates[dates.length - 1],
          })
        }
        return
      }

      setLoading(true)

      // Calculate the Monday of the target week (upcoming week if weekend)
      const upcomingMonday = getUpcomingWeekMonday()

      // Add offset weeks
      const targetMonday = new Date(upcomingMonday)
      targetMonday.setDate(targetMonday.getDate() + offset * 7)

      // Get all 5 trading days for this week
      const tradingDays = getWeekTradingDays(targetMonday)

      // Update calendar highlighting
      setHighlightedDates(tradingDays)
      if (tradingDays.length > 0) {
        setDateRange({
          from: tradingDays[0],
          to: tradingDays[tradingDays.length - 1],
        })
      }

      // Create placeholder data with dates immediately to show in UI
      const placeholderResults: EarningsByDateResponse[] = tradingDays.map(
        (tradingDate) => {
          const year = tradingDate.getFullYear()
          const month = String(tradingDate.getMonth() + 1).padStart(2, '0')
          const day = String(tradingDate.getDate()).padStart(2, '0')
          const dateStr = `${year}-${month}-${day}`
          return {
            date: dateStr,
            premarket: [],
            afterhours: [],
            success: false,
          }
        }
      )

      // Set placeholder data immediately so dates show while loading
      setWeekEarnings(placeholderResults)

      // Fetch earnings in batches: 2 days, 2 days, 1 day for optimal speed
      const results: EarningsByDateResponse[] = []

      // Helper function to fetch a single day
      const fetchDay = async (
        tradingDate: Date
      ): Promise<EarningsByDateResponse> => {
        const year = tradingDate.getFullYear()
        const month = String(tradingDate.getMonth() + 1).padStart(2, '0')
        const day = String(tradingDate.getDate()).padStart(2, '0')
        const dateStr = `${year}-${month}-${day}`

        try {
          const response = await earningsService.getEarningsByDate(dateStr)

          const result: EarningsByDateResponse = {
            date: dateStr,
            premarket: Array.isArray(response?.premarket)
              ? response.premarket
              : [],
            afterhours: Array.isArray(response?.afterhours)
              ? response.afterhours
              : [],
            success: true,
          }

          return result
        } catch (error) {
          return {
            date: dateStr,
            premarket: [],
            afterhours: [],
            success: false,
          }
        }
      }

      // Batch 1: Fetch days 0 and 1 in parallel
      const batch1 = await Promise.all([
        fetchDay(tradingDays[0]),
        fetchDay(tradingDays[1]),
      ])
      results.push(...batch1)

      // Batch 2: Fetch days 2 and 3 in parallel
      const batch2 = await Promise.all([
        fetchDay(tradingDays[2]),
        fetchDay(tradingDays[3]),
      ])
      results.push(...batch2)

      // Batch 3: Fetch day 4
      const batch3 = await fetchDay(tradingDays[4])
      results.push(batch3)

      // Cache the results
      setEarningsCache((prev) => new Map(prev).set(offset, results))

      setWeekEarnings(results)
    } catch (error) {
      // Error handling
    } finally {
      setLoading(false)
    }
  }

  // Fetch default week earnings on mount
  useEffect(() => {
    fetchWeekEarnings(weekOffset)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [weekOffset])

  // Navigation handlers
  const handlePreviousWeek = () => {
    // Don't allow going back before the upcoming/current week or if currently navigating
    if (weekOffset > 0 && !isNavigating) {
      setIsNavigating(true)
      setWeekOffset((prev) => prev - 1)
      // Reset navigation flag after a delay
      setTimeout(() => setIsNavigating(false), 500)
    }
  }

  const handleNextWeek = () => {
    if (!isNavigating) {
      setIsNavigating(true)
      setWeekOffset((prev) => prev + 1)
      // Reset navigation flag after a delay
      setTimeout(() => setIsNavigating(false), 500)
    }
  }

  const handleCurrentWeek = () => {
    if (weekOffset !== 0 && !isNavigating) {
      setIsNavigating(true)
      setWeekOffset(0)
      // Reset navigation flag after a delay
      setTimeout(() => setIsNavigating(false), 500)
    }
  }

  // Check if we can go back (only if offset > 0)
  const canGoPrevious = weekOffset > 0 && !isNavigating

  // Handle day click from week view
  const handleDayClick = (dateStr: string) => {
    const [year, month, day] = dateStr.split('-').map(Number)
    const clickedDate = new Date(year, month - 1, day)
    setSelectedDate(clickedDate)

    // Find the earnings data for this specific day
    const dayEarnings = weekEarnings.find((d) => d.date === dateStr)
    setSelectedDayEarnings(dayEarnings || null)

    // Switch to day view
    setViewMode('day')

    // Update calendar to show this specific day
    setDateRange({ from: clickedDate, to: clickedDate })
  }

  // Handle back to week view
  const handleBackToWeek = () => {
    setViewMode('week')
    setSelectedDate(null)
    setSelectedDayEarnings(null)

    // Restore week range in calendar
    if (highlightedDates.length > 0) {
      setDateRange({
        from: highlightedDates[0],
        to: highlightedDates[highlightedDates.length - 1],
      })
    }
  }

  // Format date range for display
  const getDateRangeDisplay = () => {
    if (!dateRange?.from || !dateRange?.to) return ''

    const formatDate = (date: Date) => {
      const month = date
        .toLocaleDateString('en-US', { month: 'short' })
        .toUpperCase()
      const day = date.getDate()
      return `${month} ${day}`
    }

    return `${formatDate(dateRange.from)} - ${formatDate(dateRange.to)}`
  }

  // Get current day of week and date for the toggle
  const getCurrentDayDisplay = () => {
    if (highlightedDates.length === 0) {
      const today = new Date()
      const dayLetter = today
        .toLocaleDateString('en-US', { weekday: 'short' })
        .charAt(0)
        .toUpperCase()
      const day = today.getDate()
      return { dayLetter, day }
    }

    // Use the first day of the current week range
    const firstDay = highlightedDates[0]
    const dayLetter = firstDay
      .toLocaleDateString('en-US', { weekday: 'short' })
      .charAt(0)
      .toUpperCase()
    const day = firstDay.getDate()
    return { dayLetter, day }
  }

  const { dayLetter, day } = getCurrentDayDisplay()

  return (
    <Layout>
      <Seo title='Calendar' />
      <Layout.Body className='flex h-full max-h-[calc(100vh_-_50px)] !p-0'>
        <div className='flex w-[30%] max-w-[450px] flex-col border-r-2 border-[#1F2025]'>
          <div className='border-b-2 border-[#1F2025] p-2'>
            <div className='flex flex-col bg-[#181F24] p-4'>
              {/* Week Navigation Toggle - Smaller and on calendar */}
              <div className='mb-3 flex flex-row items-center justify-between py-2'>
                <div className='text-left'>
                  <p className='text-[8px] font-medium tracking-wider text-gray-400'>
                    COMPANY EARNINGS
                  </p>
                  <p className='text-[10px] font-medium transition-all duration-300'>
                    {getDateRangeDisplay()}
                  </p>
                </div>

                <div className='flex items-center gap-2'>
                  <button
                    onClick={handlePreviousWeek}
                    disabled={!canGoPrevious || isNavigating}
                    className='flex h-6 w-6 items-center justify-center rounded-full bg-[#2A2D31] transition-colors hover:bg-[#3A3D41] disabled:cursor-not-allowed disabled:opacity-30'
                    aria-label='Previous week'
                  >
                    <IconChevronLeft size={12} />
                  </button>

                  <button
                    onClick={handleCurrentWeek}
                    disabled={weekOffset === 0 || isNavigating}
                    className='flex h-8 w-8 flex-col items-center justify-center rounded-full bg-[#2A2D31] transition-colors hover:bg-[#3A3D41] disabled:cursor-not-allowed disabled:opacity-50'
                    aria-label='Current week'
                  >
                    <span className='text-[8px] font-light text-gray-400'>
                      {dayLetter}
                    </span>
                    <span className='text-sm font-medium'>{day}</span>
                  </button>

                  <button
                    onClick={handleNextWeek}
                    disabled={isNavigating}
                    className='flex h-6 w-6 items-center justify-center rounded-full bg-[#2A2D31] transition-colors hover:bg-[#3A3D41] disabled:cursor-not-allowed disabled:opacity-30'
                    aria-label='Next week'
                  >
                    <IconChevronRight size={12} />
                  </button>
                </div>
              </div>

              <Calendar
                mode='range'
                selected={dateRange}
                onSelect={() => {}} // Read-only, controlled by week navigation
                defaultMonth={
                  highlightedDates.length > 0 ? highlightedDates[0] : undefined
                }
                month={
                  highlightedDates.length > 0 ? highlightedDates[0] : undefined
                }
                className='w-full p-0 transition-all duration-300'
                classNames={{
                  months: 'flex flex-col sm:flex-row w-full',
                  month: 'flex flex-col w-full',
                  table: 'w-full border-collapse',
                  caption: 'hidden', // Hide the default caption with month/year and arrows
                  nav: 'hidden', // Hide the navigation arrows
                  head_row: 'flex w-full',
                  head_cell:
                    'text-muted-foreground flex-1 font-normal text-[0.8rem] text-center',
                  row: 'flex w-full mt-2',
                  day_range_start:
                    'day-range-start  border-2 border-orange-500 border-r-0 aria-selected:bg-[#292B34] aria-selected:text-white aria-selected:font-bold relative z-[1]',
                  day_range_end:
                    'day-range-end  border-2 border-orange-500 border-l-0 aria-selected:bg-[#292B34] aria-selected:text-white aria-selected:font-bold relative z-[1]',
                  day_range_middle:
                    'border-y-2 border-orange-500 aria-selected:bg-[#292B34] aria-selected:text-white aria-selected:font-semibold relative z-[1]',
                  day_today:
                    'bg-[#193A4F] text-white font-bold aria-selected:bg-[#193A4F] aria-selected:text-white relative z-10 ',
                  cell: 'relative p-0 text-center text-sm flex-1 focus-within:relative focus-within:z-20',
                  day: 'h-8 w-full p-0 font-normal aria-selected:opacity-100',
                }}
              />
            </div>
          </div>
          <div className='max-h-[63%] overflow-y-auto p-4 pb-6'>
            <h4 className='mb-3 text-base'>Upcoming Events</h4>
            <Events selectedDate={selectedDate} />
          </div>
        </div>
        <div className='relative w-[70%] flex-1 overflow-hidden'>
          <div
            className={`absolute inset-0 transition-all duration-500 ease-in-out ${
              viewMode === 'week'
                ? 'translate-x-0 opacity-100'
                : 'pointer-events-none -translate-x-full opacity-0'
            }`}
          >
            <WeekView
              earningsData={weekEarnings}
              loading={loading}
              onDayClick={handleDayClick}
            />
          </div>
          <div
            className={`absolute inset-0 transition-all duration-500 ease-in-out ${
              viewMode === 'day'
                ? 'translate-x-0 opacity-100'
                : 'pointer-events-none translate-x-full opacity-0'
            }`}
          >
            <DayView
              date={selectedDate || undefined}
              earningsData={selectedDayEarnings}
              onBack={handleBackToWeek}
            />
          </div>
        </div>
      </Layout.Body>
    </Layout>
  )
}
