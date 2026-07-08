import Loader from '@/components/loader'
import {
  MAGNIFICENT_SEVEN_LINE_GRADIENT_CONSTANTS,
  TIMEFRAME,
} from '@/constants'
import { useMarketPrices } from '@/hooks/use-market-prices'
import moment from 'moment-timezone'
// import { useEffect, useState, useCallback, useMemo } from 'react'
import { useState, useMemo } from 'react'

import {
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  ReferenceLine,
} from 'recharts'

// Magnificent Seven tickers
const MAG7_TICKERS = [
  { id: 1, ticker: 'NVDA' },
  { id: 2, ticker: 'META' },
  { id: 3, ticker: 'MSFT' },
  { id: 4, ticker: 'AAPL' },
  { id: 5, ticker: 'GOOGL' },
  { id: 6, ticker: 'AMZN' },
  { id: 7, ticker: 'TSLA' },
]

// Get just the tickers array for the API call
const MAG7_TICKER_SYMBOLS = MAG7_TICKERS.map((t) => t.ticker)

const CustomDot = ({
  cx,
  cy,
  stroke,
  index,
  data,
  stroke2,
  ticker,
  activeDataKey,
}: any) => {
  if (index !== 0 && index !== data.length - 1) return <svg />

  const opacity = activeDataKey === '' ? 1 : activeDataKey === ticker ? 1 : 0.25

  if (index === 0) {
    return (
      <svg
        x={cx - 1}
        y={cy - 2}
        r={5}
        width='5'
        height='5'
        viewBox='0 0 5 5'
        fill='none'
        xmlns='http://www.w3.org/2000/svg'
        opacity={opacity}
      >
        <path
          d='M4.11899 2.4654C4.11899 3.42173 3.35592 4.18079 2.43449 4.18079C1.51306 4.18079 0.75 3.42173 0.75 2.4654C0.75 1.50906 1.51306 0.75 2.43449 0.75C3.35592 0.75 4.11899 1.50906 4.11899 2.4654Z'
          stroke={stroke}
          strokeWidth='2'
        />
      </svg>
    )
  }

  if (index === data.length - 1) {
    return (
      <svg
        x={cx - 1}
        y={cy - 2}
        r={5}
        width='5'
        height='5'
        viewBox='0 0 5 5'
        fill='none'
        xmlns='http://www.w3.org/2000/svg'
        opacity={opacity}
      >
        <path
          d='M4.11899 2.4654C4.11899 3.42173 3.35592 4.18079 2.43449 4.18079C1.51306 4.18079 0.75 3.42173 0.75 2.4654C0.75 1.50906 1.51306 0.75 2.43449 0.75C3.35592 0.75 4.11899 1.50906 4.11899 2.4654Z'
          stroke={stroke2}
          strokeWidth='2'
        />
      </svg>
    )
  }
}

// Right-side labels component using Recharts positioning
const CustomRightLabel = (props: any) => {
  const { x, y, dataKey, index, data } = props
  // Only show label at the last data point (end of line)
  if (index !== data.length - 1) return <svg />

  // Get the ticker properties
  const ticker = MAG7_TICKERS.find((t) => t.ticker === dataKey)
  const properties = MAGNIFICENT_SEVEN_LINE_GRADIENT_CONSTANTS.find(
    (obj) => obj.id === ticker?.id
  )

  if (!properties) return <svg />

  // Get the percentage value at this point
  const percentageValue = data[index][dataKey]
  if (percentageValue === null || percentageValue === undefined) return <svg />

  const formattedChange =
    percentageValue >= 0
      ? `+${percentageValue.toFixed(1)}%`
      : `${percentageValue.toFixed(1)}%`

  return (
    <g>
      <foreignObject
        x={x + 5} // Offset to the right of the line end
        y={y - 9} // Center vertically on the line
        width={50}
        height={18}
      >
        <div className='relative'>
          <div className='absolute left-0 flex items-center'>
            <div
              className='h-2 w-2'
              style={{
                clipPath: 'polygon(0 50%, 100% 0, 100% 100%)',
                backgroundColor: properties.stroke,
              }}
            />
            <div
              className='rounded-[1px] px-1 py-0.5 text-center text-[10px] font-medium leading-[10px] text-white'
              style={{ backgroundColor: properties.stroke }}
            >
              {formattedChange}
            </div>
          </div>
        </div>
      </foreignObject>
    </g>
  )
}

