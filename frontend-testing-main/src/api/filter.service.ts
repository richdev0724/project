import { apiService } from '.'

class FilterService {
  private api: typeof apiService
  controller: string = 'userfilter'

  constructor() {
    this.api = apiService
  }

  async postUserFilter(data: any) {
    return this.api.post<any>(`${this.controller}`, data)
  }

  async getFilterData() {
    return this.api.get<any>(`${this.controller}`)
  }

  async updateFilterData(id: number, data: any) {
    return this.api.patch<any>(`${this.controller}/${id}`, data)
  }
}

export const filterService = new FilterService()
