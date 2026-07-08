import { miscellaneousService } from '@/api'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

interface Bookmark {
  option_trade_id: number
}

export const useBookMarkFlow = () => {
  const queryClient = useQueryClient()

  // Fetch bookmarks with proper caching
  const { data: bookmarks = [], isLoading: isBookmarksLoading } = useQuery({
    queryKey: ['bookmarks'],
    queryFn: async () => {
      const { data } = await miscellaneousService.getBookMarkOptionTrade()
      return data
    },
    staleTime: 30000,
  })

  // Mutation for toggling multiple bookmarks
  const mutation = useMutation({
    mutationFn: async (data: {
      option_trade_ids: string[]
      selection_status: 'check' | 'uncheck'
    }) => {
      return miscellaneousService.postBookMarkOptionTrade(data)
    },
    onMutate: async (newBookmarks) => {
      await queryClient.cancelQueries({ queryKey: ['bookmarks'] })

      const previousBookmarks =
        queryClient.getQueryData<Bookmark[]>(['bookmarks']) || []

      const updatedBookmarks = previousBookmarks.filter(
        (bookmark) =>
          !newBookmarks.option_trade_ids.includes(
            String(bookmark.option_trade_id)
          )
      )

      // Optimistically add new bookmarks
      newBookmarks.option_trade_ids.forEach((id) => {
        if (!previousBookmarks.some((b) => String(b.option_trade_id) === id)) {
          updatedBookmarks.push({ option_trade_id: Number(id) })
        }
      })

      queryClient.setQueryData(['bookmarks'], updatedBookmarks)

      return { previousBookmarks }
    },
    onError: (_error, _variables, context) => {
      if (context?.previousBookmarks) {
        queryClient.setQueryData(['bookmarks'], context.previousBookmarks)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bookmarks'] })
    },
  })

  const handleBookMark = (ids: string[], flag: 'check' | 'uncheck') => {
    mutation.mutate({ option_trade_ids: ids, selection_status: flag })
  }

  return {
    handleBookMark,
    bookmarks,
    isBookmarksLoading,
  }
}
