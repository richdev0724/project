import React from 'react'
import { useNavigate } from 'react-router-dom'
import {
  IconBell,
  IconCircleFilled,
  IconChevronLeft,
} from '@tabler/icons-react'
import NewsList from './news-list'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import PriorityBadge from '../dashboard/components/upcoming-events/priority-badge'
import EarningsTickerLogo from './earnings-ticker-logo'
import type {
  EarningsByDateResponse,
  EarningsItem,
} from '@/api/earnings.service'

const economicData = [
  {
    time: '09:45',
    report: 'S&P final U.S. manufacturing PMI',
    period: 'January',
    actual: '51.2',
    medianForecast: '-',
    previous: '50.1',
    impact: 'LOW',
  },
  {
    time: '10:00',
    report: 'Construction Spending',
    period: 'December',
    actual: '0.5%',
    medianForecast: '0.3%',
    previous: '0.2%',
    impact: 'LOW',
  },
  {
    time: '10:00',
    report: 'ISM Manufacturing',
    period: 'January',
    actual: '50.9%',
    medianForecast: '50.0%',
    previous: '49.2%',
    impact: 'HIGH',
  },
  {
    time: '11:00',
    report: 'Boston Fed President Susan Collins',
    period: '-',
    actual: '-',
    medianForecast: '-',
    previous: '-',
    impact: 'HIGH',
  },
  {
    time: '12:30',
    report: 'Atlanta Fed President Raphael Bostic',
    period: '-',
    actual: '-',
    medianForecast: '-',
    previous: '-',
    impact: 'MEDIUM',
  },
]

const afterData = [
  {
    time: '16:00',
    report: 'Quarterly Bond Reallocation',
    period: 'January',
    actual: '2.1%',
    medianForecast: '2.0%',
    previous: '1.9%',
    impact: 'HIGH',
  },
  {
    time: '18:00',
    report: 'Auto Sales',
    period: 'January',
    actual: '15.6 mil',
    medianForecast: '-',
    previous: '16.9 mil',
    impact: 'MEDIUM',
  },
]

export interface DayViewProps {
  date?: Date
  earningsData: EarningsByDateResponse | null
  onBack?: () => void
}

