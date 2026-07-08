import { useMarketPrices } from '@/hooks/use-market-prices'
import { useTickerSnapshot } from '@/hooks/use-ticker-snapshot'
import moment from 'moment-timezone'
import { useParams } from 'react-router-dom'
import LogoWithFallback from '../ticker-logo'
import Loader from '@/components/loader'
import { useState, useRef, useEffect } from 'react'
import { TIMEFRAME } from '@/constants'
import * as echarts from 'echarts'

// Plot options
const PLOT_OPTIONS = [
  { id: 'pdh', label: 'PDH' },
  { id: 'pdl', label: 'PDL' },
  { id: 'ema50', label: '50 EMA' },
  { id: 'ema200', label: '200 EMA' },
]

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

// Separate EChart component for better lifecycle management
const EChart: React.FC<{
  data: any
  symbol: string
  selectedTimeFrame: string
  prevDayHigh?: number
  prevDayLow?: number
  activePlots: string[]
}> = ({
  data,
  symbol,
  selectedTimeFrame,
  prevDayHigh,
  prevDayLow,
  activePlots,
}): JSX.Element => {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    // Cleanup function to ensure proper disposal
    return () => {
      if (chartInstance.current) {
        try {
          chartInstance.current.dispose()
        } catch (e) {
          // Error disposing chart
        }
        chartInstance.current = null
      }
    }
  }, [])

  useEffect(() => {
    if (!chartRef.current || !data) return

    // Always dispose of the previous instance first
    if (chartInstance.current) {
      try {
        chartInstance.current.dispose()
      } catch (e) {
        // Error disposing previous chart
      }
      chartInstance.current = null
    }

    try {
      const chart = echarts.init(chartRef.current)
      chartInstance.current = chart

      const chartData = data.map((item: any) => [item.t, item.c])

      // Calculate EMAs
      const prices = chartData.map((item: any) => item[1])
      const ema50Data = calculateEMA(prices, 50)
      const ema200Data = calculateEMA(prices, 200)

      // Get the time range for PDH/PDL lines
      const timeRange = chartData.map((item: any) => item[0])
      const startTime = Math.min(...timeRange)
      const endTime = Math.max(...timeRange)

      const option = {
        grid: {
          left: 50,
          right: 0,
          top: 30,
          bottom: 30,
          containLabel: false,
        },
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(28, 28, 40, 1)',
          borderWidth: 0,
          padding: [12, 12],
          textStyle: {
            color: '#fff',
            fontSize: 12,
          },
          formatter: (params: any) => {
            const value = params[0].value
            const price = value[1]
            const timestamp = value[0]
            const showTime = ['1D', '5D', '1M', '3M'].includes(
              selectedTimeFrame
            )
            const formattedDate = showTime
              ? moment(timestamp)
                  .tz('America/New_York')
                  .format('MMM DD, YYYY h:mm A z')
              : moment(timestamp).tz('America/New_York').format('MMM DD, YYYY')

            let tooltipContent = `
              <div class="text-xs rounded-lg">
                <p class="mb-2 font-medium">${formattedDate}</p>
                <div class="flex gap-4 justify-between items-center">
                  <div class="flex gap-2 items-center">
                    <div class="w-2 h-2 rounded-full" style="background-color: #FFFFFF"></div>
                    <span class="font-medium">${symbol}</span>
                  </div>
                  <span class="font-medium text-gray-300">$${price.toFixed(2)}</span>
                </div>
            `

            // Add EMA values if they are active
            if (activePlots.includes('ema50')) {
              const ema50Value = params.find(
                (p: any) => p.seriesName === '50 EMA'
              )?.value[1]
              if (ema50Value) {
                tooltipContent += `
                  <div class="mt-1 flex gap-4 justify-between items-center">
                    <div class="flex gap-2 items-center">
                      <div class="w-2 h-2 rounded-full" style="background-color: #F6C85D"></div>
                      <span class="font-medium">50 EMA</span>
                    </div>
                    <span class="font-medium text-gray-300">$${ema50Value.toFixed(2)}</span>
                  </div>
                `
              }
            }

            if (activePlots.includes('ema200')) {
              const ema200Value = params.find(
                (p: any) => p.seriesName === '200 EMA'
              )?.value[1]
              if (ema200Value) {
                tooltipContent += `
                  <div class="mt-1 flex gap-4 justify-between items-center">
                    <div class="flex gap-2 items-center">
                      <div class="w-2 h-2 rounded-full" style="background-color: #8B5CF6"></div>
                      <span class="font-medium">200 EMA</span>
                    </div>
                    <span class="font-medium text-gray-300">$${ema200Value.toFixed(2)}</span>
                  </div>
                `
              }
            }

            // Add PDH/PDL info if available
            if (prevDayHigh && params[0].value[1] >= prevDayHigh) {
              tooltipContent += `<div class="mt-1 text-[#FF9800]">PDH: $${prevDayHigh.toFixed(2)}</div>`
            }
            if (prevDayLow && params[0].value[1] <= prevDayLow) {
              tooltipContent += `<div class="mt-1 text-[#FF9800]">PDL: $${prevDayLow.toFixed(2)}</div>`
            }

            tooltipContent += '</div>'
            return tooltipContent
          },
        },
        xAxis: {
          type: 'time',
          show: true,
          position: 'bottom',
          axisLine: {
            show: false,
          },
          axisTick: {
            show: true,
            length: 6,
            lineStyle: {
              color: '#FFFFFF',
              opacity: 0.2,
            },
            alignWithLabel: true,
            inside: true,
          },
          axisLabel: {
            show: true,
            color: '#FFFFFF',
            opacity: 0.5,
            fontSize: 10,
            formatter: (value: number) => {
              if (['1D', '5D'].includes(selectedTimeFrame)) {
                return moment(value).format('HH:mm')
              } else if (['1M', '3M'].includes(selectedTimeFrame)) {
                return moment(value).format('MMM DD')
              } else {
                return moment(value).format('MMM YYYY')
              }
            },
            position: 'insideBottom',
            padding: [0, 0, 8, 0],
          },
          splitLine: {
            show: false,
          },
          interval: (() => {
            if (selectedTimeFrame === '1D') return 3600 * 2 * 1000 // 2 hours
            if (selectedTimeFrame === '5D') return 3600 * 6 * 1000 // 6 hours
            if (selectedTimeFrame === '1M') return 3600 * 24 * 5 * 1000 // 5 days
            if (selectedTimeFrame === '3M') return 3600 * 24 * 15 * 1000 // 15 days
            return undefined
          })(),
        },
        yAxis: {
          type: 'value',
          show: false,
          scale: true,
        },
        series: [
          // Price line
          ...(activePlots.includes('price')
            ? [
                {
                  data: chartData,
                  type: 'line',
                  smooth: 0.2,
                  symbol: 'none',
                  lineStyle: {
                    color: '#FFFFFF',
                    width: 1.5,
                  },
                },
              ]
            : []),
          // 50 EMA Line
          ...(activePlots.includes('ema50')
            ? [
                {
                  name: '50 EMA',
                  data: chartData.map((item: any, index: number) => [
                    item[0],
                    ema50Data[index],
                  ]),
                  type: 'line',
                  smooth: 0.2,
                  symbol: 'none',
                  lineStyle: {
                    color: '#F6C85D',
                    width: 1.5,
                  },
                },
              ]
            : []),
          // 200 EMA Line
          ...(activePlots.includes('ema200')
            ? [
                {
                  name: '200 EMA',
                  data: chartData.map((item: any, index: number) => [
                    item[0],
                    ema200Data[index],
                  ]),
                  type: 'line',
                  smooth: 0.2,
                  symbol: 'none',
                  lineStyle: {
                    color: '#8B5CF6',
                    width: 1.5,
                  },
                },
              ]
            : []),
          // PDH Line
          ...(activePlots.includes('pdh') && prevDayHigh
            ? [
                {
                  name: 'PDH',
                  type: 'line',
                  markLine: {
                    symbol: ['none', 'none'],
                    silent: true,
                    animation: false,
                    label: {
                      show: true,
                      position: 'start',
                      formatter: () => {
                        return [
                          '{title|PDH}',
                          `{value|$${prevDayHigh.toFixed(2)}}`,
                        ].join('\n')
                      },
                      rich: {
                        title: {
                          color: '#FF9800',
                          fontSize: 10,
                          fontWeight: 'bold',
                          padding: [0, 0, 2, 0],
                        },
                        value: {
                          color: '#FF9800',
                          fontSize: 10,
                          fontWeight: 'normal',
                        },
                      },
                    },
                    lineStyle: {
                      color: '#FF9800',
                      width: 1,
                    },
                    data: [
                      [
                        { coord: [startTime, prevDayHigh] },
                        { coord: [endTime, prevDayHigh] },
                      ],
                    ],
                  },
                },
              ]
            : []),
          // PDL Line
          ...(activePlots.includes('pdl') && prevDayLow
            ? [
                {
                  name: 'PDL',
                  type: 'line',
                  markLine: {
                    symbol: ['none', 'none'],
                    silent: true,
                    animation: false,
                    label: {
                      show: true,
                      position: 'start',
                      formatter: () => {
                        return [
                          '{title|PDL}',
                          `{value|$${prevDayLow.toFixed(2)}}`,
                        ].join('\n')
                      },
                      rich: {
                        title: {
                          color: '#FF9800',
                          fontSize: 10,
                          fontWeight: 'bold',
                          padding: [0, 0, 2, 0],
                        },
                        value: {
                          color: '#FF9800',
                          fontSize: 10,
                          fontWeight: 'normal',
                        },
                      },
                    },
                    lineStyle: {
                      color: '#FF9800',
                      width: 1,
                    },
                    data: [
                      [
                        { coord: [startTime, prevDayLow] },
                        { coord: [endTime, prevDayLow] },
                      ],
                    ],
                  },
                },
              ]
            : []),
        ],
      }

      chart.setOption(option)

      const handleResize = () => {
        chart?.resize()
      }

      window.addEventListener('resize', handleResize)

      return () => {
        window.removeEventListener('resize', handleResize)
      }
    } catch (e) {
      // Error setting up chart
    }
  }, [data, symbol, selectedTimeFrame, prevDayHigh, prevDayLow, activePlots])

  return <div ref={chartRef} className='h-full w-full' />
}

