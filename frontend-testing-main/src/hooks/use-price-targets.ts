import { miscellaneousService } from '@/api'
import { useQuery } from '@tanstack/react-query'

export const usePriceTargets = (payload: any) => {
  return useQuery({
    queryKey: ['misc', 'priceTargets'],
    queryFn: async () => {
      const { data } = await miscellaneousService.getPriceTargets(payload)
      return data
    },
  })
}
