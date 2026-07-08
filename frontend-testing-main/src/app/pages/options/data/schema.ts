import { z } from 'zod'

export const topBlockSchema = z.object({
  sip_date_friendly: z.string(),
  price: z.string(),
  symbol: z.string(),
  net_premium: z.string(),
  size: z.number().int(),
  trf_timestamp: z.nullable(z.string()),
  trf_date_friendly: z.nullable(z.string()),
  trf_timestamp_friendly: z.nullable(z.string()),
})

export type TopBlock = z.infer<typeof topBlockSchema>
