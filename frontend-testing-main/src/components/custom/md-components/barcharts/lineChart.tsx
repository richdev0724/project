import { useStockPrices } from '@/hooks/use-stock-prices'
import {
  IChartApi,
  LineData,
  UTCTimestamp,
  createChart,
} from 'lightweight-charts'
import { useEffect, useRef } from 'react'

// Types for component props
interface LineChartProps {
  symbol?: string
  timeframe?: 'Today' | 'Weekly' | 'Monthly'
}

// Static sample data for stock prices
const staticStockData: LineData[] = [
  {
    time: Math.floor(new Date('2024-01-01').getTime() / 1000) as UTCTimestamp,
    value: 150.25,
  },
  {
    time: Math.floor(new Date('2024-01-02').getTime() / 1000) as UTCTimestamp,
    value: 151.5,
  },
  {
    time: Math.floor(new Date('2024-01-03').getTime() / 1000) as UTCTimestamp,
    value: 149.75,
  },
  {
    time: Math.floor(new Date('2024-01-04').getTime() / 1000) as UTCTimestamp,
    value: 152.1,
  },
  {
    time: Math.floor(new Date('2024-01-05').getTime() / 1000) as UTCTimestamp,
    value: 153.45,
  },
  {
    time: Math.floor(new Date('2024-01-06').getTime() / 1000) as UTCTimestamp,
    value: 151.9,
  },
  {
    time: Math.floor(new Date('2024-01-07').getTime() / 1000) as UTCTimestamp,
    value: 154.2,
  },
  {
    time: Math.floor(new Date('2024-01-08').getTime() / 1000) as UTCTimestamp,
    value: 155.6,
  },
  {
    time: Math.floor(new Date('2024-01-09').getTime() / 1000) as UTCTimestamp,
    value: 153.8,
  },
  {
    time: Math.floor(new Date('2024-01-10').getTime() / 1000) as UTCTimestamp,
    value: 156.25,
  },
]

function getMostRecentWeekday() {
  const today = new Date()
  const dayOfWeek = today.getDay()
  if (dayOfWeek === 0) {
    // Sunday
    today.setDate(today.getDate() - 2)
  } else if (dayOfWeek === 6) {
    // Saturday
    today.setDate(today.getDate() - 1)
  }
  today.setHours(9, 30, 0, 0)
  const isDST = today.getTimezoneOffset() === 240
  const utcOffset = isDST ? 4 : 5
  today.setHours(today.getHours() - utcOffset)
  return today.toISOString()
}

function getStartDate(timeframe: string) {
  const startDate = new Date(getMostRecentWeekday())
  if (timeframe === 'Today') {
    startDate.setHours(9, 30, 0, 0)
  } else if (timeframe === 'Weekly') {
    startDate.setDate(startDate.getDate() - 7)
  } else if (timeframe === 'Monthly') {
    startDate.setDate(startDate.getDate() - 120)
  }
  return startDate.toISOString()
}

function formatDateWithTime(
  date: string | number | Date,
  hour: number,
  minute = 0
) {
  const tempDate = new Date(date)
  tempDate.setHours(hour + 5, minute)
  return tempDate.toISOString().split('.')[0] + 'Z'
}
export default function LineChart({
  symbol = 'SMST',
  timeframe = 'Today',
}: LineChartProps) {
  const chartContainerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)

  const recentWeekday = getMostRecentWeekday()
  const startDate = getStartDate(timeframe)
  const endDate = formatDateWithTime(recentWeekday, 16, 30) // 4:30 PM EST

  let interval
  if (timeframe === 'Today') {
    interval = '5'
  } else if (timeframe === 'Weekly') {
    interval = '30'
  } else if (timeframe === 'Monthly') {
    interval = '1'
  }

  useStockPrices({
    symbol: symbol,
    startDate: startDate,
    endDate: endDate,
    timeframe: timeframe === 'Monthly' ? 'day' : 'minute',
    interval: interval,
  })

  useEffect(() => {
    if (chartContainerRef.current) {
      // Remove existing chart if it exists
      if (chartRef.current) {
        chartRef.current.remove()
        chartRef.current = null
      }

      // Create new chart
      const chart = createChart(chartContainerRef.current, {
        width: chartContainerRef.current.clientWidth,
        height: 300, // Fixed height for example
        layout: {
          background: { color: 'transparent' },
          textColor: '#DDD',
        },
        grid: {
          vertLines: { color: 'rgba(255, 255, 255, 0.1)' },
          horzLines: { color: 'rgba(255, 255, 255, 0.1)' },
        },
        rightPriceScale: {
          visible: true,
          borderColor: 'rgba(255, 255, 255, 0.3)',
        },
        timeScale: {
          visible: true,
          borderColor: 'rgba(255, 255, 255, 0.3)',
        },
      })

      // Add line series
      const lineSeries = chart.addLineSeries({
        color: '#7dd3fc',
        lineWidth: 2,
        priceScaleId: 'right',
      })

      // Set the static data
      lineSeries.setData(staticStockData)

      // Store reference to chart
      chartRef.current = chart

      // Fit content
      chart.timeScale().fitContent()

      // Resize handler
      const handleResize = () => {
        if (chartRef.current && chartContainerRef.current) {
          chartRef.current.resize(
            chartContainerRef.current.clientWidth,
            chartContainerRef.current.clientHeight
          )
        }
      }

      // Add resize listener
      window.addEventListener('resize', handleResize)

      // Cleanup function
      return () => {
        window.removeEventListener('resize', handleResize)
        if (chartRef.current) {
          chartRef.current.remove()
          chartRef.current = null
        }
      }
    }
  }, [symbol, timeframe])

  return (
    <div className='w-full'>
      <div
        ref={chartContainerRef}
        className='w-full'
        style={{ height: '300px' }}
      />
    </div>
  )
}
