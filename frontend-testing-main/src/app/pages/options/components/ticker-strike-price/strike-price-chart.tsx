import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { useGammaExposure } from '@/hooks/use-gamma-exposure'
import Loader from '@/components/loader'

interface StrikePriceChartProps {
  symbol: string | undefined
  expiration: string
  side: 'open_interest' | 'daily_volume' | 'net_premium'
}

const StrikePriceChart = ({
  symbol,
  expiration,
  side,
}: StrikePriceChartProps) => {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)
  const { data: response, isLoading } = useGammaExposure({
    ticker: symbol ?? '',
    expiration,
  })

  // Initialize chart
  useEffect(() => {
    // Cleanup function to properly dispose of chart
    const cleanup = () => {
      if (chartInstance.current) {
        chartInstance.current.dispose()
        chartInstance.current = null
      }
    }

    // Clean up existing chart before creating new one
    cleanup()

    if (!chartRef.current || !response?.data || !response.lastPrice) return

    // Create new chart instance
    const chart = echarts.init(chartRef.current)
    chartInstance.current = chart

    // Process data based on selected side
    const allData = response.data
      .map((item) => {
        let callValue = 0
        let putValue = 0
        switch (side) {
          case 'open_interest':
            callValue = item.call_oi
            putValue = -item.put_oi // Make puts negative for display
            break
          case 'daily_volume':
            callValue = item.call_volume
            putValue = -item.put_volume // Make puts negative for display
            break
          case 'net_premium':
            callValue = item.call_premium
            putValue = -item.put_premium // Make puts negative for display
            break
        }
        return {
          strike_price: item.strike_price,
          callValue,
          putValue,
          actualPutValue: Math.abs(putValue), // Store actual value for display
        }
      })
      .sort((a, b) => a.strike_price - b.strike_price)

    // Find the index of the strike closest to current price
    const lastPrice = response.lastPrice ?? 0
    const centerIndex = allData.findIndex(
      (item) => item.strike_price >= lastPrice
    )

    // Get 30 strikes on each side instead of 20 to show more data points
    const startIndex = Math.max(0, centerIndex - 30)
    const endIndex = Math.min(allData.length, centerIndex + 30)

    const processedData = allData.slice(startIndex, endIndex)

    // Find max values for arrows
    const maxCallValue = Math.max(
      ...processedData.map((item) => item.callValue)
    )
    const maxPutValue = Math.max(
      ...processedData.map((item) => Math.abs(item.putValue))
    )
    const maxCallIndex = processedData.findIndex(
      (item) => item.callValue === maxCallValue
    )
    const maxPutIndex = processedData.findIndex(
      (item) => Math.abs(item.putValue) === maxPutValue
    )

    const option = {
      grid: {
        top: '10%',
        right: '5%',
        bottom: '15%',
        left: '5%',
        containLabel: false,
      },
      xAxis: [
        {
          type: 'category',
          data: processedData.map((item) => item.strike_price),
          position: 'bottom',
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
            show: true,
            color: '#FFFFFF',
            fontSize: 9,
            interval: 2,
            margin: 12,
          },
        },
        {
          type: 'category',
          data: processedData.map((item) => item.strike_price),
          position: 'top',
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
            show: true,
            color: '#FFFFFF',
            fontSize: 9,
            interval: 2,
          },
        },
      ],
      yAxis: [
        {
          type: 'value',
          position: 'left',
          splitLine: {
            lineStyle: {
              color: '#FFFFFF',
              opacity: 0.2,
              type: 'dashed',
              width: 0.5,
            },
          },
          axisLine: {
            show: true,
            lineStyle: {
              color: '#FFFFFF',
              opacity: 0.2,
              width: 0.5,
            },
          },
          axisTick: { show: false },
          scale: true,
          axisLabel: {
            show: true,
            color: '#FFFFFF',
            fontSize: 9,
            margin: 12,
            showMinLabel: true,
            showMaxLabel: true,
            formatter: (value: number) => {
              const absValue = Math.abs(value)
              let formattedValue: string
              let suffix = ''

              if (absValue >= 1000000) {
                formattedValue = (absValue / 1000000).toFixed(1)
                if (formattedValue.endsWith('.0')) {
                  formattedValue = formattedValue.slice(0, -2)
                }
                suffix = 'M'
              } else if (absValue >= 1000) {
                formattedValue = (absValue / 1000).toFixed(1)
                if (formattedValue.endsWith('.0')) {
                  formattedValue = formattedValue.slice(0, -2)
                }
                suffix = 'K'
              } else {
                formattedValue = absValue.toString()
              }

              return formattedValue + suffix
            },
          },
        },
        {
          type: 'value',
          position: 'right',
          splitLine: { show: false },
          axisLine: {
            show: true,
            lineStyle: {
              color: '#FFFFFF',
              opacity: 0.2,
              width: 0.5,
            },
          },
          axisTick: { show: false },
          scale: true,
          axisLabel: {
            show: true,
            color: '#FFFFFF',
            fontSize: 9,
            margin: 12,
            showMinLabel: true,
            showMaxLabel: true,
            formatter: (value: number) => {
              const absValue = Math.abs(value)
              let formattedValue: string
              let suffix = ''

              if (absValue >= 1000000) {
                formattedValue = (absValue / 1000000).toFixed(1)
                if (formattedValue.endsWith('.0')) {
                  formattedValue = formattedValue.slice(0, -2)
                }
                suffix = 'M'
              } else if (absValue >= 1000) {
                formattedValue = (absValue / 1000).toFixed(1)
                if (formattedValue.endsWith('.0')) {
                  formattedValue = formattedValue.slice(0, -2)
                }
                suffix = 'K'
              } else {
                formattedValue = absValue.toString()
              }

              return formattedValue + suffix
            },
          },
        },
      ],
      series: [
        {
          name: 'Calls',
          type: 'bar',
          stack: 'total',
          data: processedData.map((item) => ({
            value: item.callValue,
            itemStyle: {
              color: '#28BF94',
              borderRadius: [3, 3, 0, 0],
            },
          })),
          barWidth: 6,
          z: 2,
          markPoint:
            maxCallIndex >= 0
              ? {
                  symbol: 'triangle',
                  symbolSize: 8,
                  data: [
                    {
                      coord: [
                        maxCallIndex,
                        processedData[maxCallIndex].callValue,
                      ],
                      symbolRotate: 180,
                      itemStyle: {
                        color: '#28BF94',
                      },
                      symbolOffset: [0, -15],
                    },
                  ],
                }
              : undefined,
        },
        {
          name: 'Puts',
          type: 'bar',
          stack: 'total',
          data: processedData.map((item) => ({
            value: item.putValue,
            itemStyle: {
              color: '#CD4B38',
              borderRadius: [0, 0, 3, 3],
            },
          })),
          barWidth: 6,
          z: 2,
          markPoint:
            maxPutIndex >= 0
              ? {
                  symbol: 'triangle',
                  symbolSize: 8,
                  data: [
                    {
                      coord: [maxPutIndex, processedData[maxPutIndex].putValue],
                      symbolRotate: 0,
                      itemStyle: {
                        color: '#CD4B38',
                      },
                      symbolOffset: [0, 15],
                    },
                  ],
                }
              : undefined,
        },
        // Add a dummy series to force right axis to show same scale
        {
          type: 'bar',
          yAxisIndex: 1,
          data: [
            ...processedData.map((item) => ({
              value: item.callValue,
            })),
            ...processedData.map((item) => ({
              value: item.putValue,
            })),
          ],
          stack: 'total2',
          silent: true,
          itemStyle: { opacity: 0 },
        },
        {
          name: 'Current Price',
          type: 'line',
          markLine: {
            silent: true,
            symbol: ['none', 'none'],
            animation: false,
            data: [
              {
                xAxis: centerIndex - startIndex,
                label: {
                  show: true,
                  position: 'end',
                  formatter: lastPrice.toFixed(2),
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
          const callValue = params[0].value
          const putValue = Math.abs(params[1].value) // Show absolute value in tooltip
          return `<div>
            <div style="font-size: 12px; color: #999;">Strike: $${strike}</div>
            <div style="margin-top: 4px;">
              <span style="color: #28BF94">Calls: ${callValue.toLocaleString()}</span>
            </div>
            <div style="margin-top: 2px;">
              <span style="color: #CD4B38">Puts: ${putValue.toLocaleString()}</span>
            </div>
          </div>`
        },
      },
    }

    chart.setOption(option)

    // Add resize listener
    const handleResize = () => {
      if (chartInstance.current) {
        chartInstance.current.resize()
      }
    }
    window.addEventListener('resize', handleResize)

    // Return cleanup function
    return () => {
      window.removeEventListener('resize', handleResize)
      cleanup()
    }
  }, [response, side, expiration]) // Dependencies that should trigger chart re-render

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (chartInstance.current) {
        chartInstance.current.dispose()
        chartInstance.current = null
      }
    }
  }, [])

  if (!expiration) {
    return (
      <div className='flex h-[250px] w-full items-center justify-center text-gray-400'>
        Please select an expiration date
      </div>
    )
  }

  if (isLoading || !response || !response.data || !response.lastPrice) {
    return (
      <div className='h-[250px] w-full'>
        <Loader />
      </div>
    )
  }

  return (
    <div className='h-[250px] w-full'>
      <div ref={chartRef} style={{ width: '100%', height: '100%' }} />
    </div>
  )
}

export default StrikePriceChart
