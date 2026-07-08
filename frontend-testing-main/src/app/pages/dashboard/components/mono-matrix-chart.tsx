import moment from 'moment-timezone'
import { useState, useEffect } from 'react'
import { marketTideService } from '@/api/market-tide.service'
import type { MarketTideDataPoint } from '@/api/market-tide.service'
import {
  Area,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

interface MonoMatrixChartProps {
  classes?: string
  children: React.ReactNode
}

const MonoMatrixChart = ({
  classes,
  children,
}: MonoMatrixChartProps): JSX.Element => {
  const [data, setData] = useState<MarketTideDataPoint[]>([])
  const [xAxisDomain, setXAxisDomain] = useState<[number, number]>([0, 1])

  // Transform data to separate positive and negative volumes
  const chartData = data.map((item) => ({
    ...item,
    volume_positive: item.net_volume >= 0 ? item.net_volume : 0,
    volume_negative: item.net_volume < 0 ? item.net_volume : 0, // Keep negative value
  }))

  // Fetch market tide data for today
  const fetchData = async () => {
    try {
      const response = await marketTideService.getMarketTide()

      if (response.success && response.data && Array.isArray(response.data)) {
        if (response.data.length > 0) {
          // Get the first data point to determine which day the data is from
          const firstDataTimestamp = response.data[0].timestamp
          const firstDataDate =
            moment(firstDataTimestamp).tz('America/New_York')

          // Get today's date and market open time in EST
          const nowEST = moment().tz('America/New_York')
          const todayMarketOpen = nowEST
            .clone()
            .startOf('day')
            .hour(9)
            .minute(30)

          // Check if data is from today (at or after today's 9:30 AM)
          const isDataFromToday = firstDataDate.isSameOrAfter(todayMarketOpen)

          if (isDataFromToday) {
            // Data is from today - use today's market hours
            const today = moment().tz('America/New_York').startOf('day')
            const marketOpen = today.clone().hour(9).minute(30).valueOf()
            const marketClose = today.clone().hour(16).minute(0).valueOf()
            setXAxisDomain([marketOpen, marketClose])
          } else {
            // Data is from yesterday - show it at full width using actual data range
            const timestamps = response.data.map((d) => d.timestamp)
            const minTime = Math.min(...timestamps)
            const maxTime = Math.max(...timestamps)
            setXAxisDomain([minTime, maxTime])
          }

          setData(response.data)
        }
      }
    } catch (error) {
      // Error fetching market tide
    }
  }

  // Initial fetch and polling every minute
  useEffect(() => {
    fetchData() // Initial fetch

    const interval = setInterval(() => {
      fetchData() // Poll every minute
    }, 60000) // 60000ms = 1 minute

    return () => {
      clearInterval(interval)
    }
  }, [])

  const formatData = (value: number) => {
    if (value === 0) return '0'

    const isNegative = value < 0
    const absValue = Math.abs(value)

    let formattedValue
    if (absValue >= 1000000000) {
      formattedValue = (
        <>
          ${Math.floor(absValue / 1000000000)}
          <span className='text-[#D47FFF]'>B</span>
        </>
      )
    } else if (absValue >= 1000000) {
      formattedValue = (
        <>
          ${Math.floor(absValue / 1000000)}
          <span className='text-[#D47FFF]'>M</span>
        </>
      )
    } else if (absValue >= 1000) {
      formattedValue = (
        <>
          ${Math.floor(absValue / 1000)}
          <span className='text-topBullishNetPremiumPrimaryTransparent'>K</span>
        </>
      )
    } else {
      formattedValue = absValue.toString()
    }

    return isNegative ? <>-{formattedValue}</> : formattedValue
  }

  // Custom right Y-Axis tick formatter with colored K and M
  const CustomRightYAxisTick = ({ x, y, payload }: any) => {
    const value = payload.value
    const absValue = Math.abs(value)
    let number = ''
    let suffix = ''
    let suffixColor = '#FFFFFF'

    if (absValue >= 1000000) {
      number = (value / 1000000).toFixed(1)
      suffix = 'M'
      suffixColor = '#D47FFF'
    } else if (absValue >= 1000) {
      number = (value / 1000).toFixed(0)
      suffix = 'K'
      suffixColor = '#24D438'
    } else {
      // Format 0 with same precision as millions for alignment
      number = '0.0'
      suffix = 'M'
      suffixColor = '#D47FFF'
    }

    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={0}
          y={0}
          dy={4}
          textAnchor='start'
          fill='#FFFFFF'
          fontSize={10}
        >
          {number}
          <tspan fill={suffixColor}>{suffix}</tspan>
        </text>
      </g>
    )
  }

  const CustomToolTip = ({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      // Find the actual data point from chartData
      const dataPoint = chartData.find((d) => d.timestamp === parseInt(label))

      if (!dataPoint) return null

      const spyPrice = dataPoint.spy_close
      const callPremium = dataPoint.call_premium
      const putPremium = dataPoint.put_premium
      const netVolume = dataPoint.net_volume

      return (
        <div className='rounded-md border border-gray-700 bg-[#171d23] p-3 shadow-lg'>
          <p className='mb-2 text-xs text-gray-300'>
            {moment
              .tz(parseInt(label), 'America/New_York')
              .format('MMM DD, YYYY HH:mm')}
          </p>

          {/* SPY Price */}
          {spyPrice !== null && (
            <div className='mb-1 flex items-center justify-between gap-4'>
              <div className='flex items-center gap-2'>
                <div className='h-2 w-2 rounded-full bg-[#F5F7F7]'></div>
                <span className='text-xs font-medium text-white'>
                  SPY PRICE
                </span>
              </div>
              <span className='text-xs font-medium text-white'>
                ${spyPrice.toFixed(2)}
              </span>
            </div>
          )}

          {/* Net Calls */}
          <div className='mb-1 flex items-center justify-between gap-4'>
            <div className='flex items-center gap-2'>
              <div className='h-2 w-2 rounded-full bg-[#28BF94]'></div>
              <span
                className='text-xs font-medium'
                style={{ color: '#28BF94' }}
              >
                NET CALLS
              </span>
            </div>
            <span className='text-xs font-medium text-white'>
              {formatData(callPremium)}
            </span>
          </div>

          {/* Net Puts */}
          <div className='mb-1 flex items-center justify-between gap-4'>
            <div className='flex items-center gap-2'>
              <div className='h-2 w-2 rounded-full bg-[#CD4B38]'></div>
              <span
                className='text-xs font-medium'
                style={{ color: '#CD4B38' }}
              >
                NET PUTS
              </span>
            </div>
            <span className='text-xs font-medium text-white'>
              {putPremium < 0 && <span style={{ color: '#CD4B38' }}>-</span>}
              {formatData(Math.abs(putPremium))}
            </span>
          </div>

          {/* Net Volume */}
          <div className='mb-1 flex items-center justify-between gap-4 last:mb-0'>
            <div className='flex items-center gap-2'>
              <div
                className='h-2 w-2 rounded-full'
                style={{
                  backgroundColor: netVolume >= 0 ? '#0A931A' : '#CD4B38',
                }}
              ></div>
              <span className='text-xs font-medium text-white'>NET VOLUME</span>
            </div>
            <span
              className='text-xs font-medium text-white'
              style={{ color: netVolume >= 0 ? '#0A931A' : '#CD4B38' }}
            >
              {netVolume >= 0 ? '' : '-'}
              {Math.abs(netVolume).toLocaleString()}
            </span>
          </div>
        </div>
      )
    }
    return null
  }

  return (
    <div className={`w-full ${classes}`}>
      <div className='flex items-center justify-between'>
        {children}
        <div className='flex gap-4 px-4 text-sm'>
          <div>
            <span className='inline-block text-xs font-medium text-[#F5F7F7]'>
              SPY
            </span>
            <div className='h-[2px] w-full rounded-[1px] bg-[#F5F7F7]'></div>
          </div>
          <div>
            <span className='inline-block text-xs font-medium text-[#28BF94]'>
              NCP
            </span>
            <div className='h-[2px] w-full rounded-[1px] bg-[#28BF94]'></div>
          </div>
          <div>
            <span className='inline-block text-xs font-medium text-[#CD4B38]'>
              NPP
            </span>
            <div className='h-[2px] w-full rounded-[1px] bg-[#CD4B38]'></div>
          </div>
          <div>
            <span className='inline-block text-xs font-medium text-[#0A931A]'>
              NV
            </span>
            <div className='h-[2px] w-full rounded-[1px] bg-[#0A931A]'></div>
          </div>
        </div>
      </div>
      <div style={{ position: 'relative' }}>
        <ResponsiveContainer width='100%' height={350} className='mt-4'>
          <ComposedChart
            data={chartData}
            margin={{
              top: 15,
              right: 5,
              left: 5,
              bottom: 30,
            }}
          >
            <defs>
              <linearGradient
                id='colorVolumePositive'
                x1='0'
                y1='0'
                x2='0'
                y2='1'
              >
                <stop offset='5%' stopColor='#0A931A' stopOpacity={0.8} />
                <stop offset='95%' stopColor='#0A931A' stopOpacity={0.1} />
              </linearGradient>
              <linearGradient
                id='colorVolumeNegative'
                x1='0'
                y1='0'
                x2='0'
                y2='1'
              >
                <stop offset='5%' stopColor='#CD4B38' stopOpacity={0.8} />
                <stop offset='95%' stopColor='#CD4B38' stopOpacity={0.1} />
              </linearGradient>
            </defs>

            <XAxis
              dataKey='timestamp'
              xAxisId={0}
              axisLine={false}
              tickLine={false}
              tick={false}
              domain={xAxisDomain}
              type='number'
              scale='time'
              height={1}
              hide={true}
            />

            <YAxis
              yAxisId='spy'
              orientation='left'
              domain={['auto', 'auto']}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 10, fill: '#FFFFFF' }}
              label={{
                value: 'SPY Price',
                angle: -90,
                position: 'insideLeft',
                style: { fill: '#FFFFFF', fontSize: 12 },
              }}
            />

            <YAxis
              yAxisId='premium'
              orientation='right'
              domain={[
                (dataMin: number) => Math.min(dataMin, 0),
                (dataMax: number) => Math.max(dataMax, 0),
              ]}
              tickLine={false}
              axisLine={false}
              label={{
                value: 'Net Premiums',
                angle: 90,
                position: 'insideRight',
                style: { fill: '#FFFFFF', fontSize: 12 },
              }}
              tick={<CustomRightYAxisTick />}
            />

            <YAxis
              yAxisId='volume'
              orientation='right'
              domain={[
                (dataMin: number) => dataMin * 1.2,
                (dataMax: number) => dataMax * 1.2,
              ]}
              tickFormatter={(value) => `${(value / 1000).toFixed(0)}K`}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 12, fill: '#FFFFFF' }}
              hide={true}
            />

            <Tooltip content={<CustomToolTip />} cursor={false} />

            <ReferenceLine
              yAxisId='volume'
              y={0}
              stroke='#FFFFFF'
              strokeDasharray='3 3'
              strokeOpacity={0.3}
            />

            <Area
              type='monotone'
              dataKey='volume_positive'
              fill='url(#colorVolumePositive)'
              stroke='none'
              yAxisId='volume'
              isAnimationActive={false}
              stackId='volume'
            />

            <Area
              type='monotone'
              dataKey='volume_negative'
              fill='url(#colorVolumeNegative)'
              stroke='none'
              yAxisId='volume'
              isAnimationActive={false}
              stackId='volume'
            />

            <Line
              type='monotone'
              dataKey='spy_close'
              stroke='#F5F7F7'
              strokeWidth={2}
              dot={false}
              yAxisId='spy'
              isAnimationActive={false}
              connectNulls={false}
            />

            <Line
              type='monotone'
              dataKey='call_premium'
              stroke='#28BF94'
              strokeWidth={2}
              dot={false}
              yAxisId='premium'
              isAnimationActive={false}
              connectNulls={false}
            />

            <Line
              type='monotone'
              dataKey='put_premium'
              stroke='#CD4B38'
              strokeWidth={2}
              dot={false}
              yAxisId='premium'
              isAnimationActive={false}
              connectNulls={false}
            />
          </ComposedChart>
        </ResponsiveContainer>

        {/* Custom X-Axis */}
        <>
          <div
            style={{
              position: 'absolute',
              bottom: '8px',
              left: '84px',
              fontSize: '10px',
              color: '#FFFFFF',
            }}
          >
            09:30
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: '8px',
              left: '50%',
              transform: 'translateX(-50%)',
              fontSize: '10px',
              color: '#FFFFFF',
            }}
          >
            12:45
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: '8px',
              right: '84px',
              fontSize: '10px',
              color: '#FFFFFF',
            }}
          >
            16:00
          </div>
          <div
            style={{
              position: 'absolute',
              bottom: '16px',
              left: 'calc(84px + 45px + 6px)',
              width: 'calc(50% - 84px - 45px - 6px - 33px)',
              height: '1px',
              backgroundColor: '#B0B0B0',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: '15px',
              left: 'calc(84px + 45px + 6px)',
              width: '3px',
              height: '3px',
              borderRadius: '50%',
              backgroundColor: '#B0B0B0',
              border: 'none',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: '15px',
              left: 'calc(50% - 33px)',
              width: '3px',
              height: '3px',
              borderRadius: '50%',
              backgroundColor: '#B0B0B0',
              border: 'none',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: '16px',
              left: 'calc(50% + 30px)',
              width: 'calc(100% - 168px - (50% + 30px) - 39px - 3px + 77px)',
              height: '1px',
              backgroundColor: '#B0B0B0',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: '15px',
              left: 'calc(50% + 30px)',
              width: '3px',
              height: '3px',
              borderRadius: '50%',
              backgroundColor: '#B0B0B0',
              border: 'none',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: '15px',
              left: 'calc(100% - 84px - 45px - 6px)',
              width: '3px',
              height: '3px',
              borderRadius: '50%',
              backgroundColor: '#B0B0B0',
              border: 'none',
            }}
          />
        </>
      </div>
    </div>
  )
}

export default MonoMatrixChart
