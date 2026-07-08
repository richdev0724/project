import React from 'react'
import { useNavigate } from 'react-router-dom'
import type {
  EarningsByDateResponse,
  EarningsItem,
} from '@/api/earnings.service'
import EarningsTickerLogo from './earnings-ticker-logo'

export interface WeekViewProps {
  earningsData: EarningsByDateResponse[]
  loading: boolean
  onDayClick?: (dateStr: string) => void
}

const WeekView: React.FC<WeekViewProps> = ({
  earningsData,
  loading,
  onDayClick,
}) => {
  const navigate = useNavigate()

  const handleTickerClick = (e: React.MouseEvent, symbol: string) => {
    e.stopPropagation() // Prevent day click when clicking ticker
    navigate(`/ticker-overview/${symbol}`)
  }
  if (loading) {
    return (
      <div className='flex h-full w-full items-center justify-center'>
        <div className='text-lg'>Loading earnings data...</div>
      </div>
    )
  }

  if (!earningsData || earningsData.length === 0) {
    return (
      <div className='flex h-full w-full items-center justify-center'>
        <div className='text-lg'>No earnings data available</div>
      </div>
    )
  }

  // Helper to format day - parse as UTC to avoid timezone issues
  const formatDayHeader = (dateStr: string) => {
    // Parse as UTC to avoid timezone shifting the date
    const [year, month, day] = dateStr.split('-').map(Number)
    const date = new Date(year, month - 1, day)
    const dayOfWeek = date.toLocaleDateString('en-US', { weekday: 'long' })
    const dayNumber = date.getDate()
    return { dayOfWeek, dayNumber }
  }

  return (
    <div className='flex h-full w-full gap-3 px-4 py-4'>
      {earningsData.map((dayData, index) => {
        if (!dayData || !dayData.date) {
          return null
        }

        const { dayOfWeek, dayNumber } = formatDayHeader(dayData.date)
        // Parse date correctly for comparison
        const [year, month, day] = dayData.date.split('-').map(Number)
        const earningsDate = new Date(year, month - 1, day)
        const today = new Date()
        today.setHours(0, 0, 0, 0)
        const isToday = earningsDate.toDateString() === today.toDateString()

        return (
          <div
            key={`${dayData.date}-${index}`}
            onClick={() => onDayClick?.(dayData.date)}
            className={`week-day h-full flex-1 ${
              isToday ? 'bg-weekViewBackgroundActive' : 'bg-weekViewBackground'
            } cursor-pointer bg-[length:100%_100%] p-5 transition-all hover:brightness-110`}
          >
            {/* Day Header */}
            <div className='mb-4 text-center'>
              <p className='text-sm font-light text-gray-400'>{dayOfWeek}</p>
              <p className='text-2xl font-medium'>{dayNumber}</p>
            </div>

            {/* Premarket Section */}
            <div className='flex h-[45%] flex-col'>
              <h4 className='mb-3 text-center text-sm font-light'>
                Pre-market
              </h4>
              <div className='scrollbar-thin scrollbar-thumb-gray-600 scrollbar-track-gray-800 max-h-[290px] min-h-0 flex-1 overflow-y-auto'>
                {loading ? (
                  <div className='flex h-full items-center justify-center text-xs text-gray-400'>
                    <div className='flex flex-col items-center gap-2'>
                      <div className='h-4 w-4 animate-spin rounded-full border-2 border-gray-600 border-t-primary'></div>
                      <span>Loading...</span>
                    </div>
                  </div>
                ) : dayData.premarket && dayData.premarket.length > 0 ? (
                  <div className='flex flex-wrap justify-center gap-2'>
                    {dayData.premarket.map(
                      (earning: EarningsItem, idx: number) => {
                        // Check if market cap is > 100B
                        const marketCap = parseFloat(earning.marketcap || '0')
                        const isLargeCap = marketCap >= 100e9

                        return (
                          <div
                            key={idx}
                            onClick={(e) =>
                              handleTickerClick(e, earning.symbol)
                            }
                            className='animate-fade-in flex cursor-pointer flex-col items-center gap-1 transition-transform hover:scale-110'
                            style={{ animationDelay: `${idx * 30}ms` }}
                            title={`${earning.full_name} - Market Cap: ${marketCap >= 1e9 ? (marketCap / 1e9).toFixed(2) + 'B' : (marketCap / 1e6).toFixed(2) + 'M'}`}
                          >
                            <EarningsTickerLogo symbol={earning.symbol} />
                            <span
                              className={`text-[10px] font-medium ${isLargeCap ? 'text-orange-500 hover:text-orange-400' : 'text-white hover:text-gray-300'}`}
                            >
                              {earning.symbol}
                            </span>
                          </div>
                        )
                      }
                    )}
                  </div>
                ) : (
                  <div className='flex h-full items-center justify-center text-xs text-gray-400'>
                    No earnings
                  </div>
                )}
              </div>
            </div>

            {/* Afterhours Section */}
            <div className='flex h-[45%] flex-col'>
              <h4 className='mb-3 text-center text-sm font-light'>
                After-hours
              </h4>
              <div className='scrollbar-thin scrollbar-thumb-gray-600 scrollbar-track-gray-800 max-h-[290px] min-h-0 flex-1 overflow-y-auto'>
                {loading ? (
                  <div className='flex h-full items-center justify-center text-xs text-gray-400'>
                    <div className='flex flex-col items-center gap-2'>
                      <div className='h-4 w-4 animate-spin rounded-full border-2 border-gray-600 border-t-primary'></div>
                      <span>Loading...</span>
                    </div>
                  </div>
                ) : dayData.afterhours && dayData.afterhours.length > 0 ? (
                  <div className='flex flex-wrap justify-center gap-2'>
                    {dayData.afterhours.map(
                      (earning: EarningsItem, idx: number) => {
                        // Check if market cap is > 100B
                        const marketCap = parseFloat(earning.marketcap || '0')
                        const isLargeCap = marketCap >= 100e9

                        return (
                          <div
                            key={idx}
                            onClick={(e) =>
                              handleTickerClick(e, earning.symbol)
                            }
                            className='animate-fade-in flex cursor-pointer flex-col items-center gap-1 transition-transform hover:scale-110'
                            style={{ animationDelay: `${idx * 30}ms` }}
                            title={`${earning.full_name} - Market Cap: ${marketCap >= 1e9 ? (marketCap / 1e9).toFixed(2) + 'B' : (marketCap / 1e6).toFixed(2) + 'M'}`}
                          >
                            <EarningsTickerLogo symbol={earning.symbol} />
                            <span
                              className={`text-[10px] font-medium ${isLargeCap ? 'text-orange-500 hover:text-orange-400' : 'text-white hover:text-gray-300'}`}
                            >
                              {earning.symbol}
                            </span>
                          </div>
                        )
                      }
                    )}
                  </div>
                ) : (
                  <div className='flex h-full items-center justify-center text-xs text-gray-400'>
                    No earnings
                  </div>
                )}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}

export default WeekView
