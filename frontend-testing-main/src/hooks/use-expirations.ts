import { miscellaneousService } from '@/api'
import { useQuery } from '@tanstack/react-query'

interface ExpirationProps {
  symbol: string
}

export const useExpirations = ({ symbol }: ExpirationProps) => {
  return useQuery({
    queryKey: ['options', 'expirations'],
    queryFn: async () => {
      const { data } =
        await miscellaneousService.getTickerOptionExpirations(symbol)
      return data
    },
  })
}
