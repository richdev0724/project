import { Button } from '@/components/custom/button'

interface DataTablePaginationProps {
  pageIndex: number
  pageCount: number | undefined
  handlePageChange: (page: number) => void
}

const NewsPagination = ({
  pageIndex,
  pageCount = 0,
  handlePageChange,
}: DataTablePaginationProps) => {
  return (
    <div className='flex items-center'>
      <Button
        variant='ghost'
        className='h-8 w-8 p-0'
        onClick={() => handlePageChange(pageIndex - 1)}
        disabled={pageIndex === 0}
      >
        <span className='sr-only'>Go to previous page</span>
        <span className='flex h-4 w-4 items-center justify-center text-primary'>
          {'<'}
        </span>
      </Button>
      <span className='text-sm text-popOverBorder'>Page {pageIndex + 1}</span>
      <Button
        variant='ghost'
        className='h-8 w-8 p-0'
        onClick={() => handlePageChange(pageIndex + 1)}
        disabled={pageIndex >= pageCount - 1}
      >
        <span className='sr-only'>Go to next page</span>
        <span className='flex h-4 w-4 items-center justify-center text-primary'>
          {'>'}
        </span>
      </Button>
    </div>
  )
}

export default NewsPagination
