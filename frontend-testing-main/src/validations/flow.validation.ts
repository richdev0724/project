import { z } from 'zod'

export const flowFilterSchema = z.object({
  tickers: z.string().nullable().optional(),
  ignoreTickers: z.string().nullable().optional(),
  marketCap: z.array(z.string()).nullable().optional(),
  startDate: z.string().nullable().optional(),
  endDate: z.string().nullable().optional(),
  expiration: z.array(z.string()).nullable().optional(),
  dteMin: z.number().nullable().optional(),
  dteMax: z.number().nullable().optional(),
  minimumPremium: z.number().nullable().optional(),
  maximumPremium: z
    .number()
    .max(2500000, { message: 'Premium Can Not be greater than 2500000' })
    .nullable()
    .optional(),
  minimumSize: z.number().nullable().optional(),
  maximumSize: z
    .number()
    .max(2500000, { message: 'Size Can Not be greater than 2500000' })
    .nullable()
    .optional(),
  minimumVolume: z.number().nullable().optional(),
  maximumVolume: z.number().nullable().optional(),
  calls: z.boolean().nullable().optional(),
  puts: z.boolean().nullable().optional(),
  sweeps: z.boolean().nullable().optional(),
  floor: z.boolean().nullable().optional(),
  cross: z.boolean().nullable().optional(),
  bid: z.boolean().nullable().optional(),
  mid: z.boolean().nullable().optional(),
  ask: z.boolean().nullable().optional(),
  noSide: z.boolean().nullable().optional(),
  aboveAsk: z.boolean().nullable().optional(),
  belowBid: z.boolean().nullable().optional(),
  singleLegOnly: z.boolean().nullable().optional(),
  'volume>Oi': z.boolean().nullable().optional(),
  'size>Oi': z.boolean().nullable().optional(),
  openingTrade: z.boolean().nullable().optional(),
  excludeDeepItm: z.boolean().nullable().optional(),
  otmOnly: z.boolean().nullable().optional(),
  itmOnly: z.boolean().nullable().optional(),
  multilegOnly: z.boolean().nullable().optional(),
  singlelegOnly: z.boolean().nullable().optional(),
  aggregatedTrades: z.boolean().nullable().optional(),
  hideExpired: z.boolean().nullable().optional(),
})

export type FlowFilterRequest = z.infer<typeof flowFilterSchema>
