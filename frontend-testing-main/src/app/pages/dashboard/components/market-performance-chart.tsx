import Loader from '@/components/loader'
import { LINE_GRADIENT_CONSTANTS, TIMEFRAME } from '@/constants'
// import { useDashboard } from '@/hooks/use-dashboard'
// import { useMarketDashboardQueryKeys } from '@/hooks/use-query-keys'
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
} from 'recharts'
import { useMarketPrices } from '@/hooks/use-market-prices'

/**
 * Component for rendering a line chart showing market performance data.
 *
 * This component takes in an array of objects, each representing a different
 * market index. Each object should contain the following properties:
 *   - `name`: the name of the market index
 *   - `data`: an array of objects representing the daily data for the index
 *     - each object should contain the following properties:
 *       - `date`: the date of the data
 *       - `value`: the value of the index on that date
 *
 * The component will render a line chart with the data from each index
 * represented by a different line. The chart will also include a legend
 * showing the name of each index and the color of its line.
 *
 * The component will also render a set of buttons above the chart allowing the
 * user to toggle the visibility of each index on the chart.
 *
 * The component will also render a set of options below the chart allowing the
 * user to select the time period for which the data is displayed.
 */

// Move CustomDot and CustomLabel above the main component
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

// Replace CustomLabel with CustomRightLabel
const CustomRightLabel = (props: any) => {
  const { x, y, dataKey, index, data, tickers } = props
  // Only show label at the last data point (end of line)
  if (index !== data.length - 1) return <svg />

  // Get the ticker properties
  const ticker = tickers.find((t: { ticker: string }) => t.ticker === dataKey)
  const properties = LINE_GRADIENT_CONSTANTS.find(
    (obj) => obj.id === ticker?.id
  )

  if (!properties) return <svg />

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
              {dataKey}
            </div>
          </div>
        </div>
      </foreignObject>
    </g>
  )
}

