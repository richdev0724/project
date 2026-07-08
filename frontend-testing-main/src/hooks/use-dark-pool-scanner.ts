import { useQuery, Query } from '@tanstack/react-query'

interface DarkPoolScannerResult {
  status: string
  data?: any[]
  lastScanTime?: string
}

const fetchDarkPoolScanner = async (): Promise<DarkPoolScannerResult> => {
  // Get today's date in local timezone
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    .toISOString()
    .split('T')[0]

  try {
    const res = await fetch(`/api/dark-pool/scanner?date=${today}`)
    if (!res.ok) {
      throw new Error(`Failed to fetch dark pool data: ${res.statusText}`)
    }
    return res.json()
  } catch (_error) {
    // Error fetching dark pool data
    return { status: 'error', data: [] }
  }
}

export const useDarkPoolScanner = () => {
  // Get today's date in local timezone
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    .toISOString()
    .split('T')[0]

  return useQuery<DarkPoolScannerResult>({
    queryKey: ['marketDataAnalysis', 'darkPoolScanner', today],
    queryFn: fetchDarkPoolScanner,
    refetchInterval: (query: Query<DarkPoolScannerResult, Error>) =>
      query.state.data?.status === 'running' ? 3000 : false, // Poll every 3s if running
    refetchOnWindowFocus: true,
  })
}
