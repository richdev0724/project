export interface TickerRoot {
  ticker: string
  todaysChangePerc: number
  todaysChange: number
  updated: number
  day: Day
  lastQuote: LastQuote
  lastTrade: LastTrade
  fmv: number
  min: Min
  prevDay: PrevDay
}

export interface Day {
  o: number
  h: number
  l: number
  c: number
  v: number
  vw: number
}

export interface LastQuote {
  P: number
  S: number
  p: number
  s: number
  t: number
}

export interface LastTrade {
  i: string
  p: number
  s: number
  t: number
  x: number
}

export interface Min {
  av: number
  t: number
  n: number
  o: number
  h: number
  l: number
  c: number
  v: number
  vw: number
}

export interface PrevDay {
  o: number
  h: number
  l: number
  c: number
  v: number
  vw: number
}

export interface IPayload {
  timespan: string | number | undefined
  latest_trade_date: number | undefined
  sortBy: string | undefined
  ticker: string | undefined
}

export type IDashboardPayload = {
  widget: string
  payload: IPayload | undefined
}