const MarketPerformanceChart = () => {
  const [activeDataKey, setActiveDataKey] = useState<string>('')
  const mainTickers = [
    { id: 1, ticker: 'SPY' },
    { id: 2, ticker: 'QQQ' },
    { id: 3, ticker: 'IWM' },
    { id: 4, ticker: 'DIA' },
    { id: 5, ticker: 'VTI' },
  ]
  const [tickers, setTickers] = useState<{ id: number; ticker: string }[]>([
    { id: 1, ticker: 'SPY' },
    { id: 2, ticker: 'QQQ' },
    { id: 3, ticker: 'IWM' },
    { id: 4, ticker: 'DIA' },
    { id: 5, ticker: 'VTI' },
  ])
  // const [maxIndex, setMaxIndex] = useState(0)
  const [selectedTimeFrame, setSelectedTimeFrame] = useState<string>(
    TIMEFRAME[0].value
  )
  // const queryKeys = useMarketDashboardQueryKeys()
  const {
    data: polygonData,
    isLoading,
    isError,
  } = useMarketPrices(selectedTimeFrame)

  // const getStaticTimes = () => {
  //   // Get today's date in NY time
  //   const now = new Date();
  //   const ny = new Date(now.toLocaleString('en-US', { timeZone: 'America/New_York' }));
  //   ny.setHours(9, 30, 0, 0);
  //   const t930 = ny.getTime();
  //   ny.setHours(12, 0, 0, 0);
  //   const t1200 = ny.getTime();
  //   ny.setHours(16, 0, 0, 0);
  //   const t1600 = ny.getTime();
  //   return { t930, t1200, t1600 };
  // };

  const getTickerOffset = (ticker: string, timeframe: string): number => {
    type ScaleType = {
      [key: string]: number
    }

    type TimeframeScales = {
      [key: string]: ScaleType
    }

    const scales: TimeframeScales = {
      '1D': {
        SPY: 0.3,
        QQQ: 0.2,
        IWM: 0.1,
        DIA: 0,
        VTI: -0.1,
      },
      '5D': {
        SPY: 1.0,
        QQQ: 0.75,
        IWM: 0.5,
        DIA: 0.25,
        VTI: 0,
      },
      '1M': {
        SPY: 2.0,
        QQQ: 1.5,
        IWM: 1.0,
        DIA: 0.5,
        VTI: 0,
      },
      '3M': {
        SPY: 3.0,
        QQQ: 2.25,
        IWM: 1.5,
        DIA: 0.75,
        VTI: 0,
      },
      '6M': {
        SPY: 4.0,
        QQQ: 3.0,
        IWM: 2.0,
        DIA: 1.0,
        VTI: 0,
      },
      YTD: {
        SPY: 4.0,
        QQQ: 3.0,
        IWM: 2.0,
        DIA: 1.0,
        VTI: 0,
      },
      '1Y': {
        SPY: 5.0,
        QQQ: 3.75,
        IWM: 2.5,
        DIA: 1.25,
        VTI: 0,
      },
      '5Y': {
        SPY: 6.0,
        QQQ: 3.0,
        IWM: 1.0,
        DIA: -2.0,
        VTI: -4.0,
      },
    }

    return scales[timeframe]?.[ticker] ?? 0
  }

  // Add type definition for data point
  // type DataPoint = {
  //   time: number;
  //   [key: string]: number | null;
  // };

  // Memoize the transformed data
  const data = useMemo(() => {
    if (!polygonData || tickers.length === 0) return []

    // Processing data for timeframe
    // Polygon data keys
    // First ticker data count

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
          // Set market open point
          dummy930[ticker.ticker] = 0
          dummy930[`${ticker.ticker}_close`] = open

          // Set end of day point
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

      // Filter out any points outside our time range and combine with dummy points
      const filtered = data.filter(
        (point) => point.time >= t930 && point.time <= t1600
      )
      const allData = [dummy930, ...filtered, dummy1600]

      // Sort by time to ensure proper ordering
      return allData.sort((a, b) => a.time - b.time)
    }

    // For other timeframes
    const firstTicker = tickers[0]?.ticker
    const results = polygonData[firstTicker]?.results || []
    const data: Record<string, any>[] = []

    // Processing timeframe with data points

    // Add initial point for label positioning
    if (results.length > 0) {
      const initialPoint: Record<string, any> = {
        time:
          moment.tz(results[0].t, 'America/New_York').startOf('day').valueOf() -
          86400000, // One day before
      }

      tickers.forEach((ticker) => {
        const tickerResults = polygonData[ticker.ticker]?.results || []
        const firstValue = tickerResults[0]?.c ?? tickerResults[0]?.o ?? null // Use close first, then open as fallback
        if (firstValue !== null) {
          const offset = getTickerOffset(ticker.ticker, selectedTimeFrame)
          initialPoint[ticker.ticker] = offset
          initialPoint[`${ticker.ticker}_close`] =
            firstValue * (1 + offset / 100)
        } else {
          initialPoint[ticker.ticker] = null
          initialPoint[`${ticker.ticker}_close`] = null
        }
      })
      data.push(initialPoint)
    }

    // For each data point in the results
    for (let i = 0; i < results.length; i++) {
      const point: Record<string, any> = {
        time: results[i].t, // Use raw timestamp for all timeframes except 1D
      }

      // For each ticker, calculate the percentage change from the first value
      tickers.forEach((ticker) => {
        const tickerResults = polygonData[ticker.ticker]?.results || []
        const firstValue = tickerResults[0]?.c ?? tickerResults[0]?.o ?? null // Use close first, then open as fallback
        const currentValue = tickerResults[i]?.c ?? null

        if (firstValue !== null && currentValue !== null) {
          point[ticker.ticker] =
            ((currentValue - firstValue) / firstValue) * 100
          point[`${ticker.ticker}_close`] = currentValue
        } else {
          point[ticker.ticker] = null
          point[`${ticker.ticker}_close`] = null
        }
      })

      data.push(point)
    }

    // Generated data points for chart
    // Sample data point

    return data
  }, [polygonData, tickers, selectedTimeFrame])

  const CustomTick = ({ x, y, payload, index }: any) => {
    if (selectedTimeFrame !== '1D') {
      let dateFormat = 'MMM DD'

      // Use different date formats based on timeframe
      if (selectedTimeFrame === '5Y' || selectedTimeFrame === '1Y') {
        dateFormat = 'YYYY'
      } else if (selectedTimeFrame === '6M' || selectedTimeFrame === '3M') {
        dateFormat = 'MMM YYYY'
      }

      const date = moment
        .tz(payload.value, 'America/New_York')
        .format(dateFormat)
      return (
        <g transform={`translate(${x},${y})`}>
          <text x={0} y={0} dy={16} textAnchor='middle' fill='#FFFFFF'>
            {date}
          </text>
        </g>
      )
    }

    // For 1D timeframe, we only want to show specific times
    const fixedTimes = ['09:15', '09:30', '12:45', '16:00']
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

    // 9:15
    tradingDay.setHours(9, 15, 0, 0)
    ticks.push(tradingDay.getTime())

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

  // Memoize the filtered data
  const filteredData = useMemo(() => {
    if (selectedTimeFrame !== '1D' || data.length === 0) return data

    if (!polygonData) return data

    const firstTicker = Object.keys(polygonData)[0]
    if (!firstTicker || !polygonData[firstTicker]?.results?.length) return data

    // Use the actual trading day from the data
    const firstDataPoint = polygonData[firstTicker].results[0]
    const tradingDayDate = new Date(firstDataPoint.t)
    const ny = new Date(
      tradingDayDate.toLocaleString('en-US', { timeZone: 'America/New_York' })
    )

    const tradingDay = new Date(ny)
    tradingDay.setHours(9, 15, 0, 0)
    const t915 = tradingDay.getTime()
    tradingDay.setHours(16, 0, 0, 0)
    const t1600 = tradingDay.getTime()

    // Include data from 9:15 AM
    return data.filter((point) => point.time >= t915 && point.time <= t1600)
  }, [data, selectedTimeFrame, polygonData])

  // Memoize the chart lines
  const chartLines = useMemo(() => {
    return tickers.map((ticker: any, index: number) => {
      const properties = LINE_GRADIENT_CONSTANTS.find(
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
            <CustomRightLabel
              {...props}
              dataKey={ticker.ticker}
              data={data}
              tickers={tickers}
            />
          )}
        />
      )
    })
  }, [tickers, data, activeDataKey])

  const handleTimeFrame = (value: string) => {
    setSelectedTimeFrame(value)
  }

  // const handleMouseEnter = (dataKey: string) => {
  //   setActiveDataKey(dataKey)
  // }

  const handleMouseLeave = () => {
    setActiveDataKey('')
  }

  // Update the handleTickerLines function
  const handleTickerLines = (ticker: { id: number; ticker: string }) => {
    let newLines = []
    const isInTickersArray = tickers.some((t) => t.id === ticker.id)

    if (isInTickersArray) {
      newLines = tickers.filter((t) => t.id !== ticker.id)
    } else {
      newLines = [...tickers, ticker]
    }
    setTickers(newLines)
  }

  // const CustomizedTick = ({ ...props }) => {
  //   const { x, y, backgroundColor } = props
  //   return tickers.map((item, index) => {
  //     const properties = LINE_GRADIENT_CONSTANTS.find(
  //       (obj) => obj.id === item.id
  //     )
  //     return (
  //       <svg transform={`translate(${x},${y})`}>
  //         <foreignObject
  //           x={0}
  //           y={index * 22 + 60}
  //           width={50}
  //           height={20}
  //           opacity={
  //             activeDataKey === ''
  //               ? 1
  //               : activeDataKey === item.ticker
  //                 ? 1
  //                 : 0.25
  //           }
  //         >
  //           <div className='relative'>
  //             <div
  //               className='flex absolute left-0 items-center w-11 font-medium'
  //               style={{ backgroundColor: properties?.label }}
  //             >
  //               <div
  //                 className='rounded-[1px] px-2 py-0.5 text-center text-xs text-white'
  //                 onMouseEnter={() => handleMouseEnter(item.ticker)}
  //                 onMouseLeave={handleMouseLeave}
  //               >
  //                 {item.ticker.toUpperCase()}
  //               </div>
  //               <div
  //                 className='w-2 h-2 rotate-180'
  //                 style={{
  //                   clipPath: 'polygon(0 50%, 100% 0, 100% 100%)',
  //                   backgroundColor,
  //                 }}
  //               />
  //             </div>
  //           </div>
  //         </foreignObject>
  //       </svg>
  //     )
  //   })
  // }

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
                const properties = LINE_GRADIENT_CONSTANTS.find(
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

  const xAxisDomain = useMemo(() => {
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

    const tradingDay = new Date(ny)
    tradingDay.setHours(9, 15, 0, 0)
    const t915 = tradingDay.getTime()
    tradingDay.setHours(16, 0, 0, 0)
    const t1600 = tradingDay.getTime()

    return [t915, t1600]
  }, [selectedTimeFrame, polygonData])

  // Determine market sentiment based on VTI performance
  const getMarketSentiment = () => {
    if (
      !polygonData ||
      !polygonData.VTI ||
      !polygonData.VTI.results ||
      polygonData.VTI.results.length === 0
    ) {
      return { text: 'Neutral', color: '#8c9097' }
    }

    const vtiResults = polygonData.VTI.results

    // For 1D timeframe, compare current price to previous day's close
    if (selectedTimeFrame === '1D') {
      // Use the most recent data point
      const currentData = vtiResults[vtiResults.length - 1]
      const previousClose = currentData.vw || currentData.c // Use volume weighted average price or close
      const openPrice = vtiResults[0].o

      if (previousClose && openPrice) {
        const changePercent = ((previousClose - openPrice) / openPrice) * 100

        if (changePercent > 0.25) {
          return { text: 'Bullish', color: '#28BF94' }
        } else if (changePercent < -0.25) {
          return { text: 'Bearish', color: '#CD4B38' }
        } else {
          return { text: 'Neutral', color: '#8c9097' }
        }
      }
    } else {
      // For other timeframes, compare starting point to current point
      if (vtiResults.length >= 1) {
        const startingData = vtiResults[0] // First data point (starting point)
        const currentData = vtiResults[vtiResults.length - 1] // Last data point (current)

        if (startingData.c && currentData.c) {
          const changePercent =
            ((currentData.c - startingData.c) / startingData.c) * 100

          if (changePercent > 0.25) {
            return { text: 'Bullish', color: '#28BF94' }
          } else if (changePercent < -0.25) {
            return { text: 'Bearish', color: '#CD4B38' }
          } else {
            return { text: 'Neutral', color: '#8c9097' }
          }
        }
      }
    }

    return { text: 'Neutral', color: '#8c9097' }
  }

  const marketSentiment = getMarketSentiment()

  return (
    <div className='h-[404px] w-full'>
      <h1 className='px-4 text-start text-sm'>
        <span className='font-normal'>Market is</span>{' '}
        <span className='font-medium' style={{ color: marketSentiment.color }}>
          {marketSentiment.text}
        </span>
      </h1>
      <div className='mt-4 h-[321px]'>
        {isLoading ? (
          <div className='flex h-full items-center justify-center'>
            <Loader />
          </div>
        ) : isError ? (
          <div></div>
        ) : (
          <ResponsiveContainer width='100%' height='100%'>
            <LineChart
              data={filteredData}
              margin={{
                top: 10,
                right: 65,
                bottom: 10,
              }}
              onMouseLeave={handleMouseLeave}
            >
              <defs>
                <linearGradient id='fourthGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='70%' stopColor='hsla(203, 80%, 17%, 1)' />
                  <stop offset='100%' stopColor='hsla(188, 42%, 43%, 1)' />
                </linearGradient>
                <linearGradient id='firstGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='45%' stopColor='hsla(117, 31%, 34%, 1)' />
                  <stop offset='90%' stopColor='hsla(63, 65%, 42%, 1)' />
                  <stop offset='100%' stopColor='hsla(53, 100%, 54%, 1)' />
                </linearGradient>
                <linearGradient id='secondGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='30%' stopColor='hsla(186, 41%, 45%, 1)' />
                  <stop offset='90%' stopColor='hsla(42, 49%, 93%, 1)' />
                </linearGradient>
                <linearGradient id='thirdGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='60%' stopColor='hsla(0, 79%, 38%, 1)' />
                  <stop offset='100%' stopColor='hsla(0, 95%, 60%, 1)' />
                </linearGradient>
                <linearGradient id='fifthGradient' x1='0' y1='0' x2='1' y2='0'>
                  <stop offset='50%' stopColor='hsla(18, 90%, 49%, 1)' />
                  <stop offset='100%' stopColor='hsla(18, 100%, 67%, 1)' />
                </linearGradient>
              </defs>
              <XAxis
                dataKey='time'
                tickLine={false}
                axisLine={false}
                tick={<CustomTick />}
                ticks={selectedTimeFrame === '1D' ? xTicks : undefined}
                interval={selectedTimeFrame === '1D' ? 0 : 'preserveStartEnd'}
                domain={selectedTimeFrame === '1D' ? xAxisDomain : undefined}
                height={1}
                hide={selectedTimeFrame === '1D'}
              />
              <YAxis
                type='number'
                allowDataOverflow
                domain={['dataMin', 'dataMax']}
                tick={false}
                axisLine={false}
                tickLine={false}
              />
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
      <div className='flex items-center justify-between pt-2'>
        <div className='flex gap-4 text-xs'>
          {mainTickers.map((ticker, index) => {
            const properties = LINE_GRADIENT_CONSTANTS.find(
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
          {TIMEFRAME.map((timeframe, index) => (
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
          ))}
        </div>
      </div>
    </div>
  )
}

export default MarketPerformanceChart
