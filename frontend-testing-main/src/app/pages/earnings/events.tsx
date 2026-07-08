import { IconBell, IconCircleFilled } from '@tabler/icons-react'
import PriorityBadge from '../dashboard/components/upcoming-events/priority-badge'
import { useEffect, useState, useMemo } from 'react'
import {
  economicCalendarService,
  type EconomicCalendarDay,
} from '@/api/economic-calendar.service'

interface EventsProps {
  selectedDate?: Date | null
}

const Events: React.FC<EventsProps> = ({ selectedDate }) => {
  const [economicCalendarData, setEconomicCalendarData] = useState<
    EconomicCalendarDay[]
  >([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const fetchEconomicCalendar = async () => {
      try {
        setLoading(true)
        const response = await economicCalendarService.getEconomicCalendar()

        if (response.success && response.data) {
          setEconomicCalendarData(response.data)
        } else {
          setError(response.error || 'Failed to fetch economic calendar')
        }
      } catch (err) {
        setError('Failed to fetch economic calendar')
      } finally {
        setLoading(false)
      }
    }

    fetchEconomicCalendar()
  }, [])

  // Filter events by selected date if provided
  const filteredEvents = useMemo(() => {
    if (!selectedDate) return economicCalendarData

    const dateStr = selectedDate.toISOString().split('T')[0]
    return economicCalendarData.filter((dayData) => {
      return dayData.dateKey === dateStr
    })
  }, [economicCalendarData, selectedDate])

  if (loading) {
    return (
      <div className='flex items-center justify-center py-4'>
        <div className='text-sm text-gray-400'>Loading events...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div className='flex items-center justify-center py-4'>
        <div className='text-sm text-red-400'>{error}</div>
      </div>
    )
  }

  if (!filteredEvents || filteredEvents.length === 0) {
    return (
      <div className='flex items-center justify-center py-4'>
        <div className='text-sm text-gray-400'>
          {selectedDate ? 'No events for this day' : 'No upcoming events'}
        </div>
      </div>
    )
  }

  return (
    <div className='transition-all duration-300'>
      {filteredEvents.map((dayData, index) => (
        <div
          key={index}
          className='day-block animate-fade-in'
          style={{ animationDelay: `${index * 50}ms` }}
        >
          <div className='day-head my-2 flex items-center text-sm italic text-[#F5F7F7]'>
            <span className='whitespace-nowrap'>{dayData.date}</span>
            <hr className='ml-4 h-0.5 w-full border-t border-[#FFFFFF]' />
            <IconCircleFilled size={15} />
          </div>

          {dayData.events.map((event, index) => (
            <div key={index} className='flex justify-between'>
              <div className='flex items-center gap-2'>
                <IconBell stroke={1} color='#CD4B38' className='h-5 w-5' />
                <span className='min-w-[40px] text-xs font-medium'>
                  {event.time}
                </span>
                <IconCircleFilled size={5} />
                <span className='text-xs font-light'>{event.title}</span>
              </div>
              <div className='text-end'>
                <PriorityBadge priority={event.priority} />
              </div>
            </div>
          ))}
        </div>
      ))}
    </div>
  )
}

export default Events
