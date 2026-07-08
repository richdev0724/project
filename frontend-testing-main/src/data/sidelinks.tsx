import AlertIcon from '@/assets/icons/alertIcon'
import CalendarIcon from '@/assets/icons/calendarIcon'
import DashboardIcon from '@/assets/icons/dashboardIcon'
import FlowIcon from '@/assets/icons/flowIcon'
import NewsIcon from '@/assets/icons/newsIcon'
import PoliticianTradesIcon from '@/assets/icons/politicianTradesIcon'
// import SignalsIcon from '@/assets/icons/signalsIcon'
import TickerOverViewIcon from '@/assets/icons/tickerOverViewIcon'
import WhaleIcon from '@/assets/icons/whaleIcon'

export interface NavLink {
  title: string
  label?: string
  href: string
  icon: JSX.Element
}

export interface SideLink extends NavLink {
  sub?: NavLink[]
}

export const sidelinks: SideLink[] = [
  {
    title: 'Dashboard',
    label: '',
    href: '/',
    icon: <DashboardIcon />,
  },
  {
    title: 'Flow',
    label: '',
    href: '/flow',
    icon: <FlowIcon />,
  },
  {
    title: 'Ticker Overview',
    label: '',
    href: '/ticker-overview/META', //This is temporary redirecting directly to META ticker overview.
    // href: '/ticker-overview',
    icon: <TickerOverViewIcon />,
  },
  {
    title: 'Whale Flow',
    label: '',
    href: '/whale-flow',
    icon: <WhaleIcon />,
  },
  {
    title: 'Politicians',
    label: '',
    href: '/politicians',
    icon: <PoliticianTradesIcon />,
  },
  {
    title: 'Alerts',
    label: '',
    href: '/alerts',
    icon: <AlertIcon />,
  },
  // {
  //   title: 'Politician Trades',
  //   label: '',
  //   href: '/politician-trades',
  //   icon: <PoliticianTradesIcon />,
  // },
  {
    title: 'News',
    label: '',
    href: '/mono-news',
    icon: <NewsIcon />,
  },
  {
    title: 'Calendar',
    label: '',
    href: '/calendar',
    icon: <CalendarIcon />,
  },
]
