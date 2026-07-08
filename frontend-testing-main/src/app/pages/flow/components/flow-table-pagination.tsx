import { Button } from '@/components/custom/button'

interface DataTablePaginationProps {
  pageIndex: number
  pageCount: number | undefined
  handlePageChange: (page: number) => void
}

export function DataTablePagination({
  pageIndex,
  pageCount = 0,
  handlePageChange,
}: DataTablePaginationProps) {
  return (
    <div className='flex items-center justify-center'>
      <Button
        variant='ghost'
        className='h-8 w-8 p-0'
        onClick={() => handlePageChange(pageIndex - 1)}
        disabled={pageIndex === 0}
      >
        <span className='sr-only'>Go to previous page</span>
        <span className='flex h-4 w-4 items-center justify-center text-xs text-primary'>
          {'<'}
        </span>
      </Button>
      <span className='mx-2 flex items-center text-xs text-popOverBorder'>
        Page {pageIndex + 1}
      </span>
      <Button
        variant='ghost'
        className='h-8 w-8 p-0'
        onClick={() => handlePageChange(pageIndex + 1)}
        disabled={pageIndex >= pageCount - 1}
      >
        <span className='sr-only'>Go to next page</span>
        <span className='flex h-4 w-4 items-center justify-center text-xs text-primary'>
          {'>'}
        </span>
      </Button>
    </div>
  )
}
