import React, { useState, useCallback } from 'react'
import Loader from '@/components/loader'
import { MARKET_PREMIUMS_TICKERS } from '@/constants'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
  ReferenceLine,
  Tooltip,
} from 'recharts'
import { useTickerTide } from '@/hooks/use-ticker-tide'

const MarketPremiums = () => {
  const [selectedTicker, setSelectedTicker] = useState<string>(
    MARKET_PREMIUMS_TICKERS[0].value
  )

  // Fetch ticker tide data for the last 7 weekdays
  const { data: tickerTideData, isLoading } = useTickerTide(selectedTicker, 7)

  // Transform data for chart - keep actual values with signs
  const data = tickerTideData.map((item) => ({
    name: item.displayDate,
    calls: item.net_call_premium,
    puts: item.net_put_premium,
  }))

  const handleTickerSelection = useCallback((value: string) => {
    setSelectedTicker(value)
  }, [])

  const CustomYAxisTick = useCallback(({ x, y, payload }: any) => {
    const value = payload.value
    const absValue = Math.abs(value)
    const sign = value < 0 ? '-' : ''
    let finalValue = '0'

    if (absValue >= 1000000) {
      finalValue = `${sign}${(absValue / 1000000).toFixed(1)}`
    } else if (absValue >= 1000) {
      finalValue = `${sign}${(absValue / 1000).toFixed(1)}`
    } else if (absValue > 0) {
      finalValue = `${sign}${absValue.toFixed(0)}`
    }

    const unit = absValue >= 1000000 ? 'M' : absValue >= 1000 ? 'K' : ''

    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={8}
          y={0}
          dy={0}
          textAnchor='end'
          fill='#FFFFFF'
          fontSize={9}
          dominantBaseline='middle'
        >
          {finalValue}
        </text>
        <text
          x={15}
          y={0}
          dy={0}
          textAnchor='end'
          fill='#D47FFF'
          fontSize={9}
          dominantBaseline='middle'
        >
          {unit !== '' ? ` ${unit}` : ''}
        </text>
      </g>
    )
  }, [])

  const CustomXAxisTick = useCallback(({ x, y, payload }: any) => {
    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={0}
          y={0}
          dy={16}
          textAnchor='middle'
          fill='#FFFFFF'
          fontSize={12}
        >
          {payload.value}
        </text>
      </g>
    )
  }, [])

  const CustomTooltip = useCallback(({ active, payload, label }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className='rounded-lg border border-white/20 bg-[#1F2026]/95 p-3 shadow-xl backdrop-blur-sm'>
          <p className='mb-2 text-xs font-semibold text-white'>{label}</p>
          <div className='space-y-1'>
            {payload.map((entry: any, index: number) => {
              const value = entry.value
              const absValue = Math.abs(value)
              const sign = value < 0 ? '-' : ''
              return (
                <div
                  key={index}
                  className='flex items-center justify-between gap-3'
                >
                  <div className='flex items-center gap-2'>
                    <div
                      className='h-2 w-2 rounded-full'
                      style={{ backgroundColor: entry.color }}
                    />
                    <span className='text-xs text-gray-300'>{entry.name}:</span>
                  </div>
                  <span className='text-xs font-semibold text-white'>
                    {sign}${absValue.toLocaleString()}
                  </span>
                </div>
              )
            })}
          </div>
        </div>
      )
    }
    return null
  }, [])

  if (isLoading) {
    return (
      <div className='flex h-full flex-col'>
        <h1 className='mb-2 text-start text-base'>
          <span className='font-normal'>Market Premiums</span>
        </h1>
        <Loader />
      </div>
    )
  }

  if (!data || data.length === 0) {
    return (
      <div className='flex h-full flex-col'>
        <h1 className='mb-2 text-start text-base'>
          <span className='font-normal'>Market Premiums</span>
        </h1>
        <div className='flex h-full items-center justify-center'>
          <p className='text-sm text-gray-400'>No data available</p>
        </div>
        <div className='mt-2 flex items-center justify-between'>
          <div className='flex gap-4 text-sm'>
            {MARKET_PREMIUMS_TICKERS.map((ticker, index) => (
              <div
                key={index}
                onClick={() => handleTickerSelection(ticker.value)}
                className='cursor-pointer'
              >
                <span className='inline-block'>{ticker.label}</span>
                {ticker.value === selectedTicker && (
                  <div className='h-[3px] w-full rounded-b-[1px] bg-[#FFFFFF]'></div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className='flex h-full flex-col'>
      <h1 className='mb-2 text-start text-base'>
        <span className='font-normal'>Market Premiums</span>
      </h1>
      <div className='min-h-0 flex-1'>
        <ResponsiveContainer width='100%' height='100%'>
          <ComposedChart
            data={data}
            margin={{
              top: 20,
              right: -37,
              bottom: 20,
              left: 10,
            }}
            barGap={0}
            barCategoryGap={6}
          >
            <XAxis
              axisLine={true}
              tickLine={false}
              stroke='#FFFFFF'
              dataKey='name'
              tick={<CustomXAxisTick />}
            />
            <YAxis
              axisLine={false}
              tickLine={false}
              orientation='right'
              tick={<CustomYAxisTick />}
            />
            <CartesianGrid
              vertical={false}
              strokeDasharray='3 3'
              stroke='#FFFFFF'
              strokeOpacity={0.2}
            />
            <ReferenceLine
              y={0}
              stroke='#FFFFFF'
              strokeWidth={1.5}
              strokeOpacity={0.5}
            />
            <Tooltip content={<CustomTooltip />} />

            {/* Net Call Premium - Green Bar */}
            <Bar
              dataKey='calls'
              barSize={18}
              fill='#28BF94'
              name='Net Call Premium'
              radius={[4, 4, 4, 4]}
            />
            {/* Net Put Premium - Red Bar */}
            <Bar
              dataKey='puts'
              barSize={18}
              fill='#CD4B38'
              name='Net Put Premium'
              radius={[4, 4, 4, 4]}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
      <div className='mt-2 flex items-center justify-between'>
        <div className='flex gap-4 text-sm'>
          {MARKET_PREMIUMS_TICKERS.map((ticker, index) => (
            <div
              key={index}
              onClick={() => handleTickerSelection(ticker.value)}
              className='cursor-pointer'
            >
              <span className='inline-block'>{ticker.label}</span>
              {ticker.value === selectedTicker && (
                <div className='h-[3px] w-full rounded-b-[1px] bg-[#FFFFFF]'></div>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default React.memo(MarketPremiums)