const DayView: React.FC<DayViewProps> = ({ date, earningsData, onBack }) => {
  const navigate = useNavigate()

  const handleTickerClick = (e: React.MouseEvent, symbol: string) => {
    e.stopPropagation()
    navigate(`/ticker-overview/${symbol}`)
  }

  // Format date for display
  const formatDate = (date?: Date) => {
    if (!date) return 'Select a date'
    const options: Intl.DateTimeFormatOptions = {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    }
    return date.toLocaleDateString('en-US', options)
  }

  return (
    <div className='flex h-full w-full gap-3'>
      <div className='flex-1 p-6'>
        <div className='mb-5 flex items-center gap-4'>
          {onBack && (
            <button
              onClick={onBack}
              className='flex h-8 w-8 items-center justify-center rounded-full bg-[#2A2D31] transition-colors hover:bg-[#3A3D41]'
              aria-label='Back to week view'
            >
              <IconChevronLeft size={16} />
            </button>
          )}
          <h3 className='text-lg font-medium italic'>{formatDate(date)}</h3>
        </div>
        <div className='mb-5 flex h-[calc(50%_-_25px)] min-h-[calc(50%_-_25px)] flex-col px-3'>
          <h4 className='itlalic flex items-center font-normal'>
            <span className='mr-3 whitespace-nowrap'>Pre-market Earnings</span>
            <IconCircleFilled size={6} />
            <hr className='-mx-1 h-0.5 w-full border-t border-[#FFFFFF]' />
            <IconCircleFilled size={6} />
          </h4>
          <div className='mb-2 mt-1 flex flex-wrap justify-center gap-3'>
            {earningsData?.premarket && earningsData.premarket.length > 0 ? (
              earningsData.premarket.map(
                (earning: EarningsItem, idx: number) => {
                  const marketCap = parseFloat(earning.marketcap || '0')
                  const isLargeCap = marketCap >= 100e9
                  return (
                    <div
                      key={idx}
                      onClick={(e) => handleTickerClick(e, earning.symbol)}
                      className='animate-fade-in flex cursor-pointer flex-col items-center gap-1 transition-transform hover:scale-110'
                      style={{ animationDelay: `${idx * 30}ms` }}
                      title={`${earning.full_name} - Market Cap: ${marketCap >= 1e9 ? (marketCap / 1e9).toFixed(2) + 'B' : (marketCap / 1e6).toFixed(2) + 'M'}`}
                    >
                      <EarningsTickerLogo symbol={earning.symbol} />
                      <span
                        className={`text-xs font-medium ${isLargeCap ? 'text-orange-500 hover:text-orange-400' : 'text-white hover:text-gray-300'}`}
                      >
                        {earning.symbol}
                      </span>
                    </div>
                  )
                }
              )
            ) : (
              <div className='flex h-full items-center justify-center text-sm text-gray-400'>
                No pre-market earnings
              </div>
            )}
          </div>
          <div className='flex-1 overflow-y-auto'>
            <Table className='text-[13px]'>
              <TableHeader className='sticky top-0 bg-transparent'>
                <TableRow
                  key={0}
                  className='items-center space-x-3 border-none'
                >
                  <TableHead className='w-[100px] whitespace-nowrap text-center text-[13px]'>
                    Time
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-left text-[13px]'>
                    Report
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Period
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Actual
                  </TableHead>
                  <TableHead className='w-[180px] whitespace-nowrap text-center text-[13px]'>
                    Median Forecast
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Previous
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Impact
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {economicData.map((item, index) => (
                  <TableRow
                    key={index}
                    className='items-center space-x-3 border-none'
                  >
                    <TableCell className='flex h-8 items-center gap-2 text-center font-light'>
                      <IconBell stroke={1} color='#CD4B38' />
                      <span className='min-w-[40px] text-[13px] font-medium'>
                        {item.time}
                      </span>
                      <IconCircleFilled size={5} />
                    </TableCell>
                    <TableCell className='h-8 text-left font-light'>
                      {item.report}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.period}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.actual}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.medianForecast}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.previous}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      <PriorityBadge priority={item.impact} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
        <div className='mb-5 flex h-[calc(50%_-_25px)] min-h-[calc(50%_-_25px)] flex-col px-3'>
          <h4 className='itlalic flex items-center font-normal'>
            <span className='mr-3 whitespace-nowrap'>After-hours Earnings</span>
            <IconCircleFilled size={6} />
            <hr className='-mx-1 h-0.5 w-full border-t border-[#FFFFFF]' />
            <IconCircleFilled size={6} />
          </h4>
          <div className='mb-2 mt-1 flex flex-wrap justify-center gap-3'>
            {earningsData?.afterhours && earningsData.afterhours.length > 0 ? (
              earningsData.afterhours.map(
                (earning: EarningsItem, idx: number) => {
                  const marketCap = parseFloat(earning.marketcap || '0')
                  const isLargeCap = marketCap >= 100e9
                  return (
                    <div
                      key={idx}
                      onClick={(e) => handleTickerClick(e, earning.symbol)}
                      className='animate-fade-in flex cursor-pointer flex-col items-center gap-1 transition-transform hover:scale-110'
                      style={{ animationDelay: `${idx * 30}ms` }}
                      title={`${earning.full_name} - Market Cap: ${marketCap >= 1e9 ? (marketCap / 1e9).toFixed(2) + 'B' : (marketCap / 1e6).toFixed(2) + 'M'}`}
                    >
                      <EarningsTickerLogo symbol={earning.symbol} />
                      <span
                        className={`text-xs font-medium ${isLargeCap ? 'text-orange-500 hover:text-orange-400' : 'text-white hover:text-gray-300'}`}
                      >
                        {earning.symbol}
                      </span>
                    </div>
                  )
                }
              )
            ) : (
              <div className='flex h-full items-center justify-center text-sm text-gray-400'>
                No after-hours earnings
              </div>
            )}
          </div>
          <div className='flex-1 overflow-y-auto'>
            <Table className='text-[13px]'>
              <TableHeader className='sticky top-0 bg-transparent'>
                <TableRow
                  key={0}
                  className='items-center space-x-3 border-none'
                >
                  <TableHead className='w-[100px] whitespace-nowrap text-center text-[13px]'>
                    Time
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-left text-[13px]'>
                    Report
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Period
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Actual
                  </TableHead>
                  <TableHead className='w-[180px] whitespace-nowrap text-center text-[13px]'>
                    Median Forecast
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Previous
                  </TableHead>
                  <TableHead className='whitespace-nowrap text-center text-[13px]'>
                    Impact
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {afterData.map((item, index) => (
                  <TableRow
                    key={index}
                    className='items-center space-x-3 border-none'
                  >
                    <TableCell className='flex h-8 items-center gap-2 text-center font-light'>
                      <IconBell stroke={1} color='#CD4B38' />
                      <span className='min-w-[40px] text-[13px] font-medium'>
                        {item.time}
                      </span>
                      <IconCircleFilled size={5} />
                    </TableCell>
                    <TableCell className='h-8 text-left font-light'>
                      {item.report}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.period}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.actual}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.medianForecast}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      {item.previous}
                    </TableCell>
                    <TableCell className='h-8 text-center font-light'>
                      <PriorityBadge priority={item.impact} />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      </div>
      <NewsList />
    </div>
  )
}

export default DayView
