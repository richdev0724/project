import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './sidebar'
import useIsCollapsed from '@/hooks/use-is-collapsed'
import DataTableHeaderShared from './custom/data-table-header-shared'
import HeaderTimeIndicator from '@/app/pages/dashboard/components/header-time-indicator'
import { Routes } from '@/utilities/routes'

const getPageTitle = (pathname: string): string => {
  if (pathname === Routes.DASHBOARD || pathname === '/')
    return 'Market Dashboard'
  if (pathname === Routes.FLOW) return 'Market Flow'
  if (pathname.startsWith('/ticker-overview')) return 'Ticker Overview'
  if (pathname === Routes.WHALE_FLOW) return 'Whale Flow'
  if (pathname === Routes.ALERTS) return 'Alerts'
  if (pathname === Routes.POLITICIANS) return 'Politicians'
  if (pathname === Routes.POLITICIAN_TRADES) return 'Politician Trades'
  if (pathname === Routes.EARNINGS) return 'Calendar'
  if (pathname === Routes.MONO_NEWS) return 'Mono News'
  if (pathname === Routes.SETTINGS) return 'Settings'
  return 'Market Dashboard'
}

export default function AppShell() {
  const [isCollapsed, setIsCollapsed] = useIsCollapsed()
  const location = useLocation()
  const pageTitle = getPageTitle(location.pathname)

  return (
    <div className='relative h-full overflow-hidden bg-background'>
      <Sidebar isCollapsed={isCollapsed} setIsCollapsed={setIsCollapsed} />
      <main
        id='content'
        className={`flex flex-col overflow-hidden transition-all duration-500 ease-in-out md:pt-0 ${isCollapsed ? 'sidebar-collapse md:ml-11' : 'md:ml-[180px]'} h-full`}
      >
        {/* Persistent Header */}
        <div className='z-40 flex flex-shrink-0 items-center justify-between border-b border-[#1F2025] bg-navBarBg'>
          <DataTableHeaderShared
            hasWebsocket={false}
            areFiltersVisible={false}
            title={pageTitle}
          />
          <HeaderTimeIndicator />
        </div>

        <div className='flex-1 overflow-y-auto'>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
