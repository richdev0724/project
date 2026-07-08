import { apiService } from './api.service'

export interface EconomicEvent {
  time: string
  title: string
  priority: 'LOW' | 'MEDIUM' | 'HIGH'
  forecast?: string | null
  prev?: string | null
  type: string
  rawTime: string
}

export interface EconomicCalendarDay {
  date: string
  dateKey: string
  events: EconomicEvent[]
}

export interface EconomicCalendarResponse {
  success: boolean
  data: EconomicCalendarDay[]
  error?: string
}

class EconomicCalendarService {
  /**
   * Get economic calendar events (no parameters needed - backend handles defaults)
   */
  async getEconomicCalendar(): Promise<EconomicCalendarResponse> {
    const response =
      await apiService.get<EconomicCalendarResponse>('/economic-calendar')
    return response
  }
}

export const economicCalendarService = new EconomicCalendarService()
