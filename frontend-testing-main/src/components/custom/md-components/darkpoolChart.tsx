import { useMarketDataDarkPool } from '@/hooks/use-market-data-dark-pool'
import { useStockPrices } from '@/hooks/use-stock-prices'
import { CrosshairMode, IChartApi, createChart } from 'lightweight-charts'
import { useEffect, useRef } from 'react'

const today = new Date()
const getToday = today.toISOString().split('T')[0]
const p60days = new Date(today.setDate(today.getDate() - 60))
const past60days = p60days.toISOString().split('T')[0]
const p74days = new Date(today.setDate(today.getDate() - 74))
const past74Days = p74days.toISOString().split('T')[0]

export default function DarkPoolChart() {
  const chartContainerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<IChartApi | null>(null)

  const stockData = {
    symbol: 'SMST',
    startDate: past74Days,
    endDate: getToday,
    timeframe: 'day',
    interval: '1',
  }
  const { chartData, isLoading, isError } = useStockPrices(stockData)

  const {
    data: dpData,
    isLoading: isLoadingDarkPool,
    isError: isErrorMacro,
  } = useMarketDataDarkPool({
    showLevels: true,
    showBlockLevels: true,
    ticker: 'SMST',
    startDate: past60days,
    endDate: getToday,
    levelsAmount: 10,
    blockLevelsAmount: 10,
  })

  const darkPoolData = dpData?.map((item: any) => ({
    time: item.date,
    value: Number(item.dark), // Ensure value is a number
  }))

  useEffect(() => {
    if (
      chartContainerRef.current &&
      !chartRef.current &&
      !isLoading &&
      !isError &&
      !isLoadingDarkPool &&
      !isErrorMacro
    ) {
      const chart = createChart(chartContainerRef.current, {
        width: chartContainerRef.current.clientWidth,
        height: chartContainerRef.current.clientHeight,
        layout: {
          background: { color: 'transparent' },
          textColor: '#DDD',
        },
        grid: {
          vertLines: { color: '#1f2937' },
          horzLines: { color: '#1f2937' },
        },
        crosshair: {
          mode: CrosshairMode.Normal,
        },
        timeScale: {
          rightOffset: 0,
          barSpacing: 6,
          fixLeftEdge: true,
          lockVisibleTimeRangeOnResize: true,
          rightBarStaysOnScroll: true,
          borderVisible: false,
          visible: true,
          timeVisible: true,
          secondsVisible: false,
        },
        handleScroll: {
          mouseWheel: false, // Disable mouse wheel scroll
          pressedMouseMove: true, // Enable scrolling with mouse drag
        },
        handleScale: {
          axisPressedMouseMove: true, // Enable moving chart by dragging axis
          mouseWheel: false, // Disable zooming with mouse wheel
          pinch: false, // Disable pinch-to-zoom
        },
      })

      const candleSeries = chart.addCandlestickSeries({
        upColor: '#06b6d4',
      })
      chart.timeScale().scrollToPosition(0, false)

      if (chartData?.candleData.length) {
        candleSeries.setData(chartData.candleData)
        chart.timeScale().fitContent()
      }

      // Adding histogram series for the two sets of data
      const darkPoolAverageSeries = chart.addHistogramSeries({
        color: '#8b5cf6', // Set the color of the dark pool data
        priceFormat: {
          type: 'volume',
        },
        priceScaleId: '', // set as an overlay by setting a blank priceScaleId
      })
      darkPoolAverageSeries.priceScale().applyOptions({
        scaleMargins: {
          top: 0.9,
          bottom: 0,
        },
      })

      // Set data for histogram series
      if (darkPoolData?.length) {
        darkPoolAverageSeries.setData(darkPoolData)
      }

      chartRef.current = chart

      new ResizeObserver((entries) => {
        if (
          entries.length === 0 ||
          entries[0].target !== chartContainerRef.current
        ) {
          return
        }
        const { width, height } = entries[0].contentRect
        if (chartRef.current) {
          chartRef.current.applyOptions({ width, height })
        }
      }).observe(chartContainerRef.current)
    }
  }, [chartData, isLoadingDarkPool, isErrorMacro])

  return (
    <div
      ref={chartContainerRef}
      style={{ height: '860px', width: '100%' }}
    ></div>
  )
}
