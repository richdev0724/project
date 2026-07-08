import Loader from '@/components/loader'
import { useRecentMacro } from '@/hooks/use-recent-macro'
import { useStockPrices } from '@/hooks/use-stock-prices'
import { CrosshairMode, IChartApi, createChart } from 'lightweight-charts'
import { useEffect, useRef } from 'react'

const today = new Date()
const getToday = today.toISOString().split('T')[0]
// const p60days = new Date(today.setDate(today.getDate() - 60))
// const past60days = p60days.toISOString().split('T')[0]
const p74days = new Date(today.setDate(today.getDate() - 74))
const past74Days = p74days.toISOString().split('T')[0]

export default function OptionsActivityChart() {
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
    data: macroData,
    isLoading: isLoadingMacro,
    isError: isErrorMacro,
  } = useRecentMacro(60)

  const bullData = macroData?.map((item) => ({
    time: item.date,
    value: parseFloat(item.percent_call_opening), // Ensure value is a number
  }))

  const bearData = macroData?.map((item) => ({
    time: item.date,
    value: parseFloat(item.percent_put_opening), // Ensure value is a number
  }))

  useEffect(() => {
    if (
      chartContainerRef.current &&
      !chartRef.current &&
      !isLoading &&
      !isError &&
      !isLoadingMacro &&
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
          mouseWheel: false,
          pressedMouseMove: true,
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
      const bullishAverageSeries = chart.addHistogramSeries({
        color: '#06b6d4',
        priceFormat: {
          type: 'volume',
        },
        priceScaleId: '', // set as an overlay by setting a blank priceScaleId
        // scaleMargins: {
        //   top: 0.7,
        //   bottom: 0,
        // },
        // overlay: true,
      })

      bullishAverageSeries.priceScale().applyOptions({
        scaleMargins: {
          top: 0.9,
          bottom: 0,
        },
        // overlay: true,
      })

      const bearishAverageSeries = chart.addHistogramSeries({
        color: 'rgba(244, 63, 94, 0.3)',
        priceFormat: {
          type: 'volume',
        },
        priceScaleId: '', // set as an overlay by setting a blank priceScaleId
        // scaleMargins: {
        //   top: 0.7,
        //   bottom: 0,
        // },
        // overlay: true,
      })
      bearishAverageSeries.priceScale().applyOptions({
        scaleMargins: {
          top: 0.9,
          bottom: 0,
        },
        // overlay: true,
      })

      // Set data for histogram series
      if (bullData?.length && bearData?.length) {
        bullishAverageSeries.setData(bullData)
        bearishAverageSeries.setData(bearData)
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
  }, [chartData, bullData, bearData])

  if (isLoading || isLoadingMacro) {
    return <Loader />
  }

  return (
    <div
      ref={chartContainerRef}
      className='h-full min-h-[300px] w-full sm:min-h-[400px] md:min-h-[600px] lg:min-h-[800px]'
    />
  )
}
