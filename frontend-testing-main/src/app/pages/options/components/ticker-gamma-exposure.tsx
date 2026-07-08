import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import * as echarts from 'echarts'
import {
  useGammaExposure,
  useExpirationDates,
} from '@/hooks/use-gamma-exposure'
import Loader from '@/components/loader'
import { ExpirationSelectDropdown } from '@/components/custom/expiration-select-dropdown'

interface DataItem {
  strike_price: number
  call_gamma: number
  put_gamma: number
  net_gamma: number
  positive_gex: number
  negative_gex: number
}

const TickerGammaExposure = () => {
  const { symbol } = useParams()
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)
  const [selectedExpiration, setSelectedExpiration] = useState<string>('')

  const { data: expirationDates, isLoading: isLoadingDates } =
    useExpirationDates(symbol || '')
  const {
    data: response,
    isLoading: isLoadingGamma,
    isError,
  } = useGammaExposure({
    ticker: symbol || '',
    expiration: selectedExpiration,
  })

  // Set initial expiration date
  useEffect(() => {
    // Current symbol and expiration dates
    // Received expiration dates
    if (expirationDates?.length > 0 && !selectedExpiration) {
      // Setting initial expiration date
      setSelectedExpiration(expirationDates[0])
    }
  }, [expirationDates, selectedExpiration])

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
    const lastPrice = response.lastPrice ?? 0
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

    const option = {
      grid: {
        top: '10%',
        right: '5%',
        bottom: '15%',
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
          show: true,
          color: '#FFFFFF',
          fontSize: 9,
          interval: 2,
          margin: 12,
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

            return prefix + formattedValue + suffix
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
          const netGamma = params[0].value
          const formattedNetGamma = netGamma.toLocaleString()
          return `<div class="flex flex-col gap-2">
            <div class="text-xs text-gray-300">Strike: $${strike}</div>
            <div class="flex gap-4 justify-between items-center">
              <div class="flex gap-2 items-center">
                <span class="text-xs text-gray-300">Net Gamma:</span>
                <span class="text-xs font-medium" style="color: ${netGamma >= 0 ? '#28BF94' : '#CD4B38'}">${formattedNetGamma}</span>
              </div>
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
  }, [response, selectedExpiration]) // Dependencies that should trigger chart re-render

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (chartInstance.current) {
        chartInstance.current.dispose()
        chartInstance.current = null
      }
    }
  }, [])

  // Render loading, error, and empty states
  if (!selectedExpiration) {
    return (
      <div className='h-[404px] w-full px-4 pb-4 pt-3'>
        <div className='mb-2 flex items-center justify-between pl-2 text-sm'>
          <div className='flex w-fit items-center'>
            <div>
              <span className='whitespace-nowrap text-sm font-normal'>
                Gamma Exposure |
              </span>
            </div>
            <div className='ml-2'>
              <span className='text-sm font-medium text-[#28BF94]'>
                Net Gamma
              </span>
            </div>
          </div>
          <div>
            <span className='text-sm font-normal'>{symbol}</span>
          </div>
        </div>
        <div className='flex h-[315px] w-full items-center justify-center text-gray-400'>
          Please select an expiration date
        </div>
        <div className='flex justify-end'>
          <ExpirationSelectDropdown
            setExpiration={(date) => {
              // User selected new expiration date
              setSelectedExpiration(date)
            }}
            expiration={selectedExpiration}
            expirationDates={expirationDates || []}
          />
        </div>
      </div>
    )
  }

  if (
    isLoadingDates ||
    isLoadingGamma ||
    !response?.data ||
    !response.data.length
  ) {
    return (
      <div className='h-[404px] w-full px-4 pb-4 pt-3'>
        <div className='mb-2 flex items-center justify-between pl-2 text-sm'>
          <div className='flex w-fit items-center'>
            <div>
              <span className='whitespace-nowrap text-sm font-normal'>
                Gamma Exposure |
              </span>
            </div>
            <div className='ml-2'>
              <span className='text-sm font-medium text-[#28BF94]'>
                Net Gamma
              </span>
            </div>
          </div>
          <div>
            <span className='text-sm font-normal'>{symbol}</span>
          </div>
        </div>
        <div className='h-[315px] w-full'>
          <Loader />
        </div>
        <div className='flex justify-end'>
          <ExpirationSelectDropdown
            setExpiration={(date) => {
              // User selected new expiration date
              setSelectedExpiration(date)
            }}
            expiration={selectedExpiration}
            expirationDates={expirationDates || []}
          />
        </div>
      </div>
    )
  }

  if (isError) {
    return (
      <div className='h-[404px] w-full px-4 pb-4 pt-3'>
        <div className='mb-2 flex items-center justify-between pl-2 text-sm'>
          <div className='flex w-fit items-center'>
            <div>
              <span className='whitespace-nowrap text-sm font-normal'>
                Gamma Exposure |
              </span>
            </div>
            <div className='ml-2'>
              <span className='text-sm font-medium text-[#28BF94]'>
                Net Gamma
              </span>
            </div>
          </div>
          <div>
            <span className='text-sm font-normal'>{symbol}</span>
          </div>
        </div>
        <div className='flex h-[315px] w-full items-center justify-center text-red-400'>
          Error loading data
        </div>
        <div className='flex justify-end'>
          <ExpirationSelectDropdown
            setExpiration={(date) => {
              // User selected new expiration date
              setSelectedExpiration(date)
            }}
            expiration={selectedExpiration}
            expirationDates={expirationDates || []}
          />
        </div>
      </div>
    )
  }

  return (
    <div className='h-[404px] w-full px-4 pb-4 pt-3'>
      <div className='mb-2 flex items-center justify-between pl-2 text-sm'>
        <div className='flex w-fit items-center'>
          <div>
            <span className='whitespace-nowrap text-sm font-normal'>
              Gamma Exposure |
            </span>
          </div>
          <div className='ml-2'>
            <span className='text-sm font-medium text-[#28BF94]'>
              Net Gamma
            </span>
          </div>
        </div>
        <div>
          <span className='text-sm font-normal'>{symbol}</span>
        </div>
      </div>
      <div className='h-[315px] w-full'>
        <div ref={chartRef} style={{ width: '100%', height: '100%' }} />
      </div>
      <div className='flex justify-end'>
        <ExpirationSelectDropdown
          setExpiration={(date) => {
            // User selected new expiration date
            setSelectedExpiration(date)
          }}
          expiration={selectedExpiration}
          expirationDates={expirationDates || []}
        />
      </div>
    </div>
  )
}

export default TickerGammaExposure
