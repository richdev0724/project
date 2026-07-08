import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../ui/select'
import { Dispatch, SetStateAction } from 'react'

interface ExpirationProps {
  setExpiration: Dispatch<SetStateAction<string>>
  expiration: string
  expirationDates: string[]
}

const formatExpirationDate = (dateStr: string) => {
  const [year, month, day] = dateStr.split('-')
  // Remove leading zeros and combine in desired format
  return `${parseInt(month)}-${parseInt(day)}-${year}`
}

export const ExpirationSelectDropdown = ({
  setExpiration,
  expiration,
  expirationDates,
}: ExpirationProps) => {
  const zeroDTE = expirationDates[0] // First date is usually the closest expiration

  const handleZeroDTE = () => {
    if (zeroDTE) {
      setExpiration(zeroDTE)
    }
  }

  return (
    <div className='flex justify-end'>
      <div>
        <div className='flex items-center gap-4 text-xs font-medium'>
          {zeroDTE && (
            <div>
              <span
                className='inline-block cursor-pointer text-xs text-[#FFFFFF]'
                onClick={handleZeroDTE}
              >
                ODTE
              </span>
              {zeroDTE === expiration && (
                <div className='h-[2px] w-full rounded-b-[1px] bg-[#FFFFFF]'></div>
              )}
            </div>
          )}
          <div className='flex items-center justify-center'>
            <Select
              onValueChange={(expiry) => setExpiration(expiry)}
              value={expiration}
            >
              <SelectTrigger className='w-[140px] !border-none !bg-transparent text-xs font-medium hover:!bg-transparent focus:!bg-transparent focus-visible:!outline-none focus-visible:!ring-0 focus-visible:!ring-offset-0 [&_svg]:!text-white [&_svg]:!opacity-100'>
                <SelectValue
                  placeholder='Select Expiry'
                  className='justify-end px-0 text-xs font-medium'
                >
                  {expiration
                    ? formatExpirationDate(expiration)
                    : 'Select Expiry'}
                </SelectValue>
              </SelectTrigger>
              <SelectContent className='max-h-[280px] overflow-y-auto !border-0 bg-background text-xs font-medium !shadow-none [&>div>*[data-highlighted]]:!bg-transparent [&>div>*[data-state=checked]]:!bg-transparent [&>div>*]:!rounded-none [&>div>*]:!bg-transparent [&>div>*]:hover:!bg-transparent [&>div>*]:focus:!bg-transparent'>
                {[...expirationDates]
                  .sort((a, b) => new Date(a).getTime() - new Date(b).getTime())
                  .map((exp: string) => (
                    <SelectItem key={exp} value={exp}>
                      <span className='inline-block text-xs font-medium text-[#FFFFFF]'>
                        {formatExpirationDate(exp)}
                      </span>
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>
    </div>
  )
}
