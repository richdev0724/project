import { useEarning } from '@/hooks/use-earning'
import { useStockPrices } from '@/hooks/use-stock-prices'
import { useSymbolLevels } from '@/hooks/use-symbol-levels'
import { useTopDarkPoolOrders } from '@/hooks/use-top-darkpool-orders'
import { IconChartCandle, IconTimeline } from '@tabler/icons-react'
import {
  CrosshairMode,
  IChartApi,
  ISeriesApi,
  createChart,
} from 'lightweight-charts'
import { useEffect, useRef, useState } from 'react'

// Type definitions for chart data
interface CandleData {
  time: string
  open: number
  high: number
  low: number
  close: number
}

interface LineChartData {
  time: string
  value: number
}

// Chart component props type
interface ChartProps {
  symbol?: string
  className?: string
}

function Chart({ symbol = 'AAPL' }: ChartProps) {
  const [chartType, setChartType] = useState<'candlestick' | 'line'>(
    'candlestick'
  )
  const chartContainerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)
  const seriesRef = useRef<ISeriesApi<'Candlestick' | 'Line'> | null>(null)
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 })

  // Calculate the date 15 days before today as the start date
  const startDate = new Date()
  startDate.setDate(startDate.getDate() - 60)
  const startDateLevels = new Date()
  startDateLevels.setDate(startDateLevels.getDate() - 10)

  // End date is today
  const endDate = new Date()

  const formatDate = (date: Date) => {
    const year = date.getFullYear()
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  const { data: symbolLevels, isLoading: levelsLoading } = useSymbolLevels({
    symbol: symbol,
    startDate: formatDate(startDateLevels),
    endDate: formatDate(endDate),
    levelsCount: 10,
  })

  const { data: earnings, isLoading: earningLoading } = useEarning(symbol)

  const { data: topDarkPoolOrders, isLoading: darkPoolOrdersLoading } =
    useTopDarkPoolOrders({
      symbol: symbol,
      startDate: formatDate(startDateLevels),
      endDate: formatDate(endDate),
      levelsCount: 10,
    })

  const { chartData, isLoading } = useStockPrices({
    symbol: symbol,
    startDate: formatDate(startDate),
    endDate: formatDate(endDate),
    timeframe: 'day',
    interval: '1',
  })

  const toggleChartType = () => {
    setChartType((prevType) =>
      prevType === 'candlestick' ? 'line' : 'candlestick'
    )
  }

  // Responsive resize handler
  useEffect(() => {
    const handleResize = () => {
      if (chartContainerRef.current) {
        const { width, height } =
          chartContainerRef.current.getBoundingClientRect()
        setDimensions({ width, height: Math.max(height, 800) })
      }
    }

    // Initial sizing
    handleResize()

    // Add resize listener
    window.addEventListener('resize', handleResize)

    // Cleanup listener
    return () => {
      window.removeEventListener('resize', handleResize)
    }
  }, [])

  useEffect(() => {
    if (!chartContainerRef.current) {
      return
    }

    const cleanup = () => {
      if (chartRef.current) {
        chartRef.current.remove()
        chartRef.current = null
        seriesRef.current = null
      }
    }

    if (!chartRef.current) {
      chartRef.current = createChart(chartContainerRef.current, {
        width: dimensions.width,
        height: dimensions.height,
        layout: {
          background: { color: 'transparent' },
          textColor: '#333',
        },
        grid: {
          vertLines: { color: '#282A2F' },
          horzLines: { color: '#282A2F' },
        },
        crosshair: {
          mode: CrosshairMode.Normal,
        },
        timeScale: {
          visible: true,
          borderColor: 'transparent',
        },
        leftPriceScale: {
          visible: true,
          borderColor: 'transparent',
        },
        rightPriceScale: {
          visible: false,
        },
        handleScroll: {
          mouseWheel: false,
          pressedMouseMove: false,
        },
        handleScale: {
          mouseWheel: false,
          pinch: false,
          axisDoubleClickReset: false,
        },
      })
    } else if (chartRef.current) {
      // Update chart dimensions
      chartRef.current.resize(dimensions.width, dimensions.height)
    }

    if (seriesRef.current) {
      chartRef.current.removeSeries(seriesRef.current)
      seriesRef.current = null
    }

    seriesRef.current =
      chartType === 'candlestick'
        ? chartRef.current.addCandlestickSeries({
            upColor: '#0ea5e9',
            downColor: '#F23645',
          })
        : chartRef.current.addLineSeries({
            color: 'rgba(34, 211, 238, 1)',
            lineWidth: 1,
          })

    const mainData =
      chartType === 'candlestick'
        ? chartData.candleDataSmall
        : chartData.lineDataSmall

    if (chartType === 'candlestick') {
      ;(seriesRef.current as ISeriesApi<'Candlestick'>).setData(
        mainData as CandleData[]
      )
    } else {
      ;(seriesRef.current as ISeriesApi<'Line'>).setData(
        mainData as LineChartData[]
      )
    }

    const litValid = Array.isArray(symbolLevels) ? symbolLevels : []
    const darkValid = Array.isArray(topDarkPoolOrders) ? topDarkPoolOrders : []

    const levelMap = new Map()
    ;[...litValid, ...darkValid].forEach((level) => {
      if (!levelMap.has(level.price)) {
        levelMap.set(level.price, {
          price: level.price,
          type: level.type,
        })
      }
    })

    levelMap.forEach((level) => {
      const color = '#8b5cf6'
      const lineStyle = 1

      if (seriesRef.current) {
        seriesRef.current.createPriceLine({
          price: parseFloat(level.price),
          color: color,
          lineWidth: 2,
          lineStyle: lineStyle,
        })
      }
    })

    chartRef.current.timeScale().fitContent()

    return cleanup
  }, [
    chartData,
    chartType,
    dimensions,
    symbolLevels,
    topDarkPoolOrders,
    earnings,
    isLoading,
    earningLoading,
    darkPoolOrdersLoading,
    levelsLoading,
  ])

  return (
    <div>
      <div className='flex flex-row justify-between p-4 text-gray-300'>
        <div className='flex flex-row items-center space-x-2'>
          <img
            src={`/src/assets/stock-logos/${symbol === 'PRN' ? 'PRN_' : symbol}.svg`}
            alt='logo'
            className='h-14 w-14 rounded-full'
          />
          {earnings?.[0] ? (
            <div>
              <p className='text-lg font-bold'>{symbol}</p>
              <p className='text-md font-bold'>Earnings Data</p>
            </div>
          ) : (
            <div>
              <p className='text-lg font-bold'>{symbol}</p>
              <p className='text-md font-bold'>No Earnings Data</p>
            </div>
          )}
        </div>
        <div className='flex flex-row space-x-4'>
          <button
            onClick={toggleChartType}
            className='text-gray-300 hover:cursor-pointer hover:text-sky-500'
          >
            {chartType === 'candlestick' ? (
              <IconChartCandle />
            ) : (
              <IconTimeline />
            )}
          </button>
        </div>
      </div>
      <div ref={chartContainerRef} className='min-h-[90%] w-full flex-grow' />
    </div>
  )
}

export default Chart
