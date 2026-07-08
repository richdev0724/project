import { useParams } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
  ReferenceLine,
} from 'recharts'
import { useEarning } from '@/hooks/use-earning'
import Loader from '@/components/loader'
import { useMemo } from 'react'

// Helper function to parse YYYY-MM-DD date strings without timezone issues
const parseDate = (dateString: string | null | undefined): Date | null => {
  if (!dateString) return null
  const [year, month, day] = dateString.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const TickerEarningsEPS = () => {
  const { symbol } = useParams()
  const { data: earningsData, isLoading, error } = useEarning(symbol || '')

  // Transform earnings data for the chart
  const data = useMemo(() => {
    if (!earningsData || earningsData.length === 0) return []

    // Filter only for essential fields, allow missing actual_eps (future earnings)
    const sortedData = [...earningsData]
      .filter(
        (item) =>
          item.report_date && item.ending_fiscal_quarter && item.street_mean_est
      )
      .sort((a, b) => {
        const dateA = parseDate(a.report_date)
        const dateB = parseDate(b.report_date)
        if (!dateA || !dateB) return 0
        return dateA.getTime() - dateB.getTime()
      })
      .slice(-5) // Take last 5 (most recent + upcoming)

    return sortedData
      .map((item) => {
        // Extract quarter from ending_fiscal_quarter (format: YYYY-MM-DD)
        const date = parseDate(item.ending_fiscal_quarter)
        if (!date) return null

        const month = date.getMonth() + 1
        let quarter = 'Q1'
        if (month >= 1 && month <= 3) quarter = 'Q1'
        else if (month >= 4 && month <= 6) quarter = 'Q2'
        else if (month >= 7 && month <= 9) quarter = 'Q3'
        else quarter = 'Q4'

        const year = date.getFullYear().toString().slice(-2)

        return {
          name: quarter,
          year: `'${year}`,
          uv: item.actual_eps ? parseFloat(item.actual_eps) : 0, // 0 if no actual yet (future earnings)
          pv: parseFloat(item.street_mean_est) || 0,
        }
      })
      .filter((item): item is NonNullable<typeof item> => item !== null)
  }, [earningsData])

  const CustomYAxisTick = ({ ...props }) => {
    const { x, y, payload } = props
    return (
      <g transform={`translate(${x},${y})`}>
        <text
          x={8}
          y={0}
          dy={0}
          textAnchor='end'
          fill='#FFFFFF'
          fontSize={11}
          fontWeight={500}
          dominantBaseline='middle'
        >
          {payload.value}
        </text>
      </g>
    )
  }

  // Helper (optional, keeps things tidy)
  const arcRoundedBottom = (
    x: number,
    yTop: number,
    width: number,
    height: number,
    r: number
  ) => {
    const yBottom = yTop + height
    return `
    M ${x} ${yTop}
    L ${x} ${yBottom - r}
    A ${r} ${r} 0 0 0 ${x + r} ${yBottom}
    L ${x + width - r} ${yBottom}
    A ${r} ${r} 0 0 0 ${x + width} ${yBottom - r}
    L ${x + width} ${yTop}
    L ${x} ${yTop}
    Z
  `
  }

  const CustomXAxisTick = ({ ...props }) => {
    const { x, y, payload, index } = props
    const yearLabel = data[index]?.year || "'24"
    return (
      <g>
        <text
          x={x + 13}
          y={y + 10}
          dy={0}
          textAnchor='end'
          fontWeight={'bold'}
          fill='#FFFFFF'
          fontSize={12}
          dominantBaseline='middle'
        >
          {payload.value}
        </text>
        <text
          x={x + 13}
          y={y + 30}
          dy={0}
          textAnchor='end'
          fontWeight={'light'}
          fill='#FFFFFF'
          fontSize={12}
          dominantBaseline='middle'
        >
          {yearLabel}
        </text>
      </g>
    )
  }

  // Calculate dynamic Y-axis ticks based on data
  const yAxisTicks = useMemo(() => {
    if (data.length === 0) return [0.5, 0.7, 0.9, 1.1, 1.3]

    const allValues = data.flatMap((item) => [item.uv, item.pv])
    const maxValue = Math.max(...allValues)
    const minValue = Math.min(...allValues)

    // Add some padding
    const range = Math.abs(maxValue - minValue)
    const paddedMax = maxValue + range * 0.2
    const paddedMin = minValue - range * 0.2 // Allow negative values

    // Generate 5 ticks
    const step = (paddedMax - paddedMin) / 4
    return Array.from({ length: 5 }, (_, i) =>
      Number((paddedMin + step * i).toFixed(2))
    )
  }, [data])

  // Custom Tooltip
  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      const actualEPS = payload.find((p: any) => p.dataKey === 'uv')?.value
      const estimatedEPS = payload.find((p: any) => p.dataKey === 'pv')?.value
      const difference = actualEPS - estimatedEPS
      const isBeat = difference >= 0

      return (
        <div className='rounded-lg border border-white/20 bg-[#1F2026]/95 p-3 shadow-xl backdrop-blur-sm'>
          <p className='mb-2 text-xs font-semibold text-white'>
            {payload[0]?.payload?.name} {payload[0]?.payload?.year}
          </p>
          <div className='space-y-1'>
            <div className='flex items-center justify-between gap-3'>
              <div className='flex items-center gap-2'>
                <div
                  className='h-2 w-2 rounded-full'
                  style={{
                    backgroundColor:
                      actualEPS < estimatedEPS ? '#CD4B38' : '#28BF94',
                  }}
                />
                <span className='text-xs text-gray-300'>Actual EPS:</span>
              </div>
              <span className='text-xs font-semibold text-white'>
                ${actualEPS?.toFixed(2) || '0.00'}
              </span>
            </div>
            <div className='flex items-center justify-between gap-3'>
              <div className='flex items-center gap-2'>
                <div
                  className='h-2 w-2 rounded-full border border-gray-400'
                  style={{ backgroundColor: 'transparent' }}
                />
                <span className='text-xs text-gray-300'>Est. EPS:</span>
              </div>
              <span className='text-xs font-semibold text-white'>
                ${estimatedEPS?.toFixed(2) || '0.00'}
              </span>
            </div>
            <div className='mt-2 border-t border-white/10 pt-2'>
              <div className='flex items-center justify-between gap-3'>
                <span className='text-xs text-gray-300'>
                  {isBeat ? 'Beat by:' : 'Miss by:'}
                </span>
                <span
                  className='text-xs font-bold'
                  style={{ color: isBeat ? '#28BF94' : '#CD4B38' }}
                >
                  ${Math.abs(difference).toFixed(2)}
                </span>
              </div>
            </div>
          </div>
        </div>
      )
    }
    return null
  }

  // Function to determine fill color based on uv and pv comparison
  const getFillColor = (entry: { name?: string; uv: any; pv: any }) => {
    return entry.uv < entry.pv
      ? 'url(#redLinearGradient)'
      : 'url(#greenLinearGradient)'
  }

  if (isLoading) {
    return (
      <div className='flex h-[285px] w-full items-center justify-center pt-4'>
        <Loader />
      </div>
    )
  }

  if (error || !data || data.length === 0) {
    return (
      <div className='h-[285px] w-full pt-4'>
        <h1 className='mb-1 pl-6 text-start text-base'>
          <span className='font-normal'>{`${symbol} Earnings - EPS`}</span>
        </h1>
        <div className='flex h-full w-full items-center justify-center'>
          <p className='text-gray-400'>No earnings data available</p>
        </div>
      </div>
    )
  }

  return (
    <div className='h-[285px] w-full pt-4'>
      <h1 className='mb-1 pl-6 text-start text-base'>
        <span className='font-normal'>{`${symbol} Earnings - EPS`}</span>
      </h1>
      <div className='mt-[20px] h-full w-full px-4'>
        <ResponsiveContainer width='100%' height='100%'>
          <BarChart
            data={data}
            margin={{
              top: 5,
              right: -10,
              left: 20,
              bottom: 65,
            }}
          >
            <defs>
              <linearGradient
                id='dashedLinerGradient'
                x1='0'
                y1='1'
                x2='0'
                y2='0'
              >
                <stop offset='1%' stopColor='hsla(201, 3%, 34%, 0)' />
                <stop offset='100%' stopColor='hsla(201, 9%, 73%, 1)' />
              </linearGradient>
              <linearGradient
                id='greenLinearGradient'
                x1='0'
                y1='1'
                x2='0'
                y2='0'
              >
                <stop offset='0.5%' stopColor='hsla(76, 55%, 25%, 0)' />
                <stop offset='100%' stopColor='hsla(76, 56%, 50%, 1)' />
              </linearGradient>
              <linearGradient
                id='redLinearGradient'
                x1='0'
                y1='0'
                x2='0'
                y2='1'
              >
                <stop offset='0.5%' stopColor='hsla(0, 60%, 25%, 0)' />
                <stop offset='100%' stopColor='hsla(0, 70%, 50%, 1)' />
              </linearGradient>
            </defs>
            <XAxis
              dataKey='name'
              tickLine={false}
              axisLine={false}
              color='#F5F7F7'
              fontSize={14}
              tick={<CustomXAxisTick />}
            />
            <YAxis
              tickLine={false}
              axisLine={false}
              fontSize={11}
              orientation='right'
              tickMargin={10}
              ticks={yAxisTicks}
              interval={0}
              tick={<CustomYAxisTick />}
            />
            <CartesianGrid
              strokeDasharray='3 3'
              stroke='#FFFFFF'
              strokeOpacity={0.2}
              vertical={false}
            />
            <ReferenceLine
              y={0}
              stroke='#FFFFFF'
              strokeWidth={1.5}
              strokeOpacity={0.5}
            />
            <Tooltip content={<CustomTooltip />} />
            <Bar
              dataKey='pv'
              fill='none'
              stroke={`url(#dashedLinerGradient)`}
              strokeDasharray={'5 5'}
              barSize={20}
              radius={[3, 3, 0, 0]}
            />
            <Bar
              dataKey='uv'
              barSize={20}
              radius={[3, 3, 0, 0]}
              shape={(props: any) => {
                const { x, y, width, height, index, value } = props
                const item = data[index]
                const fill = getFillColor(item)
                const radius = 3

                const isNegative = value < 0
                const effectiveRadius = Math.min(
                  radius,
                  Math.abs(height) / 2,
                  width / 2
                )

                if (isNegative) {
                  // Rounded at the *bottom* end for bars below zero
                  return (
                    <path
                      d={arcRoundedBottom(x, y, width, height, effectiveRadius)}
                      fill={fill}
                      shapeRendering='geometricPrecision'
                    />
                  )
                } else {
                  // (unchanged) Rounded at the top for positive bars
                  return (
                    <path
                      d={`
                        M ${x} ${y + height}
                        L ${x} ${y + effectiveRadius}
                        A ${effectiveRadius} ${effectiveRadius} 0 0 1 ${x + effectiveRadius} ${y}
                        L ${x + width - effectiveRadius} ${y}
                        A ${effectiveRadius} ${effectiveRadius} 0 0 1 ${x + width} ${y + effectiveRadius}
                        L ${x + width} ${y + height}
                        Z
                      `}
                      fill={fill}
                      shapeRendering='geometricPrecision'
                    />
                  )
                }
              }}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default TickerEarningsEPS
