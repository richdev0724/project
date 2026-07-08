import React, { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { useMarketPrices } from '@/hooks/use-market-prices'
// import { useTickerSnapshot } from '@/hooks/use-ticker-snapshot'

// Calculate EMA
const calculateEMA = (prices: number[], period: number): number[] => {
  if (prices.length === 0) return []

  const k = 2 / (period + 1)
  const emaData: number[] = []
  let ema = prices[0]

  emaData.push(ema)

  for (let i = 1; i < prices.length; i++) {
    ema = prices[i] * k + ema * (1 - k)
    emaData.push(ema)
  }

  return emaData
}

interface StockData {
  timestamp: number
  open: number
  high: number
  low: number
  close: number
  volume: number
}

interface StockChartProps {
  className?: string
  onPriceData?: (data: {
    currentPrice: number
    priceChange: number
    percentChange: number
  }) => void
  onIndicatorState?: (state: {
    activePlots: string[]
    togglePlot: (plotId: string) => void
    PLOT_OPTIONS: typeof PLOT_OPTIONS
  }) => void
}

// Plot options
const PLOT_OPTIONS = [
  { id: 'ema50', label: 'EMA50' },
  { id: 'ema200', label: 'EMA200' },
  { id: 'dpl', label: 'DPL' },
  { id: 'lpl', label: 'LPL' },
  { id: 'mobe', label: 'MOBE' },
]

const StockChart: React.FC<StockChartProps> = ({
  className,
  onPriceData,
  onIndicatorState,
}) => {
  const { symbol = '' } = useParams()

  // Use simple market prices hook for today's data only
  const {
    data: polygonData,
    isLoading,
    error: marketError,
  } = useMarketPrices('1D', [symbol])

  // const { data: snapshotData } = useTickerSnapshot(symbol)

  // Convert polygon data to stock data format
  const stockData: StockData[] =
    polygonData?.[symbol]?.results?.map((item) => ({
      timestamp: item.t,
      open: item.o,
      high: item.h,
      low: item.l,
      close: item.c,
      volume: item.v,
    })) || []

  const error = marketError

  const [activePlots, setActivePlots] = useState<string[]>([
    'ema50',
    'ema200',
    'dpl',
    'lpl',
    'mobe',
  ])

  // Fetch snapshot data for indicators (currently unused but may be needed for other indicators)
  // const { data: snapshotData } = useTickerSnapshot(symbol)

  // Get previous day's high and low for DPL/LPL (currently unused but may be needed for other indicators)
  // const prevDayHigh = snapshotData?.[symbol]?.prevDay?.h
  // const prevDayLow = snapshotData?.[symbol]?.prevDay?.l

  const togglePlot = (plotId: string) => {
    setActivePlots((prev) => {
      if (prev.includes(plotId)) {
        return prev.filter((p) => p !== plotId)
      } else {
        return [...prev, plotId]
      }
    })
  }

  // Pass indicator state to parent
  useEffect(() => {
    if (onIndicatorState) {
      onIndicatorState({
        activePlots,
        togglePlot,
        PLOT_OPTIONS,
      })
    }
  }, [activePlots]) // Remove onIndicatorState from dependencies to prevent infinite loop

  // Send price data to parent component when data changes
  useEffect(() => {
    if (stockData.length > 0 && onPriceData) {
      const currentPrice = stockData[stockData.length - 1]?.close || 0
      const previousClose = stockData[0]?.close || 0
      const priceChange = currentPrice - previousClose
      const percentChange = previousClose
        ? (priceChange / previousClose) * 100
        : 0

      onPriceData({
        currentPrice,
        priceChange,
        percentChange,
      })
    }
  }, [stockData]) // Remove onPriceData from dependencies to prevent infinite loop

  // Calculate EMAs from today's minute data
  const calculateEMAsFromTodayData = () => {
    if (stockData.length === 0) return { ema50Data: [], ema200Data: [] }

    const prices = stockData.map((d) => d.close)
    const ema50Data = calculateEMA(prices, 50)
    const ema200Data = calculateEMA(prices, 200)

    return { ema50Data, ema200Data }
  }

  // Send price data to parent component when data changes
  useEffect(() => {
    if (stockData.length > 0 && onPriceData) {
      const currentPrice = stockData[stockData.length - 1]?.close || 0
      const previousClose = stockData[0]?.close || 0
      const priceChange = currentPrice - previousClose
      const percentChange = previousClose
        ? (priceChange / previousClose) * 100
        : 0

      onPriceData({
        currentPrice,
        priceChange,
        percentChange,
      })
    }
  }, [stockData, onPriceData])

  if (isLoading) {
    return (
      <div className={`flex items-center justify-center ${className}`}>
        <div className='text-gray-400'>Loading chart...</div>
      </div>
    )
  }

  if (error || stockData.length === 0) {
    return (
      <div className={`flex items-center justify-center ${className}`}>
        <div className='text-gray-400'>{error || 'No data available'}</div>
      </div>
    )
  }

  // Calculate chart dimensions and scaling
  const chartWidth = 800
  const chartHeight = 350
  const padding = 20

  const prices = stockData.map((d) => d.close)
  const minPrice = Math.min(...prices)
  const maxPrice = Math.max(...prices)
  const priceRange = maxPrice - minPrice

  // Add some padding to the price range
  const paddedMinPrice = minPrice - priceRange * 0.1
  const paddedMaxPrice = maxPrice + priceRange * 0.1
  const paddedPriceRange = paddedMaxPrice - paddedMinPrice

  const { ema50Data, ema200Data } = calculateEMAsFromTodayData()

  // Generate SVG path for the line chart
  const generatePath = () => {
    if (stockData.length === 0) return ''

    const points = stockData.map((point, index) => {
      // Extend line 20px wider by reducing padding by 10px on each side
      const x =
        padding -
        10 +
        (index / (stockData.length - 1)) * (chartWidth - 2 * (padding - 10))
      const y =
        padding +
        ((paddedMaxPrice - point.close) / paddedPriceRange) *
          (chartHeight - 2 * padding)
      return `${x},${y}`
    })

    return `M ${points.join(' L ')}`
  }

  // Generate SVG path for EMA lines
  const generateEMAPath = (emaData: number[]) => {
    if (emaData.length === 0) return ''

    const points = emaData.map((value, index) => {
      const x =
        padding -
        10 +
        (index / (emaData.length - 1)) * (chartWidth - 2 * (padding - 10))
      const y =
        padding +
        ((paddedMaxPrice - value) / paddedPriceRange) *
          (chartHeight - 2 * padding)
      return `${x},${y}`
    })

    return `M ${points.join(' L ')}`
  }

  return (
    <div className={`p-4 ${className} relative`}>
      <style>
        {`
          .price-label {
            font-size: 6px !important;
          }
        `}
      </style>
      {/* SVG Chart */}
      <div>
        <svg
          width='100%'
          height={chartHeight}
          className='h-auto w-full'
          viewBox={`0 0 ${chartWidth} ${chartHeight}`}
          style={{ outline: 'none', border: 'none', maxWidth: '800px' }}
        >
          {/* Clean background */}
          <rect width='100%' height='100%' fill='transparent' />

          {/* Price line */}
          <path
            d={generatePath()}
            fill='none'
            stroke='#F5F5F5'
            strokeWidth='0.75'
            strokeLinecap='round'
            strokeLinejoin='round'
          />

          {/* Price area fill */}
          <path
            d={`${generatePath()} L ${chartWidth - (padding - 10)},${chartHeight - padding} L ${padding - 10},${chartHeight - padding} Z`}
            fill='url(#priceGradient)'
            opacity='0.075'
          />

          {/* 50 EMA Line */}
          {activePlots.includes('ema50') && ema50Data.length > 0 && (
            <path
              d={generateEMAPath(ema50Data)}
              fill='none'
              stroke='#F6C85D'
              strokeWidth='1.5'
              strokeLinecap='round'
              strokeLinejoin='round'
            />
          )}

          {/* 200 EMA Line */}
          {activePlots.includes('ema200') && ema200Data.length > 0 && (
            <path
              d={generateEMAPath(ema200Data)}
              fill='none'
              stroke='#8B5CF6'
              strokeWidth='1.5'
              strokeLinecap='round'
              strokeLinejoin='round'
            />
          )}

          {/* Gradient definition */}
          <defs>
            <linearGradient
              id='priceGradient'
              x1='0%'
              y1='0%'
              x2='0%'
              y2='100%'
            >
              <stop offset='0%' stopColor='#F5F5F5' />
              <stop offset='100%' stopColor='#F5F5F5' stopOpacity='0' />
            </linearGradient>
          </defs>
        </svg>
      </div>

      {/* Price labels - positioned relative to main container to stay fixed */}
      <div
        className='price-label absolute left-[-30px] top-[16px] text-gray-500'
        style={{ fontSize: '12px', transform: 'scale(0.8)' }}
      >
        ${paddedMaxPrice.toFixed(2)}
      </div>
      <div
        className='price-label absolute left-[-30px] text-gray-500'
        style={{
          fontSize: '12px',
          transform: 'scale(0.8)',
          top: `${16 + chartHeight * 0.25}px`,
        }}
      >
        ${(paddedMinPrice + paddedPriceRange * 0.75).toFixed(2)}
      </div>
      <div
        className='price-label absolute left-[-30px] text-gray-500'
        style={{
          fontSize: '12px',
          transform: 'scale(0.8)',
          top: `${16 + chartHeight * 0.5}px`,
        }}
      >
        ${(paddedMinPrice + paddedPriceRange * 0.5).toFixed(2)}
      </div>
      <div
        className='price-label absolute left-[-30px] text-gray-500'
        style={{
          fontSize: '12px',
          transform: 'scale(0.8)',
          top: `${16 + chartHeight * 0.75}px`,
        }}
      >
        ${(paddedMinPrice + paddedPriceRange * 0.25).toFixed(2)}
      </div>
      <div
        className='price-label absolute left-[-30px] text-gray-500'
        style={{
          fontSize: '12px',
          transform: 'scale(0.8)',
          top: `${16 + chartHeight}px`,
        }}
      >
        ${paddedMinPrice.toFixed(2)}
      </div>
    </div>
  )
}

export default StockChart
