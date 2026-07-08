import { Column } from '@tanstack/react-table'

import { cn } from '@/lib/utils'

interface DataTableColumnHeaderProps<TData, TValue>
  extends React.HTMLAttributes<HTMLDivElement> {
  column: Column<TData, TValue>
  title: string
}

export function DataTableColumnHeader<TData, TValue>({
  column,
  title,
  className,
}: DataTableColumnHeaderProps<TData, TValue>) {
  if (!column.getCanSort()) {
    return <div className={cn(className)}>{title}</div>
  }

  const handleSorting = () => {
    column.toggleSorting()
  }

  return (
    <div
      className={cn(
        'flex cursor-pointer select-none items-center space-x-2 bg-none',
        className
      )}
      onClick={handleSorting}
    >
      <span className='font-medium text-primary'>{title}</span>
    </div>
  )
}
