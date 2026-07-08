import { useState, useEffect } from 'react'
import moment from 'moment-timezone'

// US market holidays for 2025 (add more years as needed)
const marketHolidays = [
  '2025-01-01', // New Year's Day
  '2025-01-20', // MLK Day
  '2025-02-17', // Presidents Day
  '2025-04-18', // Good Friday
  '2025-05-26', // Memorial Day
  '2025-06-19', // Juneteenth
  '2025-07-04', // Independence Day
  '2025-09-01', // Labor Day
  '2025-11-27', // Thanksgiving
  '2025-12-25', // Christmas
]

const isMarketDay = (date: moment.Moment): boolean => {
  const dayOfWeek = date.day()
  // Check if it's a weekend (Saturday = 6, Sunday = 0)
  if (dayOfWeek === 0 || dayOfWeek === 6) {
    return false
  }

  // Check if it's a holiday
  const dateString = date.format('YYYY-MM-DD')
  if (marketHolidays.includes(dateString)) {
    return false
  }

  return true
}

export default function HeaderTimeIndicator() {
  const [ctime, setCtime] = useState(
    moment().tz('America/New_York').format('hh:mm:ss A')
  )
  const [timeUntilOpen, setTimeUntilOpen] = useState('')
  const [isMarketOpen, setIsMarketOpen] = useState(false)
  const [showCountdown, setShowCountdown] = useState(false)
  const [showWeekendMessage, setShowWeekendMessage] = useState(false)
  const [isWeekendWindow, setIsWeekendWindow] = useState(false)

  useEffect(() => {
    const interval = setInterval(() => {
      const now = moment().tz('America/New_York')
      setCtime(now.format('hh:mm:ss A'))

      // Check if market is open (9:30 AM - 4:00 PM EST on market days)
      const marketOpen = now.clone().hour(9).minute(30).second(0)
      const marketClose = now.clone().hour(16).minute(0).second(0)

      if (isMarketDay(now) && now.isBetween(marketOpen, marketClose)) {
        setIsMarketOpen(true)
        setShowCountdown(false)
        setTimeUntilOpen('')
        setIsWeekendWindow(false)
      } else {
        setIsMarketOpen(false)

        // Check if we're in the weekend window (Friday 4PM EST to Monday 3AM EST)
        const dayOfWeek = now.day()
        const hour = now.hour()

        // Friday 4PM onwards
        const isFridayAfternoon = dayOfWeek === 5 && hour >= 16
        // Saturday all day
        const isSaturday = dayOfWeek === 6
        // Sunday until 3AM
        const isSundayEarly = dayOfWeek === 0 && hour < 3
        // Monday 3AM is still weekend window until market opens at 9:30AM
        const isMondayEarly = dayOfWeek === 1 && hour < 3

        const inWeekendWindow =
          isFridayAfternoon || isSaturday || isSundayEarly || isMondayEarly
        setIsWeekendWindow(inWeekendWindow)

        // Check if we're in the countdown window (Monday 3AM to Monday 9:30 AM)
        const isMondayBeforeOpen =
          dayOfWeek === 1 && hour >= 3 && now.isBefore(marketOpen)

        if (isMondayBeforeOpen) {
          // Show countdown on Monday before market open
          setShowCountdown(true)
          const duration = moment.duration(marketOpen.diff(now))
          const hours = String(Math.floor(duration.asHours())).padStart(2, '0')
          const minutes = String(duration.minutes()).padStart(2, '0')
          const seconds = String(duration.seconds()).padStart(2, '0')
          setTimeUntilOpen(`${hours}:${minutes}:${seconds}`)
        } else if (
          isMarketDay(now) &&
          now.isBefore(marketOpen) &&
          !inWeekendWindow
        ) {
          // Tuesday-Friday before market open, show countdown (but not in weekend window)
          setShowCountdown(true)
          const duration = moment.duration(marketOpen.diff(now))
          const hours = String(Math.floor(duration.asHours())).padStart(2, '0')
          const minutes = String(duration.minutes()).padStart(2, '0')
          const seconds = String(duration.seconds()).padStart(2, '0')
          setTimeUntilOpen(`${hours}:${minutes}:${seconds}`)
        } else {
          // Other closed times (weekday evenings, holidays, etc.)
          setShowCountdown(false)
          setTimeUntilOpen('')
        }
      }
    }, 1000)
    return () => clearInterval(interval)
  }, [])

  // Crossfade animation for weekend message
  useEffect(() => {
    if (isWeekendWindow) {
      // Start with "markets closed" showing
      setShowWeekendMessage(false)

      const fadeInterval = setInterval(() => {
        setShowWeekendMessage((prev) => !prev)
      }, 5000) // 5 seconds between fades
      return () => clearInterval(fadeInterval)
    } else {
      // Reset when not in weekend mode
      setShowWeekendMessage(false)
    }
  }, [isWeekendWindow])

  return (
    <>
      <style>{`
        @keyframes gradient-shift {
          0% {
            background-position: 0% 50%;
          }
          50% {
            background-position: 100% 50%;
          }
          100% {
            background-position: 0% 50%;
          }
        }
        .gradient-animate {
          background: linear-gradient(90deg, #f9a8d4, #e9d5ff, #a5b4fc, #f9a8d4, #e9d5ff);
          background-size: 300% 100%;
          -webkit-background-clip: text;
          background-clip: text;
          -webkit-text-fill-color: transparent;
          animation: gradient-shift 4s ease-in-out infinite;
        }
      `}</style>
      <div className='flex items-center gap-4 p-5 text-xs'>
        <div className='flex items-center gap-3'>
          <div className='relative'>
            {isMarketOpen ? (
              <span
                key='markets-open'
                className='animate-fade-in font-extralight italic text-green-400'
              >
                markets open
              </span>
            ) : showCountdown ? (
              <div
                key='countdown'
                className='animate-fade-in flex items-center gap-3'
              >
                <span className='w-15 font-medium'>{timeUntilOpen}</span>
                <span className='font-extralight italic'>
                  until market open
                </span>
              </div>
            ) : isWeekendWindow ? (
              <span
                key='weekend'
                className='animate-fade-in whitespace-nowrap font-light italic'
                style={{
                  color: showWeekendMessage ? 'transparent' : '#f87171',
                  background: showWeekendMessage
                    ? 'linear-gradient(90deg, #f9a8d4, #e9d5ff, #a5b4fc, #f9a8d4, #e9d5ff)'
                    : 'none',
                  backgroundSize: showWeekendMessage ? '300% 100%' : 'auto',
                  WebkitBackgroundClip: showWeekendMessage ? 'text' : 'initial',
                  backgroundClip: showWeekendMessage ? 'text' : 'initial',
                  WebkitTextFillColor: showWeekendMessage
                    ? 'transparent'
                    : 'initial',
                  transition: 'all 0.5s ease-in-out',
                  animation: showWeekendMessage
                    ? 'gradient-shift 4s ease-in-out infinite'
                    : 'none',
                }}
              >
                {showWeekendMessage ? 'enjoy your weekend' : 'markets closed'}
              </span>
            ) : (
              <span
                key='markets-closed'
                className='animate-fade-in font-light italic text-red-400'
              >
                markets closed
              </span>
            )}
          </div>
        </div>
        <span className='font-medium'>|</span>
        <div className='flex items-center'>
          <span className='font-medium tabular-nums'>
            {ctime.split(' ')[0]}
          </span>
          <span className='ml-1 text-[10px] font-medium'>
            {ctime.split(' ')[1]}
          </span>
        </div>
      </div>
    </>
  )
}
