import React from 'react'
import { useBookMarkFlow } from '@/hooks/use-bookmark-flow'
import { BookmarkButton } from './bookmark-button'

interface Props {
  row: any
}

export const BookmarkManager = ({ row }: Props) => {
  const { handleBookMark, bookmarks } = useBookMarkFlow()

  const rowId = String(row.original._id)
  const rowIds = row.original._ids || []

  const bookmarksArray = React.useMemo(
    () =>
      bookmarks?.map((bookmark: any) => String(bookmark.option_trade_id)) || [],
    [bookmarks]
  )

  const isBookmarked =
    rowIds.length > 0
      ? rowIds.every((id: string) => bookmarksArray.includes(id))
      : bookmarksArray.includes(rowId)

  const toggleBookmark = React.useCallback(() => {
    const idsToSend = rowIds.length > 0 ? rowIds : [rowId]
    handleBookMark(idsToSend, isBookmarked ? 'uncheck' : 'check')
  }, [handleBookMark, rowId, rowIds])

  return (
    <BookmarkButton
      key={rowId}
      rowId={rowId}
      rowIds={rowIds}
      isActive={isBookmarked}
      onBookmarkToggle={toggleBookmark}
    />
  )
}