const TickerMainChart: React.FC<{ tagLine: string }> = ({ tagLine }) => {
  const { symbol = '' } = useParams()
  const [selectedTimeFrame, setSelectedTimeFrame] = useState<string>(
    TIMEFRAME[0].value
  )
  const [activePlots, setActivePlots] = useState<string[]>([
    'price',
    'pdh',
    'pdl',
    'ema50',
    'ema200',
  ])
  const {
    data: polygonData,
    isLoading,
    isError,
  } = useMarketPrices(selectedTimeFrame, [symbol])
  const { data: snapshotData } = useTickerSnapshot(symbol)

  // Debug logging removed for production

  // Get previous day's high and low
  const prevDayHigh = snapshotData?.[symbol]?.prevDay?.h
  const prevDayLow = snapshotData?.[symbol]?.prevDay?.l

  // Reset timeframe when symbol changes
  useEffect(() => {
    setSelectedTimeFrame(TIMEFRAME[0].value)
  }, [symbol])

  const handleTimeFrame = (timeframe: string) => {
    setSelectedTimeFrame(timeframe)
  }

  const togglePlot = (plotId: string) => {
    setActivePlots((prev) => {
      if (prev.includes(plotId)) {
        return prev.filter((p) => p !== plotId)
      } else {
        return [...prev, plotId]
      }
    })
  }

  if (!symbol) {
    return (
      <div className='flex h-[500px] w-full items-center justify-center'>
        <p className='text-gray-400'>No ticker symbol provided</p>
      </div>
    )
  }

  return (
    <div className='h-[440px] w-full'>
      <div className='mb-2 flex flex-row items-center justify-between'>
        <div
          className='flex flex-row items-center space-x-3'
          style={{ marginLeft: '-20px' }}
        >
          <LogoWithFallback symbol={symbol} />
          <div>
            <p className='text-2xl font-bold'>{symbol}</p>
            {tagLine && (
              <p className='-mt-1 ml-0.5 text-[12px] font-light'>{tagLine}</p>
            )}
          </div>
        </div>
      </div>
      <div
        className='relative'
        style={{ height: 'calc(100% - 80px)', width: '100%' }}
      >
        {isLoading ? (
          <div className='absolute inset-0 flex items-center justify-center'>
            <Loader />
          </div>
        ) : isError || !polygonData || !polygonData[symbol]?.results ? (
          <div className='absolute inset-0 flex items-center justify-center'>
            <p className='text-gray-400'>No data available</p>
          </div>
        ) : (
          <EChart
            data={polygonData[symbol].results}
            symbol={symbol}
            selectedTimeFrame={selectedTimeFrame}
            prevDayHigh={prevDayHigh}
            prevDayLow={prevDayLow}
            activePlots={activePlots}
          />
        )}
      </div>
      <div className='mt-7 flex items-center justify-between'>
        <div className='flex gap-4 text-sm'>
          {PLOT_OPTIONS.map((plot) => {
            let underlineColor = ''
            switch (plot.id) {
              case 'pdh':
              case 'pdl':
                underlineColor = '#FF9800'
                break
              case 'ema50':
                underlineColor = '#F6C85D'
                break
              case 'ema200':
                underlineColor = '#8B5CF6'
                break
              default:
                underlineColor = '#FFFFFF'
            }

            return (
              <div
                key={plot.id}
                onClick={() => togglePlot(plot.id)}
                className='cursor-pointer'
              >
                <span
                  className={`inline-block ${activePlots.includes(plot.id) ? 'text-white' : 'text-gray-400'}`}
                >
                  {plot.label}
                </span>
                {activePlots.includes(plot.id) && (
                  <div
                    className='h-[2px] w-full rounded-b-[1px]'
                    style={{ backgroundColor: underlineColor }}
                  />
                )}
              </div>
            )
          })}
        </div>
        <div className='flex gap-4 text-sm font-medium'>
          {TIMEFRAME.map((timeframe, index) => (
            <div
              key={index}
              onClick={() => handleTimeFrame(timeframe.value)}
              className='cursor-pointer'
            >
              <span className='inline-block'>{timeframe.label}</span>
              {timeframe.value === selectedTimeFrame && (
                <div className='h-[2px] w-full rounded-b-[1px] bg-[#FFFFFF]' />
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

export default TickerMainChart