const MagnificentSevenChart = () => {
  const [activeDataKey, setActiveDataKey] = useState<string>('')
  const [tickers, setTickers] =
    useState<{ id: number; ticker: string }[]>(MAG7_TICKERS)
  const [selectedTimeFrame, setSelectedTimeFrame] = useState<string>(
    TIMEFRAME[0].value
  )
  const {
    data: polygonData,
    isLoading,
    isError,
  } = useMarketPrices(selectedTimeFrame, MAG7_TICKER_SYMBOLS)

  // Memoize the transformed data
  const data = useMemo(() => {
    if (!polygonData || tickers.length === 0) return []

    if (selectedTimeFrame === '1D') {
      const firstTicker = tickers[0]?.ticker
      const results = polygonData[firstTicker]?.results || []
      if (results.length === 0) return []

      // Use the actual trading day from the data, not today's date
      const firstDataPoint = results[0]
      const tradingDayDate = new Date(firstDataPoint.t)
      const ny = new Date(
        tradingDayDate.toLocaleString('en-US', { timeZone: 'America/New_York' })
      )

      // Set times for the actual trading day
      const tradingDay = new Date(ny)
      tradingDay.setHours(9, 30, 0, 0)
      const t930 = tradingDay.getTime()
      tradingDay.setHours(16, 0, 0, 0)
      const t1600 = tradingDay.getTime()

      // Find the first and last available value for each ticker
      const dummy930: Record<string, any> = { time: t930 }
      const dummy1600: Record<string, any> = { time: t1600 }

      tickers.forEach((ticker) => {
        const tickerResults = polygonData[ticker.ticker]?.results || []
        const open = tickerResults[0]?.o ?? null
        const last = tickerResults[tickerResults.length - 1]?.c ?? open
        if (open !== null) {
          dummy930[ticker.ticker] = 0
          dummy930[`${ticker.ticker}_close`] = open
          dummy1600[ticker.ticker] = ((last - open) / open) * 100
          dummy1600[`${ticker.ticker}_close`] = last
        } else {
          dummy930[ticker.ticker] = null
          dummy930[`${ticker.ticker}_close`] = null
          dummy1600[ticker.ticker] = null
          dummy1600[`${ticker.ticker}_close`] = null
        }
      })

      // Build the rest of the data
      const data: Record<string, any>[] = []
      for (let i = 0; i < results.length; i++) {
        const point: Record<string, any> = { time: results[i].t }
        tickers.forEach((ticker) => {
          const tickerResults = polygonData[ticker.ticker]?.results || []
          const open = tickerResults[0]?.o ?? null
          const close = tickerResults[i]?.c ?? null
          if (open && close) {
            point[ticker.ticker] = ((close - open) / open) * 100
            point[`${ticker.ticker}_close`] = close
          } else {
            point[ticker.ticker] = null
            point[`${ticker.ticker}_close`] = null
          }
        })
        data.push(point)
      }

      // Filter data points between 9:30 and 16:00 using actual trading day times
      const filtered = data.filter(
        (point) => point.time >= t930 && point.time <= t1600
      )

      // Return the combined data points
      return [dummy930, ...filtered, dummy1600].sort((a, b) => a.time - b.time)
    }

    // Default transformation for other timeframes
    const data: Record<string, any>[] = []
    const firstTicker = tickers[0]?.ticker
    const results = polygonData[firstTicker]?.results || []

    // Add initial point for label positioning
    if (results.length > 0) {
      const initialPoint: Record<string, any> = {
        time:
          moment.tz(results[0].t, 'America/New_York').startOf('day').valueOf() -
          86400000, // One day before
      }

      tickers.forEach((ticker) => {
        const tickerResults = polygonData[ticker.ticker]?.results || []
        initialPoint[ticker.ticker] = 0
        initialPoint[`${ticker.ticker}_close`] = tickerResults[0]?.c ?? null
      })

      data.push(initialPoint)
    }

    for (let i = 0; i < results.length; i++) {
      const point: Record<string, any> = { time: results[i].t }
      tickers.forEach((ticker) => {
        const tickerResults = polygonData[ticker.ticker]?.results || []
        const firstClose = tickerResults[0]?.c ?? null
        const currentClose = tickerResults[i]?.c ?? null
        if (firstClose && currentClose) {
          point[ticker.ticker] =
            ((currentClose - firstClose) / firstClose) * 100
          point[`${ticker.ticker}_close`] = currentClose
        } else {
          point[ticker.ticker] = null
          point[`${ticker.ticker}_close`] = null
        }
      })
      data.push(point)
    }

    return data
  }, [polygonData, tickers, selectedTimeFrame])

  // Calculate Y-axis configuration based on data
  const yAxisConfig = useMemo(() => {
    if (!data || data.length === 0) {
      return {
        domain: [-2, 2],
        gridLines: [-2, -1, 0, 1, 2],
      }
    }

    // Find min and max values across all tickers
    let min = Infinity
    let max = -Infinity
    data.forEach((point) => {
      tickers.forEach((ticker) => {
        const value = point[ticker.ticker]
        if (value !== null && value !== undefined) {
          min = Math.min(min, value)
          max = Math.max(max, value)
        }
      })
    })

    // Add padding to the domain
    const padding = (max - min) * 0.1
    const domainMin = Math.floor(min - padding)
    const domainMax = Math.ceil(max + padding)

    // Generate grid lines
    const step = Math.ceil((domainMax - domainMin) / 4)
    const gridLines = []
    for (let i = domainMin; i <= domainMax; i += step) {
      gridLines.push(i)
    }

    return {
      domain: [domainMin, domainMax],
      gridLines,
    }
  }, [data, tickers])

  const CustomTick = ({ x, y, payload, index }: any) => {
    if (selectedTimeFrame !== '1D') {
      const date = moment.tz(payload.value, 'America/New_York').format('MMM DD')
      return (
        <g transform={`translate(${x},${y})`}>
          <text x={0} y={0} dy={16} textAnchor='middle' fill='#FFFFFF'>
            {date}
          </text>
        </g>
      )
    }

    // For 1D timeframe, we only want to show 3 specific times
    const fixedTimes = ['09:30', '12:45', '16:00']
    const time = fixedTimes[index]

    if (!time) return null

    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={0}
          y={0}
          dy={16}
          textAnchor='middle'
          fill='#FFFFFF'
          style={{ fontSize: '12px' }}
        >
          {time}
        </text>
        <circle cx={0} cy={8} r={3} fill='#FFFFFF' />
      </g>
    )
  }

  // Fixed ticks for 1D timeframe
  const xTicks = useMemo(() => {
    if (selectedTimeFrame !== '1D' || !polygonData) return undefined

    const firstTicker = Object.keys(polygonData)[0]
    if (!firstTicker || !polygonData[firstTicker]?.results?.length)
      return undefined

    // Use the actual trading day from the data
    const firstDataPoint = polygonData[firstTicker].results[0]
    const tradingDayDate = new Date(firstDataPoint.t)
    const ny = new Date(
      tradingDayDate.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )

    // Create timestamps for our fixed points on the actual trading day
    const ticks = []
    const tradingDay = new Date(ny)

    // 9:30
    tradingDay.setHours(9, 30, 0, 0)
    ticks.push(tradingDay.getTime())

    // 12:45
    tradingDay.setHours(12, 45, 0, 0)
    ticks.push(tradingDay.getTime())

    // 16:00
    tradingDay.setHours(16, 0, 0, 0)
    ticks.push(tradingDay.getTime())

    return ticks
  }, [selectedTimeFrame, polygonData])

  // Memoize the chart lines
  const chartLines = useMemo(() => {
    return tickers.map((ticker: any, index: number) => {
      const properties = MAGNIFICENT_SEVEN_LINE_GRADIENT_CONSTANTS.find(
        (obj) => obj.id === ticker.id
      )
      return (
        <Line
          key={`${ticker.ticker}-${index}`}
          type='linear'
          height={200}
          dataKey={ticker.ticker}
          stroke={`url(#${properties?.lineColor})`}
          dot={
            <CustomDot
              stroke={`${properties?.stroke}`}
              data={data}
              stroke2={`${properties?.stroke2}`}
              ticker={ticker.ticker}
              activeDataKey={activeDataKey}
            />
          }
          strokeWidth={1.5}
          opacity={
            activeDataKey === ''
              ? 1
              : activeDataKey === ticker.ticker
                ? 1
                : 0.25
          }
          label={(props: any) => (
            <CustomRightLabel {...props} dataKey={ticker.ticker} data={data} />
          )}
        />
      )
    })
  }, [tickers, data, activeDataKey])

  const handleTimeFrame = (value: string) => {
    setSelectedTimeFrame(value)
  }

  //   const handleMouseEnter = (dataKey: string) => {
  //     setActiveDataKey(dataKey)
  //   }

  const handleMouseLeave = () => {
    setActiveDataKey('')
  }

  const handleTickerLines = (ticker: { id: number; ticker: string }) => {
    let newLines: any = []
    if (tickers.includes(ticker)) {
      newLines = tickers.filter((line) => line !== ticker)
    } else if (!tickers.includes(ticker)) {
      newLines = [...tickers, ticker]
    }
    setTickers(newLines)
  }

  const CustomTooltip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      const date = new Date(label)
      const showTime = ['1D', '5D', '1M', '3M'].includes(selectedTimeFrame)
      const formattedDate = showTime
        ? moment(date).tz('America/New_York').format('MMM DD, YYYY h:mm A z')
        : moment(date).tz('America/New_York').format('MMM DD, YYYY')

      return (
        <div className='rounded-lg bg-[#1C1C28] p-3 text-xs text-white shadow-lg'>
          <p className='mb-2 font-medium'>{formattedDate}</p>
          <div className='flex flex-col gap-2'>
            {payload
              .filter((item: any) => !item.dataKey.includes('_close'))
              .map((item: any, index: number) => {
                if (item.value === null) return null
                const properties =
                  MAGNIFICENT_SEVEN_LINE_GRADIENT_CONSTANTS.find(
                    (obj) => obj.id === tickers[index]?.id
                  )
                const tickerValue = item.payload[`${item.dataKey}_close`]
                return (
                  <div
                    key={index}
                    className='flex items-center justify-between gap-4'
                  >
                    <div className='flex items-center gap-2'>
                      <div
                        className='h-2 w-2 rounded-full'
                        style={{
                          backgroundColor: properties?.stroke,
                        }}
                      />
                      <span className='font-medium'>{item.dataKey}</span>
                      <span
                        className={`font-medium ${
                          item.value >= 0 ? 'text-[#16C784]' : 'text-[#FF2E2E]'
                        }`}
                      >
                        {item.value >= 0
                          ? `+${item.value.toFixed(2)}%`
                          : `${item.value.toFixed(2)}%`}
                      </span>
                    </div>
                    <span className='font-medium text-gray-300'>
                      $
                      {typeof tickerValue === 'number'
                        ? tickerValue.toFixed(2)
                        : 'N/A'}
                    </span>
                  </div>
                )
              })}
          </div>
        </div>
      )
    }
    return null
  }

  return (
    <div className='flex h-full flex-col'>
      <h1 className='mb-2 text-start text-base'>
        <span className='font-normal'>Magnificent Seven</span>
      </h1>
      <div className='min-h-0 flex-1'>
        {isLoading ? (
          <div className='flex h-full items-center justify-center'>
            <Loader />
          </div>
        ) : isError ? (
          <div></div>
        ) : (
          <ResponsiveContainer width='100%' height='100%'>
            <LineChart
              data={data}
              margin={{
                top: 10,
                right: 65,
                bottom: 10,
              }}
              onMouseLeave={handleMouseLeave}
            >
              <defs>
                <linearGradient id='nvdaGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#45CA89' />
                  <stop offset='100%' stopColor='#45CA89' />
                </linearGradient>
                <linearGradient id='metaGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#0A70E3' />
                  <stop offset='100%' stopColor='#0A70E3' />
                </linearGradient>
                <linearGradient id='msftGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#87D1FF' />
                  <stop offset='100%' stopColor='#87D1FF' />
                </linearGradient>
                <linearGradient id='aaplGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#DD9DF7' />
                  <stop offset='100%' stopColor='#DD9DF7' />
                </linearGradient>
                <linearGradient id='googlGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#FBC664' />
                  <stop offset='100%' stopColor='#FBC664' />
                </linearGradient>
                <linearGradient id='amznGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#E76D3D' />
                  <stop offset='100%' stopColor='#E76D3D' />
                </linearGradient>
                <linearGradient id='tslaGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='#DB3C3B' />
                  <stop offset='100%' stopColor='#DB3C3B' />
                </linearGradient>
              </defs>
              <XAxis
                dataKey='time'
                tickLine={false}
                axisLine={false}
                tick={<CustomTick />}
                ticks={selectedTimeFrame === '1D' ? xTicks : undefined}
                interval={selectedTimeFrame === '1D' ? 0 : 'preserveStartEnd'}
                domain={
                  selectedTimeFrame === '1D'
                    ? ['dataMin', 'dataMax']
                    : undefined
                }
                height={1}
                hide={true}
              />
              <YAxis
                type='number'
                allowDataOverflow
                domain={yAxisConfig.domain}
                tick={{
                  fontSize: 12,
                  fill: '#8c9097',
                }}
                axisLine={false}
                tickLine={false}
                width={35}
                tickCount={6}
                tickFormatter={(value) => `${value.toFixed(0)}%`}
              />

              {/* Dotted grid lines */}
              {yAxisConfig.gridLines.map((value, index) => (
                <ReferenceLine
                  key={index}
                  y={value}
                  stroke='#8c9097'
                  strokeDasharray='3 3'
                  strokeOpacity={0.3}
                />
              ))}

              <Tooltip content={<CustomTooltip />} cursor={false} />
              {selectedTimeFrame === '1D' && (
                <Line
                  type='monotone'
                  dataKey='none'
                  stroke='#FFFFFF'
                  strokeWidth={1}
                  dot={false}
                  style={{ opacity: 0.3 }}
                  name='connector'
                />
              )}
              {chartLines}
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
      <div className='mt-2 flex items-center justify-between'>
        <div className='flex gap-4 text-sm'>
          {MAG7_TICKERS.map((ticker, index) => {
            const properties = MAGNIFICENT_SEVEN_LINE_GRADIENT_CONSTANTS.find(
              (obj) => obj.id === ticker.id
            )

            const isInTickersArray = tickers.some((t) => t.id === ticker.id)

            return (
              <div key={index}>
                <span
                  className='inline-block cursor-pointer'
                  onClick={() => handleTickerLines(ticker)}
                >
                  {ticker.ticker.toUpperCase()}
                </span>

                {isInTickersArray && (
                  <div
                    className={`h-[3px] w-full rounded-b-[1px] ${properties?.className}`}
                  ></div>
                )}
              </div>
            )
          })}
        </div>
        <div className='font-sm flex gap-4'>
          {TIMEFRAME.filter((timeframe) => timeframe.value !== '5Y').map(
            (timeframe, index) => (
              <div
                key={index}
                onClick={() => handleTimeFrame(timeframe.value)}
                className='cursor-pointer'
              >
                <span className='inline-block text-xs'>{timeframe.label}</span>
                {timeframe.value === selectedTimeFrame && (
                  <div className='h-[3px] w-full rounded-b-[1px] bg-[#FFFFFF]'></div>
                )}
              </div>
            )
          )}
        </div>
      </div>
    </div>
  )
}

export default MagnificentSevenChart
