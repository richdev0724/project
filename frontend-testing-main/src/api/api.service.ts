// import { tokenStore } from '@/store/token'
import { APIError } from './api.error'

type RequestBody =
  | undefined
  | Record<string, unknown>
  | FormData
  | Record<string, unknown>[]

interface RequestOptions {
  headers?: Record<string, string>
  params?: Record<string, string | undefined>
}

class ApiService {
  private baseUrl: string

  constructor() {
    // Use the proxy configuration instead of direct URL
    this.baseUrl = '/api'
    // API Base URL initialized
  }

  private async request<T>(
    endpoint: string,
    method: string = 'GET',
    body: RequestBody = undefined,
    options: RequestOptions = {}
  ): Promise<T> {
    // Remove any leading slash from endpoint and handle base URL trailing slash
    const cleanEndpoint = endpoint.startsWith('/')
      ? endpoint.slice(1)
      : endpoint
    const baseUrl = this.baseUrl.endsWith('/')
      ? this.baseUrl.slice(0, -1)
      : this.baseUrl
    let url = `${baseUrl}/${cleanEndpoint}`

    // Add query parameters if provided
    if (options.params) {
      const params = new URLSearchParams()
      Object.entries(options.params).forEach(([key, value]) => {
        if (value !== undefined) {
          params.append(key, value)
        }
      })
      const queryString = params.toString()
      if (queryString) {
        url += `?${queryString}`
      }
    }

    // Making request to constructed URL

    const mainHeader = new Headers(options.headers || {})
    const requestOptions: RequestInit = {
      method,
      headers: mainHeader,
    }

    mainHeader.set('Content-Type', `application/json`)

    if (body) {
      if (body instanceof FormData) {
        requestOptions.body = body
        mainHeader.set('Content-Type', `multipart/form-data`)
      } else {
        requestOptions.body = JSON.stringify(body)
      }
    }

    requestOptions.headers = mainHeader
    const response = await fetch(url, requestOptions)

    if (
      response.status === 404 &&
      (response.url.includes('login') ||
        response.url.includes('reset-password-otp'))
    ) {
      return { status: 404 } as T
    }

    if (response.ok && response.status === 204) {
      return { status: 204 } as T
    }

    const data = await response.json()

    if (!response.ok) {
      throw new APIError(
        data?.message || 'Something went wrong!!',
        response.status,
        data?.details || {},
        data?.data || {}
      )
    }

    return data
  }

  get<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>(endpoint, 'GET', undefined, options)
  }

  post<T>(
    endpoint: string,
    body: RequestBody,
    options: RequestOptions = {}
  ): Promise<T> {
    return this.request<T>(endpoint, 'POST', body, options)
  }

  put<T>(
    endpoint: string,
    body: RequestBody,
    options: RequestOptions = {}
  ): Promise<T> {
    return this.request<T>(endpoint, 'PUT', body, options)
  }

  patch<T>(
    endpoint: string,
    body: RequestBody,
    options: RequestOptions = {}
  ): Promise<T> {
    return this.request<T>(endpoint, 'PATCH', body, options)
  }

  delete<T>(endpoint: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>(endpoint, 'DELETE', undefined, options)
  }
}

export const apiService = new ApiService()
