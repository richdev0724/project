import { filterService } from '@/api/filter.service'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

export const useUserFilter = () => {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: async ({
      filterName,
      data,
    }: {
      filterName: string
      data: any
    }) => {
      await filterService.postUserFilter({
        name: filterName,
        filter_query: JSON.stringify(data),
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['userFilters', 'filters'] })
    },
    onError: () => {},
  })

  const saveFilters = (filterName: string, data: any) => {
    mutation.mutateAsync({
      filterName,
      data,
    })
  }

  const editMutation = useMutation({
    mutationFn: async ({ id, data }: { id: number; data: any }) => {
      await filterService.updateFilterData(id, {
        filter_query: JSON.stringify(data),
      })
    },
    onSuccess: () => {},
    onError: () => {},
  })

  const updateFilter = (id: number, data: any) => {
    editMutation.mutateAsync({
      id,
      data,
    })
  }

  const { data } = useQuery({
    queryKey: ['userFilters', 'filters'],
    queryFn: async () => {
      const { data } = await filterService.getFilterData()
      return data
    },
  })

  return {
    saveFilters,
    updateFilter,
    filterData: data,
  }
}
