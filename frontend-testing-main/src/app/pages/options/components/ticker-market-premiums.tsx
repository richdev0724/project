import Loader from '@/components/loader'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
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
import TickerNetPremiumsModal from './ticker-net-premiums-modal'
import { Maximize2 } from 'lucide-react'
import { useTickerTide } from '@/hooks/use-ticker-tide'

const TickerMarketPremiums = () => {
  const { symbol } = useParams()
  const [isModalOpen, setIsModalOpen] = useState(false)

  const { data: tickerTideData, isLoading } = useTickerTide(symbol ?? '', 5)

  // Transform data for chart - use display date as name
  const data = tickerTideData.map((item) => ({
    name: item.displayDate,
    calls: item.net_call_premium,
    puts: item.net_put_premium,
  }))

  const CustomYAxisTick = ({ x, y, payload }: any) => {
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
  }

  const CustomXAxisTick = ({ x, y, payload }: any) => {
    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={0}
          y={0}
          dy={16}
          textAnchor='middle'
          fill='#FFFFFF'
          fontSize={12}
          // opacity={0.5}
        >
          {payload.value}
        </text>
      </g>
    )
  }

  const CustomTooltip = ({ active, payload, label }: any) => {
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
  }

  if (isLoading) {
    return (
      <div className='h-[404px] w-full pl-5 pr-2'>
        <Loader />
      </div>
    )
  }

  if (!data || data.length === 0) {
    return (
      <div className='flex h-[404px] w-full items-center justify-center pl-5 pr-2'>
        <p className='text-gray-400'>No net premiums data available</p>
      </div>
    )
  }

  return (
    <>
      <TickerNetPremiumsModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        ticker={symbol ?? ''}
        days={5}
      />
      <div className='h-[404px] w-full pl-5 pr-2'>
        <div className='flex items-center justify-between'>
          <h1 className='text-start text-sm'>
            <span className='font-normal'>{`${symbol} Net Premiums`}</span>
          </h1>
          <button
            onClick={() => setIsModalOpen(true)}
            className='group flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-gray-400 transition-all hover:bg-white/5 hover:text-white'
            title='View detailed net premiums'
          >
            <Maximize2 className='h-3.5 w-3.5 transition-transform group-hover:scale-110' />
            <span>Expand</span>
          </button>
        </div>
        <div className='h-[394px]'>
          <ResponsiveContainer width='100%' height={360} className='mt-3'>
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

              {/* Net Call Premium - Green Bar (solid) */}
              <Bar
                dataKey='calls'
                barSize={18}
                fill='#28BF94'
                name='Net Call Premium'
                radius={[4, 4, 4, 4]}
              />
              {/* Net Put Premium - Red Bar (solid) */}
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
      </div>
    </>
  )
}

export default TickerMarketPremiums
