import { useTickerPrices } from '@/hooks/use-ticker-prices'
import { TIMEFRAME } from '@/constants'
import moment from 'moment-timezone'
import {
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import Loader from '@/components/loader'
import { useState } from 'react'

interface TickerDashLineChartProps {
  ticker: string
}

const TickerDashLineChart: React.FC<TickerDashLineChartProps> = ({
  ticker,
}) => {
  const [selectedTimeFrame, setSelectedTimeFrame] = useState<string>(
    TIMEFRAME[0].value
  )
  const {
    data: polygonData,
    isLoading,
    isError,
  } = useTickerPrices(selectedTimeFrame, ticker)

  if (isLoading) {
    return (
      <div className='h-[323px] w-full pl-5 pr-2'>
        <Loader />
      </div>
    )
  }

  if (isError || !polygonData || !polygonData[ticker]?.results) {
    return (
      <div className='flex h-[323px] w-full items-center justify-center pl-5 pr-2'>
        <p className='text-gray-400'>No data available</p>
      </div>
    )
  }

  const data = polygonData[ticker].results.map((item: any) => ({
    timestamp: item.t,
    price: item.c,
  }))

  const CustomDot = ({ cx, cy, stroke, index, data }: any) => {
    if (index !== 0 && index !== data.length - 1) return null
    return <circle cx={cx} cy={cy} r={3.5} stroke={stroke} fill='#1A1F24' />
  }

  const handleTimeFrame = (value: string) => {
    setSelectedTimeFrame(value)
  }

  return (
    <div className='h-[323px] w-full pl-5 pr-2'>
      <h1 className='text-start text-base'>
        <span className='font-normal'>{`${ticker} Price`}</span>
      </h1>
      <ResponsiveContainer width='100%' height='100%' className='mt-3'>
        <LineChart
          data={data}
          margin={{
            top: 12,
            right: -37,
            bottom: 20,
            left: 10,
          }}
        >
          <XAxis
            dataKey='timestamp'
            tickLine={false}
            axisLine={false}
            tickFormatter={(timestamp) =>
              moment
                .tz(timestamp, 'America/New_York')
                .format(selectedTimeFrame === '1D' ? 'HH:mm' : 'MM/DD')
            }
            fontSize={12}
            stroke='#FFFFFF'
            tickMargin={10}
            interval='preserveStartEnd'
          />
          <YAxis
            tickLine={false}
            axisLine={false}
            fontSize={9}
            tickMargin={-4}
            domain={['dataMin', 'dataMax']}
            stroke='#FFFFFF'
            interval={0}
            orientation='right'
            tickFormatter={(value) => `${value.toFixed(2)}`}
          />
          <Tooltip
            content={({ active, payload }) => {
              if (active && payload && payload.length) {
                const data = payload[0].payload
                return (
                  <div className='rounded border border-gray-700 bg-[#1A1F24] p-2'>
                    <p className='text-xs text-gray-400'>
                      {moment
                        .tz(data.timestamp, 'America/New_York')
                        .format(
                          selectedTimeFrame === '1D' ? 'HH:mm' : 'MM/DD/YY'
                        )}
                    </p>
                    <p className='text-sm font-medium text-white'>
                      ${data.price.toFixed(2)}
                    </p>
                  </div>
                )
              }
              return null
            }}
          />
          <Line
            type='monotone'
            dataKey='price'
            stroke='#28BF94'
            dot={<CustomDot />}
            strokeWidth={1.5}
          />
        </LineChart>
      </ResponsiveContainer>
      <div className='flex items-center justify-end pt-1'>
        <div className='flex gap-4 text-base font-medium'>
          {TIMEFRAME.map((timeframe, index) => (
            <div
              key={index}
              onClick={() => handleTimeFrame(timeframe.value)}
              className='cursor-pointer'
            >
              <span className='inline-block'>{timeframe.label}</span>
              {timeframe.value === selectedTimeFrame && (
                <div className='h-[3px] w-full rounded-b-[1px] bg-[#FFFFFF]' />
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default TickerDashLineChart
