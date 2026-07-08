import { Dialog, DialogContent } from '@/components/ui/dialog'
import { useTickerTide } from '@/hooks/use-ticker-tide'
import Loader from '@/components/loader'
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
  ReferenceLine,
} from 'recharts'

interface TickerNetPremiumsModalProps {
  isOpen: boolean
  onClose: () => void
  ticker: string
  days?: number
}

// Custom tooltip to show values with proper labels and signs
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
      >
        {payload.value}
      </text>
    </g>
  )
}

const TickerNetPremiumsModal = ({
  isOpen,
  onClose,
  ticker,
  days = 5,
}: TickerNetPremiumsModalProps) => {
  const { data, isLoading } = useTickerTide(ticker, days)

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent
        className='flex max-h-[85vh] max-w-5xl flex-col overflow-y-auto rounded-xl border border-white/10 bg-[#1F2026]/40 p-8 backdrop-blur-xl !duration-500 data-[state=closed]:!scale-100 data-[state=open]:!scale-100 sm:max-w-5xl'
        style={{
          background:
            'linear-gradient(135deg, rgba(31, 32, 38, 0.4) 0%, rgba(31, 32, 38, 0.2) 100%)',
          boxShadow:
            '0 20px 50px -12px rgba(0, 0, 0, 0.5), 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
        }}
      >
        <div className='text-[#F5F7F7]/80'>
          {/* Header */}
          <div className='flex items-start justify-between'>
            <div className='flex-1'>
              <h1 className='text-2xl font-bold text-[#F5F7F7]'>
                {ticker} Net Premiums Flow
              </h1>
              <p className='mt-1.5 text-sm text-[#F5F7F7]/70'>
                Daily net call and put premiums for the last {days} trading days
              </p>
            </div>
          </div>

          <div className='my-4 h-px w-full bg-sky-500/50'></div>

          {/* Chart Section */}
          <div className='space-y-4 pt-2'>
            {isLoading ? (
              <div className='flex h-[500px] items-center justify-center'>
                <Loader />
              </div>
            ) : !data || data.length === 0 ? (
              <div className='flex h-[500px] items-center justify-center'>
                <p className='text-gray-400'>No data available</p>
              </div>
            ) : (
              <div className='rounded-lg border border-white/10 bg-gradient-to-br from-gray-800/40 to-gray-900/30 p-6'>
                <div className='h-[500px]'>
                  <ResponsiveContainer width='100%' height='100%'>
                    <ComposedChart
                      data={data}
                      margin={{
                        top: 20,
                        right: 30,
                        bottom: 20,
                        left: 20,
                      }}
                      barGap={0}
                      barCategoryGap={8}
                    >
                      <XAxis
                        axisLine={true}
                        tickLine={false}
                        stroke='#FFFFFF'
                        dataKey='displayDate'
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
                      <Legend
                        wrapperStyle={{
                          paddingTop: '20px',
                        }}
                        iconType='circle'
                        formatter={(value) => (
                          <span className='text-sm text-[#F5F7F7]'>
                            {value}
                          </span>
                        )}
                      />

                      {/* Net Call Premium - Green Bar (solid) */}
                      <Bar
                        dataKey='net_call_premium'
                        barSize={40}
                        fill='#28BF94'
                        name='Net Call Premium'
                        radius={[4, 4, 4, 4]}
                      />
                      {/* Net Put Premium - Red Bar (solid, goes below 0) */}
                      <Bar
                        dataKey='net_put_premium'
                        barSize={40}
                        fill='#CD4B38'
                        name='Net Put Premium'
                        radius={[4, 4, 4, 4]}
                      />
                    </ComposedChart>
                  </ResponsiveContainer>
                </div>

                {/* Data Summary */}
                <div className='mt-6 grid grid-cols-2 gap-4 border-t border-white/10 pt-4 md:grid-cols-5'>
                  {data.map((item, index) => {
                    // Format premiums in M or K
                    const formatPremium = (value: number) => {
                      const absValue = Math.abs(value)
                      const sign = value < 0 ? '-' : ''

                      if (absValue >= 1000000) {
                        return {
                          value: `${sign}$${(absValue / 1000000).toFixed(2)}`,
                          unit: 'M',
                          color: '#D47FFF',
                        }
                      } else {
                        return {
                          value: `${sign}$${(absValue / 1000).toFixed(1)}`,
                          unit: 'K',
                          color: '#24D438',
                        }
                      }
                    }

                    const callPremium = item.net_call_premium
                    const putPremium = item.net_put_premium

                    // Apply logic for net calculation
                    let adjustedPutForNet = putPremium

                    if (callPremium > 0 && putPremium > 0) {
                      // If calls positive and puts positive, treat puts as negative
                      adjustedPutForNet = -Math.abs(putPremium)
                    } else if (callPremium > 0 && putPremium < 0) {
                      // If calls positive and puts negative, treat puts as positive
                      adjustedPutForNet = Math.abs(putPremium)
                    } else if (callPremium < 0 && putPremium > 0) {
                      // If calls negative and puts positive, treat puts as negative
                      adjustedPutForNet = -Math.abs(putPremium)
                    } else if (callPremium < 0 && putPremium < 0) {
                      // If calls negative and puts negative, treat puts as positive
                      adjustedPutForNet = Math.abs(putPremium)
                    }

                    const callFormatted = formatPremium(callPremium)
                    const putFormatted = formatPremium(putPremium)
                    const netDifference = callPremium + adjustedPutForNet
                    const netFormatted = formatPremium(netDifference)

                    return (
                      <div
                        key={index}
                        className='rounded-lg border border-white/10 bg-gray-900/40 p-3'
                      >
                        <p className='mb-2 text-xs text-gray-400'>
                          {item.displayDate}
                        </p>
                        <div className='space-y-1.5'>
                          <div className='flex items-center justify-between'>
                            <span className='text-[10px] text-[#28BF94]'>
                              Calls:
                            </span>
                            <div className='flex items-baseline gap-0.5'>
                              <span className='text-xs font-semibold text-white'>
                                {callFormatted.value}
                              </span>
                              <span
                                className='text-[10px] font-medium'
                                style={{ color: callFormatted.color }}
                              >
                                {callFormatted.unit}
                              </span>
                            </div>
                          </div>
                          <div className='flex items-center justify-between'>
                            <span className='text-[10px] text-[#CD4B38]'>
                              Puts:
                            </span>
                            <div className='flex items-baseline gap-0.5'>
                              <span className='text-xs font-semibold text-white'>
                                {putFormatted.value}
                              </span>
                              <span
                                className='text-[10px] font-medium'
                                style={{ color: putFormatted.color }}
                              >
                                {putFormatted.unit}
                              </span>
                            </div>
                          </div>
                          <div className='mt-1.5 flex items-center justify-between border-t border-white/10 pt-1.5'>
                            <span className='text-[10px] text-gray-400'>
                              Net:
                            </span>
                            <div className='flex items-baseline gap-0.5'>
                              <span
                                className='text-xs font-bold'
                                style={{
                                  color:
                                    netDifference >= 0 ? '#28BF94' : '#CD4B38',
                                }}
                              >
                                {netFormatted.value}
                              </span>
                              <span
                                className='text-[10px] font-medium'
                                style={{ color: netFormatted.color }}
                              >
                                {netFormatted.unit}
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}

            {/* Info Section */}
            <div className='rounded-lg border border-[#28BF94]/30 bg-gradient-to-br from-[#28BF94]/10 to-transparent p-4'>
              <div className='mb-2 flex items-center gap-2'>
                <div className='h-2 w-2 rounded-full bg-[#28BF94]' />
                <h3 className='text-sm font-bold text-[#F5F7F7]'>
                  About Net Premiums
                </h3>
              </div>
              <p className='text-xs text-[#F5F7F7]/80'>
                Net Call Premium represents the total premium paid for call
                options, indicating bullish sentiment. Net Put Premium
                represents the total premium paid for put options, indicating
                bearish sentiment. These values are calculated from the last
                recorded tape time of each trading day.
              </p>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}

export default TickerNetPremiumsModal
