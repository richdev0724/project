import React, { useState, useRef, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import LogoWithFallback from '../ticker-logo'
import { TIMEFRAME } from '@/constants'
import StockChart from './stock-chart'
import PriceDisplay from './price-display'

// Header component
interface HeaderProps {
  symbol: string
  tagLine: string
}

const Header: React.FC<HeaderProps> = ({ symbol, tagLine }) => {
  return (
    <div className='absolute left-[4px] top-[20px]'>
      <div className='flex flex-row items-center space-x-3 drop-shadow-lg'>
        <LogoWithFallback symbol={symbol} />
        <div className='-mt-1'>
          <p className='text-2xl font-bold drop-shadow-lg'>{symbol}</p>
          {tagLine && (
            <p className='-mt-1 ml-0.5 text-[12px] font-light drop-shadow-lg'>
              {tagLine}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}

// Timeframe Controls component
interface ControlsProps {
  selectedTimeFrame: string
  onTimeFrameChange: (timeframe: string) => void
}

const Controls: React.FC<ControlsProps> = ({
  selectedTimeFrame,
  onTimeFrameChange,
}) => {
  const [indicatorStyle, setIndicatorStyle] = useState({
    left: 0,
    width: 0,
    opacity: 0,
  })
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!containerRef.current) return

    const activeElement = containerRef.current.querySelector(
      `[data-timeframe="${selectedTimeFrame}"]`
    )
    if (activeElement) {
      const containerRect = containerRef.current.getBoundingClientRect()
      const elementRect = activeElement.getBoundingClientRect()
      setIndicatorStyle({
        left: elementRect.left - containerRect.left,
        width: elementRect.width,
        opacity: 1,
      })
    }
  }, [selectedTimeFrame])

  return (
    <div className='absolute -bottom-[30px] right-[17px]'>
      <div
        ref={containerRef}
        className='relative flex gap-4 text-sm font-medium'
      >
        {TIMEFRAME.map((timeframe, index) => (
          <div
            key={index}
            data-timeframe={timeframe.value}
            onClick={() => onTimeFrameChange(timeframe.value)}
            className='cursor-pointer'
          >
            <span className='inline-block'>{timeframe.label}</span>
          </div>
        ))}

        {/* Sliding indicator */}
        <div
          className='absolute bottom-0 h-[2px] rounded-b-[1px] bg-[#FFFFFF] transition-all duration-300 ease-in-out'
          style={{
            left: `${indicatorStyle.left}px`,
            width: `${indicatorStyle.width}px`,
            opacity: indicatorStyle.opacity,
          }}
        />
      </div>
    </div>
  )
}

// Main component
interface TickerChartV2Props {
  tagLine: string
}

const TickerChartV2: React.FC<TickerChartV2Props> = ({ tagLine }) => {
  const { symbol = '' } = useParams()
  const [selectedTimeFrame, setSelectedTimeFrame] = useState('1D')
  const [priceData, setPriceData] = useState({
    currentPrice: 0,
    priceChange: 0,
    percentChange: 0,
  })
  const [indicatorState, setIndicatorState] = useState<{
    activePlots: string[]
    togglePlot: (plotId: string) => void
    PLOT_OPTIONS: Array<{ id: string; label: string }>
  } | null>(null)

  const handleTimeFrame = (timeframe: string) => {
    setSelectedTimeFrame(timeframe)
  }

  const handlePriceData = (data: {
    currentPrice: number
    priceChange: number
    percentChange: number
  }) => {
    setPriceData(data)
  }

  const handleIndicatorState = (state: {
    activePlots: string[]
    togglePlot: (plotId: string) => void
    PLOT_OPTIONS: Array<{ id: string; label: string }>
  }) => {
    setIndicatorState(state)
  }

  if (!symbol) {
    return (
      <div className='flex h-[500px] w-full items-center justify-center'>
        <p className='text-gray-400'>No ticker symbol provided</p>
      </div>
    )
  }

  return (
    <div className='relative h-[440px] w-full'>
      <Header symbol={symbol} tagLine={tagLine} />

      {/* Price Display in top right */}
      <div className='absolute right-[20px] top-[20px]'>
        <PriceDisplay
          currentPrice={priceData.currentPrice}
          priceChange={priceData.priceChange}
          percentChange={priceData.percentChange}
        />
      </div>

      {/* Stock Chart in the middle */}
      <div className='absolute bottom-[-30px] left-0 right-0 top-[19px] flex items-center justify-center'>
        <div style={{ width: '800px', maxWidth: '800px' }}>
          <StockChart
            className='w-full'
            onPriceData={handlePriceData}
            onIndicatorState={handleIndicatorState}
          />
        </div>
      </div>

      <Controls
        selectedTimeFrame={selectedTimeFrame}
        onTimeFrameChange={handleTimeFrame}
      />

      {/* Indicator Toggle Controls */}
      {indicatorState && (
        <div className='absolute left-[19px] top-[450px] flex gap-4 text-sm'>
          {indicatorState.PLOT_OPTIONS.map((plot) => {
            let underlineColor = ''
            switch (plot.id) {
              case 'ema50':
                underlineColor = '#F6C85D'
                break
              case 'ema200':
                underlineColor = '#8B5CF6'
                break
              case 'dpl':
                underlineColor = '#FF9800'
                break
              case 'lpl':
                underlineColor = '#FF9800'
                break
              case 'mobe':
                underlineColor = '#FFFFFF'
                break
              default:
                underlineColor = '#FFFFFF'
            }

            return (
              <div
                key={plot.id}
                onClick={() => indicatorState.togglePlot(plot.id)}
                className='cursor-pointer'
              >
                <span
                  className={`inline-block ${
                    indicatorState.activePlots.includes(plot.id)
                      ? 'text-white'
                      : 'text-gray-400'
                  }`}
                >
                  {plot.label}
                </span>
                {indicatorState.activePlots.includes(plot.id) && (
                  <div
                    className='h-[2px] w-full rounded-b-[1px]'
                    style={{ backgroundColor: underlineColor }}
                  />
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default TickerChartV2
