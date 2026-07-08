import { useState, useEffect } from 'react'
import { logoService } from '@/api/logo.service'

interface UseLogoResult {
  logoUrl: string | null
  isLoading: boolean
  error: string | null
  fallbackText: string
}

export const useLogo = (ticker: string | undefined): UseLogoResult => {
  const [logoUrl, setLogoUrl] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!ticker) {
      setLogoUrl(null)
      setIsLoading(false)
      setError(null)
      return
    }

    // Clear cache before fetching to ensure fresh data
    logoService.clearCache()

    const fetchLogo = async () => {
      setIsLoading(true)
      setError(null)

      try {
        const url = await logoService.getLogo(ticker)
        if (url) {
          setLogoUrl(url)
        } else {
          setError('Logo not found')
          setLogoUrl(null)
        }
      } catch (err) {
        // Error fetching logo
        setError('Failed to load logo')
        setLogoUrl(null)
      } finally {
        setIsLoading(false)
      }
    }

    fetchLogo()
  }, [ticker])

  const fallbackText = ticker ? ticker.slice(0, 4).toUpperCase() : ''

  return {
    logoUrl,
    isLoading,
    error,
    fallbackText,
  }
}
