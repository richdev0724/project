import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { useGammaExposure } from '@/hooks/use-gamma-exposure'
import Loader from '@/components/loader'

interface DataItem {
  strike_price: number
  call_gamma: number
  put_gamma: number
  net_gamma: number
  positive_gex: number
  negative_gex: number
}

interface GammaExposureChartProps {
  symbol: string
  expiration: string
}

const GammaExposureChart = ({
  symbol,
  expiration,
}: GammaExposureChartProps) => {
  const chartRef = useRef<HTMLDivElement>(null)
  const {
    data: response,
    isLoading,
    isError,
  } = useGammaExposure({ ticker: symbol, expiration })

  useEffect(() => {
    if (!chartRef.current || !response?.data) return

    const chart = echarts.init(chartRef.current)

    // Calculate net gamma and get 30 strikes above and below the price
    const allData = response.data
      .map((item: DataItem) => ({
        ...item,
        net_gamma: item.call_gamma + item.put_gamma,
      }))
      .sort(
        (
          a: DataItem & { net_gamma: number },
          b: DataItem & { net_gamma: number }
        ) => a.strike_price - b.strike_price
      )

    // Find the index of the strike closest to current price
    const lastPrice = response.lastPrice ?? allData[0].strike_price
    const centerIndex = allData.findIndex(
      (item: DataItem & { net_gamma: number }) => item.strike_price >= lastPrice
    )

    // Get 30 strikes on each side
    const startIndex = Math.max(0, centerIndex - 20)
    const endIndex = Math.min(allData.length, centerIndex + 20)

    const processedData = allData.slice(startIndex, endIndex)

    // Find max values for arrows
    const maxNetGamma = Math.max(
      ...processedData.map(
        (item: DataItem & { net_gamma: number }) => item.net_gamma
      )
    )
    const minNetGamma = Math.min(
      ...processedData.map(
        (item: DataItem & { net_gamma: number }) => item.net_gamma
      )
    )
    const maxIndex = processedData.findIndex(
      (item: DataItem & { net_gamma: number }) => item.net_gamma === maxNetGamma
    )
    const minIndex = processedData.findIndex(
      (item: DataItem & { net_gamma: number }) => item.net_gamma === minNetGamma
    )

    // Find the closest strike price to the current price
    const strikeIndex = processedData.reduce(
      (closest: number, current: DataItem, index: number) => {
        const lastPrice = response.lastPrice ?? processedData[0].strike_price
        const currentDiff = Math.abs(current.strike_price - lastPrice)
        const closestDiff = Math.abs(
          processedData[closest].strike_price - lastPrice
        )
        return currentDiff < closestDiff ? index : closest
      },
      0
    )

    const option = {
      grid: {
        top: '10%',
        right: '5%',
        bottom: '10%',
        left: '5%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        data: processedData.map((item: DataItem) => item.strike_price),
        axisLine: {
          show: true,
          lineStyle: {
            color: '#FFFFFF',
            opacity: 0.2,
            width: 0.5,
          },
        },
        axisTick: { show: false },
        axisLabel: {
          show: false,
        },
      },
      yAxis: {
        type: 'value',
        position: 'right',
        splitLine: {
          lineStyle: {
            color: '#FFFFFF',
            opacity: 0.2,
            type: 'dashed',
            width: 0.5,
          },
          interval: 50000,
        },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: {
          color: '#FFFFFF',
          fontSize: 9,
          formatter: (value: number) => {
            const absValue = Math.abs(value)
            const prefix = value < 0 ? '-' : ''
            let formattedValue: string
            let suffix = ''

            if (absValue >= 1000000) {
              formattedValue = (absValue / 1000000).toFixed(1)
              if (formattedValue.endsWith('.0')) {
                formattedValue = formattedValue.slice(0, -2)
              }
              suffix = '{million|M}'
            } else if (absValue >= 1000) {
              formattedValue = (absValue / 1000).toFixed(1)
              if (formattedValue.endsWith('.0')) {
                formattedValue = formattedValue.slice(0, -2)
              }
              suffix = '{thousand|K}'
            } else {
              formattedValue = absValue.toString()
            }

            return prefix + formattedValue + suffix
          },
          rich: {
            thousand: {
              color: '#24D438',
              fontSize: 9,
              padding: [0, 0, 0, 2],
            },
            million: {
              color: '#D47FFF',
              fontSize: 9,
              padding: [0, 0, 0, 2],
            },
          },
        },
      },
      series: [
        {
          name: 'Net Gamma',
          type: 'bar',
          data: processedData.map((item: DataItem) => ({
            value: item.net_gamma,
            itemStyle: {
              color: item.net_gamma >= 0 ? '#28BF94' : '#CD4B38',
              borderRadius: item.net_gamma >= 0 ? [3, 3, 0, 0] : [0, 0, 3, 3],
            },
          })),
          barWidth: 6,
          z: 2,
          markPoint: {
            symbol: 'triangle',
            symbolSize: 8,
            data: [
              ...(maxIndex >= 0
                ? [
                    {
                      coord: [maxIndex, processedData[maxIndex].net_gamma],
                      symbolRotate: 180,
                      itemStyle: {
                        color: '#28BF94',
                      },
                      symbolOffset: [0, -15],
                    },
                  ]
                : []),
              ...(minIndex >= 0
                ? [
                    {
                      coord: [minIndex, processedData[minIndex].net_gamma],
                      symbolRotate: 0,
                      itemStyle: {
                        color: '#CD4B38',
                      },
                      symbolOffset: [0, 15],
                    },
                  ]
                : []),
            ],
          },
          markLine: {
            silent: true,
            symbol: ['none', 'none'],
            animation: false,
            label: {
              show: true,
              position: 'end',
              formatter: response.lastPrice?.toFixed(2),
              color: '#FFFFFF',
              fontSize: 10,

              padding: [4, 8],
              backgroundColor: '#1B5139',
              borderRadius: 4,
              distance: 0,
              valueAnimation: true,
              align: 'center',
            },
            lineStyle: {
              color: {
                type: 'linear',
                x: 0,
                y: 0,
                x2: 0,
                y2: 1,
                colorStops: [
                  { offset: 0, color: 'rgba(255, 255, 255, 0)' },
                  { offset: 0.2, color: 'rgba(255, 255, 255, 0.7)' },
                  { offset: 0.5, color: 'rgba(255, 255, 255, 1)' },
                  { offset: 0.8, color: 'rgba(255, 255, 255, 0.7)' },
                  { offset: 1, color: 'rgba(255, 255, 255, 0)' },
                ],
              },
              width: 1,
              type: 'solid',
            },
            data: [
              {
                name: 'Current Price',
                xAxis: strikeIndex,
                label: {
                  position: 'end',
                  formatter: response.lastPrice?.toFixed(2),
                  color: '#FFFFFF',
                  fontSize: 12,
                  fontWeight: 'bold',
                  padding: [4, 8],
                  backgroundColor: '#1B5139',
                  borderRadius: 4,
                  distance: 0,
                  align: 'center',
                },
              },
            ],
          },
        },
      ],
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow',
        },
        backgroundColor: 'rgba(0, 0, 0, 0.8)',
        borderColor: 'rgba(255, 255, 255, 0.2)',
        borderWidth: 1,
        textStyle: {
          color: '#FFFFFF',
          fontSize: 12,
        },
        padding: [8, 12],
        formatter: (params: any) => {
          const strike = params[0].axisValue
          const netGamma = params[0].value
          const formattedNetGamma = netGamma.toLocaleString()
          return `<div class="flex flex-col gap-2">
            <div class="text-xs text-gray-300">Strike: $${strike}</div>
            <div class="flex gap-4 justify-between items-center">
              <div class="flex gap-2 items-center">
                <div class="w-2 h-2 rounded-full" style="background-color: ${netGamma >= 0 ? '#28BF94' : '#CD4B38'}"></div>
                <span class="text-xs font-medium text-white">NET GAMMA</span>
              </div>
              <span class="text-xs font-medium" style="color: ${netGamma >= 0 ? '#28BF94' : '#CD4B38'}">${formattedNetGamma}</span>
            </div>
          </div>`
        },
      },
    }

    chart.setOption(option)

    // Add resize listener
    const handleResize = () => {
      chart.resize()
    }
    window.addEventListener('resize', handleResize)

    // Cleanup
    return () => {
      chart.dispose()
      window.removeEventListener('resize', handleResize)
    }
  }, [response, symbol])

  // Render loading, error, and empty states
  if (!expiration) {
    return (
      <div className='mt-4 flex h-[250px] w-full items-center justify-center text-gray-400'>
        Please select an expiration date
      </div>
    )
  }

  if (isLoading || !response?.data || !response.data.length) {
    return (
      <div className='mt-4 h-[250px] w-full'>
        <Loader />
      </div>
    )
  }

  if (isError) {
    return (
      <div className='mt-4 flex h-[250px] w-full items-center justify-center text-red-400'>
        Error loading data
      </div>
    )
  }

  // Render the chart
  return (
    <div className='mt-4 h-[250px] w-full'>
      <div ref={chartRef} style={{ width: '100%', height: 350 }} />
    </div>
  )
}

export default GammaExposureChart
