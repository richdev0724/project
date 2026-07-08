import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogHeader,
  DialogTrigger,
} from '@/components/ui/dialog'
import FlowContractChart from './charts/flow-contract-chart'
import { DataTable } from './highest-oi-table'
import { columns } from './highest-oi-columns'
import { highestOIcolumns } from './highest-oi-contract'
import { highestOIData, optionsData } from '../data/data'
import { IconCircleFilled, IconX } from '@tabler/icons-react'
import MonoMatrixChart from '../../dashboard/components/mono-matrix-chart'

export default function FlowContractModal({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <Dialog>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent
        removeCloseIcon={true}
        className='flex max-h-[88vh] w-full flex-col overflow-hidden rounded-[8px] p-3 xl:max-w-[1488px]'
      >
        <DialogHeader className='flex flex-shrink-0 flex-row items-center justify-between text-[0.9rem] font-medium'>
          <span>Contract Lookup</span>
          <DialogClose className='!mt-0'>
            <IconX size={18} />
          </DialogClose>
        </DialogHeader>
        <div className='flex-1 space-y-4 overflow-y-auto'>
          <div className='grid grid-cols-2 gap-4'>
            <Card className='rounded-[8px] bg-[linear-gradient(0deg,_#1C1E23_0%,_#181F24_100%)]'>
              <CardHeader className='pl-6 pt-4'>
                <CardTitle className='mb-2 flex items-center gap-8'>
                  <div className='flex gap-2 font-inter text-[0.9rem]'>
                    <span>SPY</span>
                    <span className='font-bold text-[#28BF94]'>650C</span>
                    <span className='font-light'>7/18/25</span>
                  </div>
                  <div className='flex items-center gap-14'>
                    <div className='mb-1 flex items-center gap-4'>
                      <div className='flex items-center gap-1'>
                        <div className='h-3 w-3 rounded-full bg-[#ff3e3e]'></div>
                        <span className='font-inter text-[0.6rem] font-bold '>
                          Bid:{' '}
                        </span>
                        <span className='font-inter text-[0.6rem]  text-primary'>
                          0
                        </span>
                      </div>
                      <div className='flex items-center gap-1'>
                        <div className='h-3 w-3 rounded-full bg-[#00b746]'></div>
                        <span className='font-inter text-[0.6rem] font-bold text-primary'>
                          Mid:
                        </span>
                        <span className='font-inter text-[0.6rem]  text-primary'>
                          0
                        </span>
                      </div>
                      <div className='flex items-center gap-1'>
                        <div className='h-3 w-3 rounded-full bg-[#4a90e2]'></div>
                        <span className='font-inter text-[0.6rem] font-bold text-primary'>
                          Bid :
                        </span>
                        <span className='font-inter text-[0.6rem]  text-primary'>
                          0
                        </span>
                      </div>
                      <div className='flex items-center gap-1'>
                        <div className='h-0.5 w-6 bg-[#f6ad55]'></div>
                        <span className='font-inter text-[0.6rem] font-bold text-primary'>
                          SPY Price
                        </span>
                      </div>
                      <div className='flex items-center gap-1'>
                        <div className='w-6 border-t border-dashed border-[#9f7aea]'></div>
                        <span className='font-inter text-[0.6rem] font-bold text-primary'>
                          Avg Fill
                        </span>
                      </div>
                    </div>
                  </div>
                </CardTitle>
                <div className='flex justify-between font-inter text-[0.6rem] font-light'>
                  <span className='flex gap-2'>
                    DTE:
                    <span className='font-bold'>47</span>
                  </span>
                  <span className='flex gap-2'>
                    Volume:
                    <span className='font-bold text-yellowWarning'>15,000</span>
                  </span>
                  <span className='flex gap-2'>
                    Open In.:
                    <span className='font-bold'>100</span>
                  </span>
                  <span className='flex gap-2'>
                    Avg:
                    <span className='font-bold'>$5.76</span>
                  </span>
                  <span className='flex gap-2'>
                    Prems:
                    <span className='font-bold text-[#B771DD]'>$15.6M</span>
                  </span>
                  <span className='flex gap-2'>
                    Multi:<span className='font-bold'>1%</span>
                  </span>
                  <span className='flex gap-2'>
                    OTM%:<span className='font-bold'>10%</span>
                  </span>
                </div>
              </CardHeader>
              <CardContent className='p-0'>
                <FlowContractChart />
              </CardContent>
            </Card>
            <Card className='rounded-[8px] bg-[linear-gradient(0deg,_#1C1E23_0%,_#181F24_100%)] p-4'>
              <MonoMatrixChart classes='h-[200px]'>
                <h1 className='text-start text-[0.8rem]'>
                  <span className='flex gap-1 font-normal'>
                    <span className='font-semibold'>SPY</span>
                    <span>Matrix</span>
                  </span>
                </h1>
              </MonoMatrixChart>
            </Card>
          </div>
          <div className='flex gap-4'>
            <Card className='w-[60%] gap-0 overflow-hidden rounded-b-none rounded-t-[8px] p-0'>
              <CardHeader className='gap-0 space-y-0 bg-[#1D1E24] pb-0 pl-4 pt-4'>
                <CardTitle className='flex items-center gap-2 text-[0.8rem]'>
                  <span>Historical OI</span>
                  <IconCircleFilled size={6} className='mx-2' />
                  <span>SPY</span>
                  <span className='font-bold text-[#28BF94]'>650C</span>
                  <span className='font-light'>7/18/25 (47d)</span>
                </CardTitle>
              </CardHeader>
              <CardContent className='table-header-with-bg p-0'>
                <DataTable columns={columns} data={optionsData || []} />
              </CardContent>
            </Card>
            <Card className='w-[40%] gap-0 overflow-hidden rounded-b-none rounded-t-[8px] p-0'>
              <CardHeader className='gap-0 space-y-0 bg-[#1D1E24] pb-0 pl-4 pt-4'>
                <CardTitle className='text-[0.8rem]'>
                  SPY Highest OI Contracts
                </CardTitle>
              </CardHeader>
              <CardContent className='table-header-with-bg p-0'>
                <DataTable
                  columns={highestOIcolumns}
                  data={highestOIData || []}
                />
              </CardContent>
            </Card>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
