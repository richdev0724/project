import { Button } from '@/components/custom/button'
import { cn } from '@/lib/utils'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Separator } from '@/components/ui/separator'
import { useState } from 'react'
import { IconFilter, IconX } from '@tabler/icons-react'
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import * as z from 'zod'
import { Switch } from '@/components/ui/switch'
import { Input } from '@/components/ui/input'

interface ChildComponentProps {
  showFilter: boolean
  toggleFilter: () => void
  onFilterChange?: (filters: any) => void
}

const formSchema = z.object({
  symbol: z.string().optional(),
  minPremium: z.string().optional(),
  maxPremium: z.string().optional(),
  minSize: z.string().optional(),
  maxSize: z.string().optional(),
  minPrice: z.string().optional(),
  maxPrice: z.string().optional(),
  duringMarket: z.boolean().default(true),
  preMarket: z.boolean().default(true),
  afterMarket: z.boolean().default(true),
})

export default function WhaleFlowFilter({
  showFilter,
  toggleFilter,
  onFilterChange,
}: ChildComponentProps) {
  const [selectedPreset, setSelectedPreset] = useState('')

  // Define preset configurations
  const presets = {
    large: {
      label: 'Large Trades',
      minPremium: 5000000, // $5M
      maxPremium: null,
      description: 'Over $5M premium',
    },
    medium: {
      label: 'Medium Trades',
      minPremium: 500000, // $500K
      maxPremium: 5000000, // $5M
      description: '$500K - $5M premium',
    },
    small: {
      label: 'Small Trades',
      minPremium: 10000, // Keep minimum whale threshold
      maxPremium: 500000, // $500K
      description: 'Below $500K premium',
    },
  }

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      symbol: '',
      minPremium: '',
      maxPremium: '',
      minSize: '',
      maxSize: '',
      minPrice: '',
      maxPrice: '',
      duringMarket: true,
      preMarket: true,
      afterMarket: true,
    },
  })

  const handleClearAll = () => {
    form.reset()
    setSelectedPreset('')
    // Clear filters in parent component
    if (onFilterChange) {
      onFilterChange({
        minPremium: 10000, // Reset to default whale threshold
        maxPremium: null,
        minSize: null,
        maxSize: null,
        minPrice: null,
        maxPrice: null,
        session: null,
        ticker: null,
        sortBy: 'timestamp', // Default to timestamp sorting for regular trades
      })
    }
  }

  const handlePresetSelect = (presetKey: string) => {
    if (presetKey && presets[presetKey as keyof typeof presets]) {
      const preset = presets[presetKey as keyof typeof presets]
      setSelectedPreset(presetKey)

      // Update form values
      form.setValue('minPremium', preset.minPremium.toString())
      form.setValue(
        'maxPremium',
        preset.maxPremium ? preset.maxPremium.toString() : ''
      )

      // Apply filter immediately with premium sorting
      if (onFilterChange) {
        onFilterChange({
          minPremium: preset.minPremium,
          maxPremium: preset.maxPremium,
          minSize: null,
          maxSize: null,
          minPrice: null,
          maxPrice: null,
          session: null,
          ticker: null,
          sortBy: 'premium', // Ensure trades are sorted by premium for presets
        })
      }
    }
  }

  const handleApplyFilter = (values: z.infer<typeof formSchema>) => {
    // Build session filter based on selected market sessions
    let sessionFilter = null
    const selectedSessions = []
    if (values.duringMarket) selectedSessions.push('Intraday')
    if (values.preMarket) selectedSessions.push('Pre-Market')
    if (values.afterMarket) selectedSessions.push('After-Hours')

    // Apply session filter when specific sessions are selected
    // If all sessions are selected (or none), don't apply filter to show all trades
    if (selectedSessions.length > 0 && selectedSessions.length < 3) {
      sessionFilter = selectedSessions.join(',')
    }

    const filters = {
      minPremium:
        values.minPremium && values.minPremium.trim() !== ''
          ? parseInt(values.minPremium)
          : 10000,
      maxPremium:
        values.maxPremium && values.maxPremium.trim() !== ''
          ? parseInt(values.maxPremium)
          : null,
      minSize:
        values.minSize && values.minSize.trim() !== ''
          ? parseInt(values.minSize)
          : null,
      maxSize:
        values.maxSize && values.maxSize.trim() !== ''
          ? parseInt(values.maxSize)
          : null,
      minPrice:
        values.minPrice && values.minPrice.trim() !== ''
          ? parseFloat(values.minPrice)
          : null,
      maxPrice:
        values.maxPrice && values.maxPrice.trim() !== ''
          ? parseFloat(values.maxPrice)
          : null,
      session: sessionFilter,
      ticker:
        values.symbol && values.symbol.trim() !== ''
          ? values.symbol.trim().toUpperCase()
          : null,
      sortBy: 'timestamp', // Default to timestamp sorting for manual filters too
    }

    if (onFilterChange) {
      onFilterChange(filters)
    }
  }

  return (
    <div>
      <Button
        type='button'
        variant={'ghost'}
        size={'default'}
        className='gap-2 px-0'
        onClick={toggleFilter}
      >
        <IconFilter className='h-4 w-4 font-normal' />
        <span className='text-[0.65rem] font-normal text-primary'>Filters</span>
      </Button>
      <div
        className={cn(
          'fixed -left-[200px] bottom-0 top-[96px] z-30 ml-0 w-[200px] space-y-3 overflow-y-auto border-r border-[#424242] bg-[#1F2025] py-3 transition-all duration-300',
          showFilter && 'left-[48px] z-40'
        )}
      >
        <div className='flex justify-between pl-4 pr-3'>
          <div
            className='flex cursor-pointer items-center gap-2 text-[0.65rem] font-medium text-primary hover:underline'
            onClick={handleClearAll}
          >
            Clear All
          </div>
          <Button variant='ghost' className='h-auto p-0' onClick={toggleFilter}>
            <IconX stroke={2} size={18} />
          </Button>
        </div>
        <div className='grid grid-cols-2 gap-3 px-4'>
          <Form {...form}>
            <form
              onSubmit={form.handleSubmit(handleApplyFilter)}
              className='col-span-2 space-y-2.5'
            >
              <div className='space-y-1'>
                <div className='flex items-center justify-between'>
                  <Label className='block text-[0.65rem] font-normal'>
                    Presets
                  </Label>
                  <span
                    onClick={() => {
                      setSelectedPreset('')
                      handleClearAll()
                    }}
                    className='cursor-pointer text-[0.65rem] hover:underline'
                  >
                    clear
                  </span>
                </div>
                <Select
                  value={selectedPreset}
                  onValueChange={handlePresetSelect}
                >
                  <SelectTrigger className='flex justify-between rounded-sm bg-inputFieldBackground text-[0.65rem]'>
                    <SelectValue placeholder='Select a preset' />
                  </SelectTrigger>
                  <SelectContent className='rounded-sm border border-inputBorderColor bg-inputFieldBackground text-[0.65rem]'>
                    <SelectItem value='large'>Large Trades</SelectItem>
                    <SelectItem value='medium'>Medium Trades</SelectItem>
                    <SelectItem value='small'>Small Trades</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Separator className='my-[1px] h-px bg-[#6B7280]' />

              <div className='space-y-0.5'>
                <Label className='block text-[0.65rem] font-normal'>
                  Symbol
                </Label>
                <FormField
                  control={form.control}
                  name='symbol'
                  render={({ field }) => (
                    <FormItem>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder='AAPL, TSLA, etc.'
                          className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                          value={field.value || ''}
                          onChange={(e) => {
                            const value = e.target.value.toUpperCase()
                            field.onChange(value)
                          }}
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className='space-y-0.5'>
                <Label className='block text-[0.65rem] font-normal'>
                  Premium
                </Label>
                <div className='flex items-center gap-2'>
                  <FormField
                    control={form.control}
                    name='minPremium'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder={'0'}
                            className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                            value={field.value || ''}
                            onChange={(e) => {
                              const value = e.target.value
                              if (value === '' || !isNaN(Number(value))) {
                                field.onChange(value)
                              }
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem]'>{'-'}</span>
                  <FormField
                    control={form.control}
                    name='maxPremium'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder={'∞'}
                            className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                            value={field.value || ''}
                            onChange={(e) => {
                              const value = e.target.value
                              if (value === '' || !isNaN(Number(value))) {
                                field.onChange(value)
                              }
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>

              <div className='space-y-0.5'>
                <Label className='block text-[0.65rem] font-normal'>Size</Label>
                <div className='flex items-center gap-2'>
                  <FormField
                    control={form.control}
                    name='minSize'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder={'0'}
                            className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                            value={field.value || ''}
                            onChange={(e) => {
                              const value = e.target.value
                              if (value === '' || !isNaN(Number(value))) {
                                field.onChange(value)
                              }
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem]'>{'-'}</span>
                  <FormField
                    control={form.control}
                    name='maxSize'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder={'∞'}
                            className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                            value={field.value || ''}
                            onChange={(e) => {
                              const value = e.target.value
                              if (value === '' || !isNaN(Number(value))) {
                                field.onChange(value)
                              }
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>

              <div className='space-y-0.5'>
                <Label className='block text-[0.65rem] font-normal'>
                  Price
                </Label>
                <div className='flex items-center gap-2'>
                  <FormField
                    control={form.control}
                    name='minPrice'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder={'0'}
                            className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                            value={field.value || ''}
                            onChange={(e) => {
                              const value = e.target.value
                              if (value === '' || !isNaN(Number(value))) {
                                field.onChange(value)
                              }
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <span className='text-[0.65rem]'>{'-'}</span>
                  <FormField
                    control={form.control}
                    name='maxPrice'
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <Input
                            {...field}
                            placeholder={'$∞'}
                            className='h-7 rounded-sm bg-inputFieldBackground text-[0.65rem] font-normal focus:outline-none focus:ring-0'
                            value={field.value || ''}
                            onChange={(e) => {
                              const value = e.target.value
                              if (value === '' || !isNaN(Number(value))) {
                                field.onChange(value)
                              }
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </div>
              </div>

              <Separator className='my-[1px] h-px bg-[#6B7280]' />

              <div className='space-y-2'>
                <Label className='block text-[0.65rem] font-normal'>
                  Market Session
                </Label>
                <FormField
                  control={form.control}
                  name='duringMarket'
                  render={({ field }) => (
                    <FormItem className='flex items-center justify-between space-y-0'>
                      <FormLabel className='text-[0.65rem]'>
                        During Market
                      </FormLabel>
                      <FormControl>
                        <Switch
                          checked={field.value}
                          onCheckedChange={field.onChange}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='preMarket'
                  render={({ field }) => (
                    <FormItem className='flex items-center justify-between space-y-0'>
                      <FormLabel className='text-[0.65rem]'>
                        Pre Market
                      </FormLabel>
                      <FormControl>
                        <Switch
                          checked={field.value}
                          onCheckedChange={field.onChange}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name='afterMarket'
                  render={({ field }) => (
                    <FormItem className='flex items-center justify-between space-y-0'>
                      <FormLabel className='text-[0.65rem]'>
                        After Market
                      </FormLabel>
                      <FormControl>
                        <Switch
                          checked={field.value}
                          onCheckedChange={field.onChange}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
              </div>

              <div className='pt-4'>
                <Button type='submit' className='w-full text-[0.65rem]'>
                  Apply Filters
                </Button>
              </div>
            </form>
          </Form>
        </div>
      </div>
    </div>
  )
}
