import { useState } from 'react'

interface TickerCardLogoProps {
  symbol: string
}

export default function TickerCardLogo({ symbol }: TickerCardLogoProps) {
  const [imageError, setImageError] = useState(false)
  const fallbackLetter = symbol ? symbol.charAt(0).toUpperCase() : ''

  if (imageError || !symbol) {
    return (
      <div className='flex items-center justify-center rounded-md bg-slate-800  sm:h-10 sm:w-10'>
        {fallbackLetter}
      </div>
    )
  }

  return (
    <img
      alt={`${symbol} logo`}
      className={'h-8 w-8 rounded-md'}
      src={`/src/assets/stock-logos/${symbol === 'PRN' ? 'PRN_' : symbol}.svg`}
      onError={() => setImageError(true)}
    />
  )
}
