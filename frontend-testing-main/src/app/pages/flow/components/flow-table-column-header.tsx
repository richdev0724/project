import { Column } from '@tanstack/react-table'

import { cn } from '@/lib/utils'

interface DataTableColumnHeaderProps<TData, TValue>
  extends React.HTMLAttributes<HTMLDivElement> {
  column: Column<TData, TValue>
  title: string
}

export function DataTableColumnHeader<TData, TValue>({
  title,
  className,
}: Omit<DataTableColumnHeaderProps<TData, TValue>, 'column'>) {
  // Since data is pre-sorted from backend, disable all sorting
  return <div className={cn('text-xs font-normal', className)}>{title}</div>
}
