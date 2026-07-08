import { Table, TableBody, TableCell, TableRow } from '@/components/ui/table'
import {
  IconBell,
  IconCircleFilled,
  IconChevronLeft,
  IconChevronRight,
} from '@tabler/icons-react'
import PriorityBadge from './priority-badge'
import { useEffect, useState } from 'react'
import {
  economicCalendarService,
  type EconomicCalendarDay,
} from '@/api/economic-calendar.service'

/**
 * UpcomingEvents is a React component that renders a list of upcoming events,
 * displaying event details such as time, description, and priority.
 * It organizes events by date, providing visual separators and headers for each day.
 * A button at the bottom allows users to expand and show more events.
 */

const UpcomingEvents = () => {
  const [economicCalendarData, setEconomicCalendarData] = useState<
    EconomicCalendarDay[]
  >([])
  const [loading, setLoading] = useState(true)
  const [weekOffset, setWeekOffset] = useState(0) // 0 = current week, 1 = next week, -1 = previous week
  const [isNavigating, setIsNavigating] = useState(false)
  const [eventsCache, setEventsCache] = useState<
    Map<number, EconomicCalendarDay[]>
  >(new Map())

  // Helper function to get the upcoming week's Monday (if weekend, get next week)
  const getUpcomingWeekMonday = (): Date => {
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const dayOfWeek = today.getDay()

    // If it's Saturday (6) or Sunday (0), get next week's Monday
    if (dayOfWeek === 0 || dayOfWeek === 6) {
      const daysUntilMonday = dayOfWeek === 0 ? 1 : 2
      const nextMonday = new Date(today)
      nextMonday.setDate(today.getDate() + daysUntilMonday)
      return nextMonday
    }

    // Otherwise, get this week's Monday
    const diff = 1 - dayOfWeek
    const monday = new Date(today)
    monday.setDate(today.getDate() + diff)
    return monday
  }

  // Fetch economic calendar events for the current week offset
  const fetchWeekEvents = async (offset: number) => {
    try {
      // Check cache first
      if (eventsCache.has(offset)) {
        const cachedData = eventsCache.get(offset)!
        setEconomicCalendarData(cachedData)
        setLoading(false)
        return
      }

      setLoading(true)

      const response = await economicCalendarService.getEconomicCalendar()

      if (response.success && response.data) {
        // Filter events based on week offset
        const upcomingMonday = getUpcomingWeekMonday()
        const targetMonday = new Date(upcomingMonday)
        targetMonday.setDate(targetMonday.getDate() + offset * 7)

        const targetFriday = new Date(targetMonday)
        targetFriday.setDate(targetMonday.getDate() + 4)

        // Get today's date for filtering past events
        const today = new Date()
        today.setHours(0, 0, 0, 0)

        // Filter events for the target week, excluding past events
        const filteredEvents = response.data.filter((dayData) => {
          const [year, month, day] = dayData.dateKey.split('-').map(Number)
          const eventDate = new Date(year, month - 1, day)
          return (
            eventDate >= targetMonday &&
            eventDate <= targetFriday &&
            eventDate >= today
          )
        })

        // Cache the results
        setEventsCache((prev) => new Map(prev).set(offset, filteredEvents))
        setEconomicCalendarData(filteredEvents)
      }
    } catch (error) {
      // Failed to fetch economic calendar
    } finally {
      setLoading(false)
    }
  }

  // Fetch default week events on mount
  useEffect(() => {
    fetchWeekEvents(weekOffset)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [weekOffset])

  // Navigation handlers
  const handlePreviousWeek = () => {
    if (weekOffset > 0 && !isNavigating) {
      setIsNavigating(true)
      setWeekOffset((prev) => prev - 1)
      setTimeout(() => setIsNavigating(false), 500)
    }
  }

  const handleNextWeek = () => {
    if (!isNavigating) {
      setIsNavigating(true)
      setWeekOffset((prev) => prev + 1)
      setTimeout(() => setIsNavigating(false), 500)
    }
  }

  // const handleCurrentWeek = () => {
  //   if (weekOffset !== 0 && !isNavigating) {
  //     setIsNavigating(true)
  //     setWeekOffset(0)
  //     setTimeout(() => setIsNavigating(false), 500)
  //   }
  // }

  const canGoPrevious = weekOffset > 0 && !isNavigating

  // Get current day display for the toggle button
  // const getCurrentDayDisplay = () => {
  //   const today = new Date()
  //   const dayLetter = today
  //     .toLocaleDateString('en-US', { weekday: 'short' })
  //     .charAt(0)
  //     .toUpperCase()
  //   const day = today.getDate()
  //   return { dayLetter, day }
  // }

  // Removed unused destructuring

  return (
    <div className='mt-1 h-[350px] w-full pl-3 pr-1'>
      <div className='flex items-center justify-between'>
        <h1 className='text-start text-base'>
          <span className='font-normal'>Upcoming Events</span>
        </h1>

        {/* Week Navigation Controls */}
        <div className='flex items-center gap-2'>
          <button
            onClick={handlePreviousWeek}
            disabled={!canGoPrevious || isNavigating}
            className='flex h-5 w-5 items-center justify-center rounded-full bg-[#2A2D31] transition-colors hover:bg-[#3A3D41] disabled:cursor-not-allowed disabled:opacity-30'
            aria-label='Previous week'
          >
            <IconChevronLeft size={10} />
          </button>

          <button
            onClick={handleNextWeek}
            disabled={isNavigating}
            className='flex h-5 w-5 items-center justify-center rounded-full bg-[#2A2D31] transition-colors hover:bg-[#3A3D41] disabled:cursor-not-allowed disabled:opacity-30'
            aria-label='Next week'
          >
            <IconChevronRight size={10} />
          </button>
        </div>
      </div>

      <div className='mt-[20px] max-h-[350px] overflow-y-auto'>
        {loading ? (
          <div className='flex items-center justify-center py-8'>
            <div className='text-sm text-gray-400'>Loading events...</div>
          </div>
        ) : economicCalendarData.length === 0 ? (
          <div className='flex items-center justify-center py-8'>
            <div className='text-sm text-gray-400'>No events this week</div>
          </div>
        ) : (
          economicCalendarData.map((dayData, dayIndex) => (
            <div key={dayIndex}>
              {/**
               * Header for each day
               */}
              <div
                className={`flex items-center justify-start pr-4 ${dayIndex > 0 ? 'pt-6' : ''}`}
              >
                <span className='whitespace-nowrap text-[11px] italic'>
                  {dayData.date}
                </span>
                <hr className='ml-3 h-0.5 w-full border-t border-[#FFFFFF]' />
                <IconCircleFilled size={12} />
              </div>

              {/**
               * Events for this day
               */}
              {dayData.events.length > 0 ? (
                <Table className='w-full table-fixed'>
                  <TableBody>
                    {dayData.events.map((event, eventIndex) => (
                      <TableRow key={eventIndex} className='h-8 border-none'>
                        <TableCell className='flex w-[75%] items-center gap-1.5 px-1'>
                          <div className='flex-shrink-0'>
                            <IconBell stroke={1} color='#CD4B38' size={14} />
                          </div>
                          <span className='whitespace-nowrap text-[11px] font-medium'>
                            {event.time}
                          </span>
                          <IconCircleFilled size={4} />
                          <span className='text-[11px] font-light'>
                            {event.title}
                          </span>
                        </TableCell>
                        <TableCell className='w-[25%] px-1 text-end'>
                          <PriorityBadge priority={event.priority} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className='py-2 text-center text-xs text-gray-400'>
                  No events
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default UpcomingEvents
