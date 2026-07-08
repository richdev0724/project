import { useState } from 'react'

interface Props {
  symbol: string | undefined
}

const LogoWithFallback = ({ symbol }: Props) => {
  const [svgError, setSvgError] = useState(false)
  const [pngError, setPngError] = useState(false)
  const fallbackText = symbol ? symbol.slice(0, 2).toUpperCase() : ''

  // If both SVG and PNG failed, show letter fallback
  if ((svgError && pngError) || !symbol) {
    return (
      <div className='ml-4 flex h-12 w-12 items-center justify-center rounded-lg border border-amber-500/30 bg-slate-800 shadow-lg shadow-amber-500/20'>
        <span className='text-sm font-bold text-amber-500'>{fallbackText}</span>
      </div>
    )
  }

  // If SVG failed, try PNG from ticker_icons
  if (svgError && !pngError) {
    return (
      <div className='ml-4 flex h-12 w-12 items-center justify-center rounded-lg border border-gray-300/30 bg-white shadow-lg shadow-blue-500/20'>
        <img
          alt={`${symbol} logo`}
          className='h-8 w-8 object-contain'
          src={`/src/assets/ticker_icons/${symbol}.png`}
          onError={() => setPngError(true)}
          title={symbol}
        />
      </div>
    )
  }

  // Try SVG first (primary method)
  return (
    <div className='ml-4 flex h-12 w-12 items-center justify-center rounded-lg border border-gray-300/30 bg-white shadow-lg shadow-blue-500/20'>
      <img
        alt={`${symbol} logo`}
        className='h-8 w-8 object-contain'
        src={`/src/assets/stock-logos/${symbol === 'PRN' ? 'PRN_' : symbol}.svg`}
        onError={() => setSvgError(true)}
        title={symbol}
      />
    </div>
  )
}

export default LogoWithFallback
