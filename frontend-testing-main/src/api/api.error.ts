export class APIError extends Error {
  public statusCode: number
  public details: any
  public data: any

  constructor(message: string, statusCode: number, details?: any, data?: any) {
    super(message)
    this.name = 'APIError'
    this.statusCode = statusCode
    this.details = details
    this.data = data

    // Set the prototype explicitly.
    Object.setPrototypeOf(this, APIError.prototype)
  }
}
