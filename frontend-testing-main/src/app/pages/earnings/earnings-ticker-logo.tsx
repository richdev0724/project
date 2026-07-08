import { useState } from 'react'

interface EarningsTickerLogoProps {
  symbol: string
}

export default function EarningsTickerLogo({
  symbol,
}: EarningsTickerLogoProps) {
  const [svgError, setSvgError] = useState(false)
  const [pngError, setPngError] = useState(false)
  const fallbackLetter = symbol ? symbol.charAt(0).toUpperCase() : ''

  // If both SVG and PNG failed, show letter fallback
  if ((svgError && pngError) || !symbol) {
    return (
      <div className='flex h-8 w-8 items-center justify-center rounded-md bg-slate-800 text-xs font-medium'>
        {fallbackLetter}
      </div>
    )
  }

  // If SVG failed, try PNG from ticker_icons
  if (svgError && !pngError) {
    return (
      <div className='flex h-8 w-8 items-center justify-center rounded-md bg-slate-800'>
        <img
          alt={`${symbol} logo`}
          className='h-6 w-6 object-contain'
          src={`/src/assets/ticker_icons/${symbol}.png`}
          onError={() => setPngError(true)}
        />
      </div>
    )
  }

  // Try SVG first (primary method)
  return (
    <img
      alt={`${symbol} logo`}
      className='h-8 w-8 rounded-md object-contain'
      src={`/src/assets/stock-logos/${symbol === 'PRN' ? 'PRN_' : symbol}.svg`}
      onError={() => setSvgError(true)}
    />
  )
}
