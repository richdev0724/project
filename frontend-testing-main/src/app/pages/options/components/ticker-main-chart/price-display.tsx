import React from 'react'

interface PriceDisplayProps {
  currentPrice: number
  priceChange: number
  percentChange: number
  className?: string
}

const PriceDisplay: React.FC<PriceDisplayProps> = ({
  currentPrice,
  priceChange,
  percentChange,
  className = '',
}) => {
  return (
    <div className={`text-right ${className}`}>
      <div className='text-2xl font-bold text-white'>
        ${currentPrice.toFixed(2)}
      </div>
      <div
        className={`text-base ${priceChange >= 0 ? 'text-green-400' : 'text-red-400'}`}
      >
        {priceChange >= 0 ? '+' : ''}${priceChange.toFixed(2)} (
        {percentChange >= 0 ? '+' : ''}
        {percentChange.toFixed(2)}%)
      </div>
    </div>
  )
}

export default PriceDisplay
