import {
  ForgotPasswordResponse,
  GenerateSignOutResponse,
  LoginResponse,
  OtpVerifyResponse,
  RegisterRequest,
  RegisterResponse,
  ResetPasswordApiRequest,
  UserProfileResponse,
} from '@/models/user.model'
import {
  ForgotPasswordRequest,
  LoginRequest,
  OtpRequest,
  SignOutRequest,
} from '@/validations/auth.validation'
import { apiService } from './api.service'

class AuthService {
  private api: typeof apiService
  controller: string = 'auth'

  constructor() {
    this.api = apiService
  }

  async login(body: LoginRequest) {
    return this.api.post<LoginResponse>(`${this.controller}/login`, body)
  }

  async googleLogin(body: any) {
    return this.api.post<LoginResponse>(
      `${this.controller}/social-media-login-register`,
      body
    )
  }

  async loginWithToken(body: any) {
    return this.api.post<LoginResponse>(
      `${this.controller}/login-with-token`,
      body
    )
  }

  async emailRegister(body: any) {
    return this.api.post<any>(`${this.controller}/register-email`, body)
  }

  async signup(body: RegisterRequest) {
    return this.api.post<RegisterResponse>(
      `${this.controller}/update-register-user`,
      body
    )
  }

  async verifyEmail(token: string) {
    return this.api.post<void>(
      `${this.controller}/verify-email?token=${token}`,
      undefined
    )
  }

  async forgetPassword(body: ForgotPasswordRequest) {
    return this.api.post<ForgotPasswordResponse>(
      `${this.controller}/reset-password-otp`,
      body
    )
  }

  async verifyOtp(body: OtpRequest) {
    return this.api.post<OtpVerifyResponse>(
      `${this.controller}/verify-reset-password-otp`,
      body
    )
  }

  async resetPassword(body: ResetPasswordApiRequest, token: string) {
    return this.api.post<LoginResponse>(
      `${this.controller}/reset-password?token=${token}`,
      body
    )
  }

  async getUserInfo(): Promise<UserProfileResponse> {
    return this.api.get<UserProfileResponse>(`${this.controller}/me`)
  }

  async signOut(body: SignOutRequest) {
    return this.api.post<GenerateSignOutResponse>(
      `${this.controller}/logout/`,
      body
    )
  }
}

export const authService = new AuthService()
