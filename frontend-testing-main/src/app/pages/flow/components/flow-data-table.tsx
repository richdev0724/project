import {
  ColumnDef,
  ColumnFiltersState,
  SortingState,
  VisibilityState,
  flexRender,
  getCoreRowModel,
  getFacetedRowModel,
  getFacetedUniqueValues,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
} from '@tanstack/react-table'
import * as React from 'react'

import {
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from '@/components/ui/table'
import Loader from '@/components/loader'

interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[]
  data: TData[]
  isLoading?: boolean // Added loading prop
}

export function DataTable<TData, TValue>({
  columns,
  data,
  isLoading = false, // Default to false
}: DataTableProps<TData, TValue>) {
  const [rowSelection, setRowSelection] = React.useState({})
  const [columnVisibility, setColumnVisibility] =
    React.useState<VisibilityState>({})
  const [columnFilters, setColumnFilters] = React.useState<ColumnFiltersState>(
    []
  )
  const [sorting, setSorting] = React.useState<SortingState>([])

  const table = useReactTable({
    data,
    columns,
    state: {
      sorting,
      columnVisibility,
      rowSelection,
      columnFilters,
    },
    enableRowSelection: true,
    onRowSelectionChange: setRowSelection,
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFacetedRowModel: getFacetedRowModel(),
    getFacetedUniqueValues: getFacetedUniqueValues(),
  })

  const getRowBorderData = (row: any) => {
    const price = parseFloat(row.original._source.price)
    const bid = parseFloat(row.original._source.bid)
    const ask = parseFloat(row.original._source.ask)
    const midPrice = parseFloat(row.original._source.mid_price)
    const contractType = row.original._source.type

    // Determine side based on same logic as side column
    let side = ''
    if (
      !isNaN(price) &&
      price !== 0 &&
      (bid === 0 || ask === 0 || isNaN(bid) || isNaN(ask))
    ) {
      side = 'NONE'
    } else if (
      !isNaN(price) &&
      !isNaN(bid) &&
      !isNaN(ask) &&
      bid !== 0 &&
      ask !== 0
    ) {
      if (!isNaN(midPrice)) {
        if (price > midPrice && price <= ask) {
          side = 'ASK'
        } else if (price < midPrice && price >= bid) {
          side = 'BID'
        } else if (price > ask) {
          side = 'A ASK'
        } else if (price < bid) {
          side = 'B BID'
        } else if (price === midPrice) {
          side = 'MID'
        }
      } else {
        // Fallback logic if no mid price
        if (price > ask) {
          side = 'A ASK'
        } else if (price === ask) {
          side = 'ASK'
        } else if (price < bid) {
          side = 'B BID'
        } else if (price === bid) {
          side = 'BID'
        } else {
          const bidDistance = price - bid
          const askDistance = ask - price
          if (bidDistance === askDistance) {
            side = 'MID'
          } else if (bidDistance > askDistance) {
            side = 'A ASK'
          } else {
            side = 'B BID'
          }
        }
      }
    }

    // Gradient border for aggressive trades (border only, no bg fill)
    const isAggressive = side === 'B BID' || side === 'A ASK'
    if (!isAggressive) return { className: '', style: {} }

    const isAggressiveBullish =
      (contractType === 'call' && side === 'A ASK') ||
      (contractType === 'put' && side === 'B BID')

    const isAggressiveBearish =
      (contractType === 'call' && side === 'B BID') ||
      (contractType === 'put' && side === 'A ASK')

    if (isAggressiveBullish) {
      // Border gradient: left->right #9AF164 to #598B3A
      return {
        className: 'aggressive-bullish-border',
        style: {},
      }
    }

    if (isAggressiveBearish) {
      // Border gradient: left->right #F12617 to transparent
      return {
        className: 'aggressive-bearish-border',
        style: {},
      }
    }

    return { className: '', style: {} }
  }

  return (
    <div className='h-[calc(100vh-var(--header-height))] overflow-x-auto overflow-y-auto'>
      <table className='relative w-full caption-bottom text-sm'>
        <thead className='sticky top-0 z-20 bg-[#17181D] [&_tr]:border-b'>
          {table.getHeaderGroups().map((headerGroup) => (
            <TableRow key={headerGroup.id} className='h-8 bg-[#17181D]'>
              {headerGroup.headers.map((header) => {
                return (
                  <TableHead
                    key={header.id}
                    colSpan={header.colSpan}
                    className='whitespace-nowrap border-b border-[#424242] bg-[#17181D] px-2 py-1'
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                  </TableHead>
                )
              })}
            </TableRow>
          ))}
        </thead>
        <TableBody>
          {isLoading ? (
            <TableRow>
              <TableCell
                colSpan={columns.length}
                className='relative h-24 text-center'
              >
                <div className='absolute inset-0 flex items-center justify-center'>
                  <Loader />
                </div>
              </TableCell>
            </TableRow>
          ) : table.getRowModel().rows?.length ? (
            table.getRowModel().rows.map((row) => {
              const borderData = getRowBorderData(row)
              return (
                <TableRow
                  key={row.id}
                  data-state={row.getIsSelected() && 'selected'}
                  className={`h-8 ${borderData.className} text-[13px] odd:bg-TableOdd even:bg-TableEven`}
                  style={borderData.style}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      className='whitespace-nowrap px-2 py-0.5'
                    >
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext()
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              )
            })
          ) : (
            <TableRow>
              <TableCell colSpan={columns.length} className='h-24 text-center'>
                No results.
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </table>
    </div>
  )
}
