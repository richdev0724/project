import { BookMarkIcon } from '@/assets/icons/bookmarkIcon'
import { Button } from '@/components/custom/button'
import React from 'react'

interface Props {
  rowId: string
  rowIds: string[]
  isActive: boolean
  onBookmarkToggle: (id: string) => void
}

export const BookmarkButton = React.memo(
  ({ rowId, isActive, onBookmarkToggle }: Props) => {
    const handleClick = () => {
      onBookmarkToggle(rowId)
    }

    return (
      <Button size='icon' variant='ghost' onClick={handleClick} className='h-7'>
        <BookMarkIcon isActive={isActive} />
      </Button>
    )
  }
)
