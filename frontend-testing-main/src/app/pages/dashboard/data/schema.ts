import { z } from 'zod'

export const blockSchema = z.object({
  id: z.string(),
  symbol: z.string(),
  unique_trade_id: z.string(),
  size: z.number(),
  price: z.string(),
  participant_timestamp: z.string(),
  participant_timestamp_friendly: z.string(),
  trf_id: z.number(),
  trf_timestamp: z.number(),
  trf_timestamp_friendly: z.string(),
  exchange_id: z.number(),

  // Using z.any() for fields with potentially dynamic types
  bid: z.any().optional(),
  ask: z.any().optional(),

  net_premium: z.string(),

  sip_timestamp: z.any().optional(),
  sip_timestamp_friendly: z.string().optional(),
  tape_trade_id: z.any().optional(),
  backtest_at: z.any().optional(),

  sip_date_friendly: z.string(),
  trf_date_friendly: z.string(),

  conditions: z.any().optional(),
  conditions_raw: z.any().optional(),

  signature_print: z.number(),

  // Boolean-like number fields (0 or 1)
  is_bullish: z.number().min(0).max(1),
  is_bearish: z.number().min(0).max(1),
  is_ssr: z.number().min(0).max(1),
  is_out_of_sequence: z.number().min(0).max(1),
  is_extended_hours: z.number().min(0).max(1),
  is_held: z.number().min(0).max(1),
  is_next_day: z.number().min(0).max(1),
  is_form_t: z.number().min(0).max(1),
  is_block: z.number().min(0).max(1),
  is_sweep: z.number().min(0).max(1),
  is_derivatively_priced: z.number().min(0).max(1),
  is_average_priced: z.number().min(0).max(1),
  is_contingent: z.number().min(0).max(1),

  previous_close: z.any().optional(),

  tags: z.any().optional(),
  queryable_tags: z.any().optional(),

  pulled_historically: z.number(),

  is_order_imbalance: z.number().min(0).max(1),
  is_bunched: z.number().min(0).max(1),
  is_bunched_sold: z.number().min(0).max(1),
  is_distribution: z.number().min(0).max(1),
  is_127_nyse: z.number().min(0).max(1),
  is_tagged: z.number().min(0).max(1),
  is_delayed_print: z.number().min(0).max(1),
  is_phantom_print: z.number().min(0).max(1),

  tags_count: z.number(),

  sector: z.any().optional(),
  industry_type: z.any().optional(),

  // Percentage fields
  percent_1h: z.any().optional(),
  percent_1d: z.any().optional(),
  percent_1w: z.any().optional(),
  percent_1m: z.any().optional(),

  created_at: z.string(),
  updated_at: z.string(),
})

export type Block = z.infer<typeof blockSchema>
