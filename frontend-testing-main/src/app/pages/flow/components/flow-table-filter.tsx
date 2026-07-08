import OpenedFiltersIcon from '@/assets/icons/OpenedFiltersIcon'
import { Button } from '@/components/custom/button'
import { MultiSelect } from '@/components/custom/multi-select'
import { Checkbox } from '@/components/ui/checkbox'
import { Form, FormField, FormItem, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { Switch } from '@/components/ui/switch'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { useUserFilter } from '@/hooks/use-user-filter'
import { cn } from '@/lib/utils'
import { FlowFilterRequest } from '@/validations/flow.validation'
import { IconX } from '@tabler/icons-react'
import { Dispatch, SetStateAction, useEffect, useState, useRef } from 'react'
import SaveFilterPopUp from './save-filter-pop-up'
import { getFridaysOfThreeMonths } from '@/utilities/get-quarter-fridays'
import { ExpirationDateMultiSelect } from './expiration-date-select'

export interface ChildComponentProps {
  setPageIndex: Dispatch<SetStateAction<number>>
  showFilter: boolean
  toggleFilter: () => void
  form: any
  handleSubmitFilter: () => void
}

/**
 * FlowTableFilter component provides a UI for filtering flow data.
 *
 * This component allows the user to apply various filters to the flow data, including presets, tickers,
 * market cap, trade date range, expiration, DTE, premium, size, daily volume, options type, side, and other filters.
 *
 * Props:
 * - showFilter: A boolean indicating whether the filter panel is visible.
 * - toggleFilter: A function to toggle the visibility of the filter panel.
 * - form: The form object for managing filter state and actions.
 * - handleSubmitFilter: A function to handle submitting the applied filters.
 *
 * The component manages the state of selected presets and market cap values. It provides functions to handle
 * clearing all filters, selecting filter presets, saving and updating filters, and handling multi-select and option changes.
 *
 * The component renders a filter panel with various input fields and controls for the filters.
 * It includes buttons for applying, saving, and updating filters, and options for clearing specific sets of filters.
 */

export default function FlowTableFilter({
  setPageIndex,
  showFilter,
  toggleFilter,
  form,
  handleSubmitFilter,
}: ChildComponentProps) {
  const { saveFilters, filterData, updateFilter } = useUserFilter()

  // Helper function to format number with commas
  const formatNumberWithCommas = (value: string | number | null): string => {
    if (value === null || value === '') return ''
    const numStr = value.toString().replace(/,/g, '')
    if (numStr === '' || isNaN(Number(numStr))) return ''
    return Number(numStr).toLocaleString('en-US')
  }
  const [marketCapDefaultValue, setMarketCapDefaultValue] = useState<any>([])
  const [expirationDefaultValue, setExpirationDefaultValue] = useState<any>([])
  const [selectedPreset, setSelectedPreset] = useState<string>('')
  const debounceTimerRef = useRef<NodeJS.Timeout | null>(null)

  // Auto-submit filter on form value changes with debouncing
  useEffect(() => {
    const subscription = form.watch(() => {
      // Clear any existing timer
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
      }

      // Set a new timer to apply filter after 500ms of no changes
      debounceTimerRef.current = setTimeout(() => {
        setPageIndex(0)
        handleSubmitFilter()
      }, 500)
    })

    return () => {
      subscription.unsubscribe()
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current)
      }
    }
  }, [form, handleSubmitFilter, setPageIndex])

  const handleClearAll = () => {
    form.reset({
      tickers: '',
      ignoreTickers: '',
      marketCap: [],
      startDate: '',
      endDate: '',
      expiration: [],
      dteMin: null,
      dteMax: null,
      minimumPremium: null,
      maximumPremium: null,
      minimumSize: null,
      maximumSize: null,
      minimumVolume: null,
      maximumVolume: null,
      calls: null,
      puts: null,
      sweeps: null,
      floor: null,
      cross: null,
      bid: null,
      mid: null,
      ask: null,
      noSide: null,
      singleLegOnly: null,
      'volume>Oi': null,
      'size>Oi': null,
      openingTrade: null,
      excludeDeepItm: null,
      otmOnly: null,
      itmOnly: null,
      multilegOnly: null,
      singlelegOnly: null,
      aggregatedTrades: null,
      hideExpired: null,
    })
    setPageIndex(0)
    setMarketCapDefaultValue([])
    setExpirationDefaultValue([])
    setSelectedPreset('')
  }

  const handleOptionChange = (
    option: keyof FlowFilterRequest,
    isShow: boolean,
    checked: boolean | 'indeterminate'
  ) => {
    const isChecked = checked === true

    if (isShow) {
      if (isChecked) {
        form.setValue(option, true)
      } else if (form.getValues(option) === true) {
        form.setValue(option, null)
      }
    } else {
      if (isChecked) {
        form.setValue(option, false)
      } else if (form.getValues(option) === false) {
        form.setValue(option, null)
      }
    }
  }

  const handleMarketCapMultiSelect = (data: string[]) => {
    form.setValue('marketCap', data)
  }

  const handleExpirationDateMultiSelect = (data: string[]) => {
    form.setValue('expiration', data)
  }

  const handleSaveFilter = (filterName: string) => {
    saveFilters(filterName, form.getValues())
  }

  const handleUpdateFilter = () => {
    updateFilter(filterData[selectedPreset].id, form.getValues())
  }

  const handleFilterPresetSelect = (index: number) => {
    const filter = JSON.parse(filterData[index].filter_query)
    for (const prop in filter) {
      form.setValue(prop, filter[prop])
    }
    form.setValue('marketCap', filter.marketCap || [])
    setMarketCapDefaultValue(filter.marketCap || [])
    form.setValue('expiration', filter.expiration || [])
    setExpirationDefaultValue(filter.expiration || [])
  }

  const handleOtherFilterOptionSwitch = (option: string, checked: boolean) => {
    if (checked) {
      form.setValue(option, true)
    } else {
      form.setValue(option, null)
    }
  }

  const handleApplyFilter = () => {
    setPageIndex(0)
    handleSubmitFilter()
    // Don't close the filter menu - let user keep it open to see results
    // toggleFilter()
  }

  useEffect(() => {
    if (selectedPreset !== '') {
      handleFilterPresetSelect(Number(selectedPreset))
    }
  }, [selectedPreset])

  return (
    <div>
      <Button
        type='button'
        variant={'ghost'}
        size={'default'}
        className='gap-2 px-0'
        onClick={toggleFilter}
      >
        <OpenedFiltersIcon className='font-normal' />
        <span className='text-[0.65rem] font-normal text-primary'>Filters</span>
      </Button>
      <div
        className={cn(
          'fixed -left-[216px] bottom-0 top-[105px] z-30 ml-0 w-[216px] overflow-y-auto border-r border-[#424242] bg-[#1F2025] transition-all duration-300',
          showFilter && 'flow-filter-wrapper left-48 z-40'
        )}
      >
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleApplyFilter)}>
            {/* Single sticky section for header + filters */}
            <div className='sticky top-0 z-30 bg-[#1F2025]'>
              {/* Header: Clear All and X button */}
              <div className='flex h-[var(--header-height)] items-center justify-between border-b border-[#424242] px-4'>
                <div
                  className='flex cursor-pointer items-center gap-2 text-xs font-medium text-primary transition-colors hover:text-primary/80'
                  onClick={handleClearAll}
                >
                  Clear All
                </div>
                <Button
                  variant='ghost'
                  className='h-auto p-0'
                  onClick={toggleFilter}
                >
                  <IconX stroke={2} size={18} />
                </Button>
              </div>
              {/* Filters: Presets, Include/Ignore Tickers, Expiration */}
              <div className='space-y-2.5 px-4 py-2.5'>
                <div className='space-y-1'>
                  <Label className='block text-[0.7rem] font-medium text-gray-300'>
                    Presets
                  </Label>
                  <Select
                    value={selectedPreset}
                    onValueChange={(value) => {
                      setSelectedPreset(value)
                    }}
                  >
                    <SelectTrigger className='flex h-7 justify-between rounded-sm border border-white/10 !bg-[#17181D] pl-2 !text-[0.6rem] leading-tight shadow-sm focus-within:border-sky-400 focus-within:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0'>
                      <SelectValue placeholder='' />
                    </SelectTrigger>
                    <SelectContent className='rounded-sm border border-inputBorderColor !bg-[#17181D] !text-[0.6rem] leading-tight'>
                      {filterData &&
                        filterData.map((item: any, index: number) => (
                          <SelectItem key={index} value={index.toString()}>
                            {item.name}
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className='space-y-1'>
                  <Label className='block text-[0.7rem] font-medium text-gray-300'>
                    Include Tickers
                  </Label>
                  <FormField
                    name='tickers'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'SPY, AAPL, TSLA'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value ? 'border-sky-400' : 'border-white/10'
                          )}
                          title='Enter ticker symbols separated by commas (e.g., SPY, AAPL, TSLA)'
                        />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='space-y-1'>
                  <Label className='block text-[0.7rem] font-medium text-gray-300'>
                    Ignore Tickers
                  </Label>
                  <FormField
                    name='ignoreTickers'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'VIX, SPX, QQQ'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value ? 'border-sky-400' : 'border-white/10'
                          )}
                          title='Enter ticker symbols to exclude, separated by commas (e.g., VIX, SPX, QQQ)'
                        />
                      </FormItem>
                    )}
                  />
                </div>
                <div className='space-y-1'>
                  <Label className='block text-[0.7rem] font-medium text-gray-300'>
                    Expiration
                  </Label>
                  <FormField
                    name='expiration'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <ExpirationDateMultiSelect
                          className='h-auto min-h-7 border border-white/10 !text-[0.6rem] leading-tight shadow-sm focus-within:border-sky-400 focus-within:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus-visible:ring-0 focus-visible:ring-offset-0'
                          onExposeClearState={() => {}}
                          options={getFridaysOfThreeMonths()}
                          value={field.value}
                          onValueChange={(value) => {
                            field.onChange(value)
                            handleExpirationDateMultiSelect(value)
                          }}
                          defaultValue={expirationDefaultValue}
                          maxCount={2}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
            </div>
            {/* Scrollable filters section */}
            <div className='space-y-2.5 px-4 py-2.5'>
              <div className='space-y-1'>
                <Label className='block text-[0.7rem] font-medium text-gray-300'>
                  Market Cap
                </Label>
                <FormField
                  name='marketCap'
                  control={form.control}
                  render={({ field }) => (
                    <FormItem>
                      <MultiSelect
                        className='h-7 min-h-7 border border-white/10 !text-[0.6rem] leading-tight shadow-sm focus-within:border-sky-400 focus-within:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus-visible:ring-0 focus-visible:ring-offset-0'
                        onExposeClearState={() => {}}
                        options={[
                          { label: 'Micro Cap', value: 'micro' },
                          { label: 'Small Cap', value: 'small' },
                          { label: 'Mid Cap', value: 'mid' },
                          { label: 'Large Cap', value: 'large' },
                          { label: 'Mega Cap', value: 'mega' },
                        ]}
                        {...field}
                        onValueChange={handleMarketCapMultiSelect}
                        defaultValue={marketCapDefaultValue}
                      />
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <div className='space-y-1'>
                <Label className='block text-[0.7rem] font-medium text-gray-300'>
                  Trade Date Range
                </Label>
                <div className='flex items-center gap-1.5'>
                  <FormField
                    name='startDate'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'mm/dd/yy'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0',
                            field.value ? 'border-sky-400' : 'border-white/10'
                          )}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem] text-gray-500'>-</span>
                  <FormField
                    name='endDate'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'mm/dd/yy'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0',
                            field.value ? 'border-sky-400' : 'border-white/10'
                          )}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
              <div className='space-y-1'>
                <Label className='block text-[0.7rem] font-medium text-gray-300'>
                  DTE
                </Label>
                <div className='flex items-center gap-1.5'>
                  <FormField
                    name='dteMin'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'0'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              (!isNaN(Number(value)) && value !== '.')
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem] text-gray-500'>-</span>
                  <FormField
                    name='dteMax'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'365'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              (!isNaN(Number(value)) && value !== '.')
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
              <div className='space-y-1'>
                <Label className='block text-[0.7rem] font-medium text-gray-300'>
                  Premium
                </Label>
                <div className='flex items-center gap-1.5'>
                  <FormField
                    name='minimumPremium'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'0'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              !isNaN(Number(value))
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem] text-gray-500'>-</span>
                  <FormField
                    name='maximumPremium'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'2,500,000'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              !isNaN(Number(value))
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
              <div className='space-y-1'>
                <Label className='block text-[0.7rem] font-medium text-gray-300'>
                  Size
                </Label>
                <div className='flex items-center gap-1.5'>
                  <FormField
                    name='minimumSize'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'0'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              !isNaN(Number(value))
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem] text-gray-500'>-</span>
                  <FormField
                    name='maximumSize'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'1,000,000'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              !isNaN(Number(value))
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
              <div className='space-y-1'>
                <Label className='block text-[0.7rem] font-medium text-gray-300'>
                  Daily Volume
                </Label>
                <div className='flex items-center gap-1.5'>
                  <FormField
                    name='minimumVolume'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'0'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              !isNaN(Number(value))
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem] text-gray-500'>-</span>
                  <FormField
                    name='maximumVolume'
                    control={form.control}
                    render={({ field }) => (
                      <FormItem>
                        <Input
                          {...field}
                          placeholder={'1,000,000'}
                          className={cn(
                            'h-7 rounded-sm border !bg-[#17181D] pl-2 !text-[0.6rem] font-normal leading-tight shadow-sm placeholder:!text-[0.6rem] focus:border-sky-400 focus:shadow-[0_0_0_1px_rgba(56,189,248,0.6)] focus:outline-none focus:ring-0 focus-visible:ring-0 focus-visible:ring-offset-0',
                            field.value !== null && field.value !== ''
                              ? 'border-sky-400'
                              : 'border-white/10'
                          )}
                          value={formatNumberWithCommas(field.value)}
                          onChange={(e) => {
                            const value = e.target.value.replace(/,/g, '')
                            if (
                              value === '' ||
                              value === null ||
                              !isNaN(Number(value))
                            ) {
                              field.onChange(
                                value === '' || value === null
                                  ? null
                                  : Number(value)
                              )
                            }
                          }}
                        />
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>
              <div className='space-y-1 pt-1.5'>
                <Table>
                  <TableHeader>
                    <TableRow className='border-b-0'>
                      <TableHead className='bg-[#1F2025] px-0 text-left text-[0.7rem] font-semibold text-gray-300'>
                        Options Type
                      </TableHead>
                      <TableHead className='bg-[#1F2025] text-center text-[0.65rem] font-medium text-gray-400'>
                        Show
                      </TableHead>
                      <TableHead className='bg-[#1F2025] text-center text-[0.65rem] font-medium text-gray-400'>
                        Hide
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {['Calls', 'Puts', 'Sweeps', 'Floor', 'Cross'].map(
                      (option, index) => {
                        const key =
                          option.toLowerCase() as keyof FlowFilterRequest

                        return (
                          <TableRow key={index} className='border-b-0'>
                            <TableCell className='w-[70px] px-0 py-1 text-left text-[0.7rem]'>
                              {option}
                            </TableCell>
                            <TableCell className='py-0.5 text-center'>
                              <FormField
                                name={key}
                                control={form.control}
                                render={({ field }) => (
                                  <div className='relative mx-auto h-3.5 w-3.5'>
                                    <Checkbox
                                      className='h-3.5 w-3.5 !transform-none rounded-full !bg-[#17181D] transition-none hover:!scale-100 focus:!scale-100 active:!scale-100 data-[state=checked]:!border-[#28BF94] data-[state=checked]:!bg-[#28BF94] data-[state=checked]:text-[#1E1E1E]'
                                      checked={field.value === true}
                                      onCheckedChange={(checked) =>
                                        handleOptionChange(key, true, checked)
                                      }
                                    />
                                  </div>
                                )}
                              />
                            </TableCell>
                            <TableCell className='py-0.5 text-center'>
                              <FormField
                                name={key}
                                control={form.control}
                                render={({ field }) => (
                                  <div className='relative mx-auto h-3.5 w-3.5'>
                                    <Checkbox
                                      className='h-3.5 w-3.5 !transform-none rounded-full !bg-[#17181D] transition-none hover:!scale-100 focus:!scale-100 active:!scale-100 data-[state=checked]:!border-[#28BF94] data-[state=checked]:!bg-[#28BF94] data-[state=checked]:text-[#1E1E1E]'
                                      checked={field.value === false}
                                      onCheckedChange={(checked) =>
                                        handleOptionChange(key, false, checked)
                                      }
                                    />
                                  </div>
                                )}
                              />
                            </TableCell>
                          </TableRow>
                        )
                      }
                    )}
                  </TableBody>
                </Table>
              </div>
              <Separator className='my-2 h-px bg-[#424242]' />
              <div className='space-y-1'>
                <Table>
                  <TableHeader>
                    <TableRow className='border-b-0'>
                      <TableHead className='bg-[#1F2025] px-0 text-left text-[0.7rem] font-semibold text-gray-300'>
                        Side
                      </TableHead>
                      <TableHead className='bg-[#1F2025] text-center text-[0.65rem] font-medium text-gray-400'>
                        Show
                      </TableHead>
                      <TableHead className='bg-[#1F2025] text-center text-[0.65rem] font-medium text-gray-400'>
                        Hide
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {[
                      'Bid',
                      'Mid',
                      'Ask',
                      'No Side',
                      'Above Ask',
                      'Below Bid',
                    ].map((option, index) => {
                      const key = option
                        .split(' ')
                        .join()
                        .toLowerCase()
                        .replace(/,\s*([a-z])/g, (letter) =>
                          letter.toUpperCase()
                        )
                        .replace(/,/g, '') as keyof FlowFilterRequest

                      return (
                        <TableRow key={index} className='border-b-0'>
                          <TableCell className='w-[70px] px-0 py-1 text-left text-[0.7rem]'>
                            {option}
                          </TableCell>
                          <TableCell className='py-0.5 text-center'>
                            <FormField
                              name={key}
                              control={form.control}
                              render={({ field }) => (
                                <div className='relative mx-auto h-3.5 w-3.5'>
                                  <Checkbox
                                    key={index}
                                    className='h-3.5 w-3.5 !transform-none rounded-full !bg-[#17181D] transition-none hover:!scale-100 focus:!scale-100 active:!scale-100 data-[state=checked]:!border-[#28BF94] data-[state=checked]:!bg-[#28BF94] data-[state=checked]:text-[#1E1E1E]'
                                    checked={field.value === true}
                                    onCheckedChange={(checked) =>
                                      handleOptionChange(key, true, checked)
                                    }
                                  />
                                </div>
                              )}
                            />
                          </TableCell>
                          <TableCell className='py-0.5 text-center'>
                            <FormField
                              name={key}
                              control={form.control}
                              render={({ field }) => (
                                <div className='relative mx-auto h-3.5 w-3.5'>
                                  <Checkbox
                                    key={index}
                                    className='h-3.5 w-3.5 !transform-none rounded-full !bg-[#17181D] transition-none hover:!scale-100 focus:!scale-100 active:!scale-100 data-[state=checked]:!border-[#28BF94] data-[state=checked]:!bg-[#28BF94] data-[state=checked]:text-[#1E1E1E]'
                                    checked={field.value === false}
                                    onCheckedChange={(checked) =>
                                      handleOptionChange(key, false, checked)
                                    }
                                  />
                                </div>
                              )}
                            />
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
              <Separator className='my-2 h-px bg-[#424242]' />
              <div className='space-y-1'>
                <Table>
                  <TableHeader>
                    <TableRow className='border-b-0'>
                      <TableHead className='bg-[#1F2025] px-0 text-left text-[0.7rem] font-semibold text-gray-300'>
                        Other Filters
                      </TableHead>

                      <TableHead className='bg-[#1F2025] text-center text-[0.65rem] font-medium text-gray-400'>
                        {''}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {[
                      'Single Leg Only',
                      'Volume > OI',
                      'Size > OI',
                      'Opening Trade',
                      'Exclude deep ITM',
                      'OTM Only',
                      'ITM Only',
                      'Multileg Only',
                      'Singleleg Only',
                      'Aggregated Trades',
                      'Hide Expired',
                    ].map((option, index) => {
                      const key = option
                        .split(' ')
                        .join()
                        .toLowerCase()
                        .replace(/,\s*([a-z])/g, (letter) =>
                          letter.toUpperCase()
                        )
                        .replace(/,/g, '') as keyof FlowFilterRequest

                      return (
                        <TableRow key={index} className='border-b-0'>
                          <TableCell className='w-[120px] px-0 py-1 text-left text-[0.7rem]'>
                            {option}
                          </TableCell>

                          <TableCell className='py-0.5 pl-5 text-center'>
                            <FormField
                              name={key}
                              control={form.control}
                              render={({ field }) => (
                                <Switch
                                  key={index}
                                  className='scale-[0.8]'
                                  checked={field.value === true}
                                  onCheckedChange={(checked) => {
                                    handleOtherFilterOptionSwitch(key, checked)
                                  }}
                                />
                              )}
                            />
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </div>
            </div>
            {/* Buttons section - not sticky */}
            <div className='flex items-center justify-between gap-2 px-4 pt-3'>
              <Button
                className='h-7 flex-1 rounded-md text-[0.7rem] font-medium shadow-lg shadow-black/30'
                variant='gradient'
                type='submit'
              >
                Apply
              </Button>
              {selectedPreset ? (
                <Button
                  type='button'
                  className='h-7 flex-1 rounded-md text-[0.7rem] font-medium shadow-lg shadow-black/30'
                  variant='gradient'
                  onClick={handleUpdateFilter}
                >
                  Update
                </Button>
              ) : (
                <SaveFilterPopUp onSaveFilter={handleSaveFilter}>
                  <Button
                    className='h-7 flex-1 rounded-md text-[0.7rem] font-medium shadow-lg shadow-black/30'
                    variant='gradient'
                  >
                    Save
                  </Button>
                </SaveFilterPopUp>
              )}
            </div>
          </form>
        </Form>
      </div>
    </div>
  )
}
