import { TableState } from '@/hooks/use-table-state'
import { GenericResponse } from './generic'

export interface User {
  id: string
  first_name: string
  last_name: string
  email: string
  email_verified_at: string
  password: string
  api_token: any
  remember_token: any
  created_at: any
  updated_at: any
  stripe_id: any
  pm_type: any
  pm_last_four: any
  trial_ends_at: any
  sams_bitch: any
  discord_details: any
  discord_token: any
  discord_refresh_token: any
  twitter_id: any
  oauth_type: any
  twitter_data: any
  twitter_refresh_token: any
  beta_access: any
  is_admin: boolean
  is_moderator: boolean
  referral_code: any
  beta_features: any
  sams_shitlist: boolean
  banned_at: any
  membership_type: any
  membership_active: boolean
  is_lifetime: any
  onboarded_at: any
  completed_profile_at: any
  trader_description: any
  phone_number: any
  snaptrade_user_id: any
  snaptrade_user_secret: any
  snaptrade_secret: any
  snaptrade_token: any
  broker: any
}

export type LoginResponse = GenericResponse<{
  user: User
  tokens: Tokens
}>

export type RegisterRequest = {
  first_name: string
  last_name: string
  email: string
  password: string
}

export type OtpVerifyRequest = {
  email: string
  otp: string
}

export type OtpVerifyResponse = GenericResponse<{ resetPasswordToken: string }>

export type ResetPasswordApiRequest = {
  password: string
}

export type RegisterResponse = GenericResponse<{
  user: User
  tokens: Tokens
}>

export type ForgotPasswordResponse = GenericResponse<Record<string, any>>

export type UserProfileResponse = GenericResponse<{
  user: User
  latest_trades_date: number
}>

export interface Tokens {
  access: Access
  refresh: Access
}

export interface TokensResponse {
  tokens: Tokens
}

export interface Access {
  token: string
  expires: string
}

export type GenerateNewTokenResponse = GenericResponse<TokensResponse>

export type UserListRequest = TableState<UserListFilter>

export type UserListResponse = GenericResponse<{
  users: User[]
  count: number
}>
export type UserListFilter = {
  search: string
}

export type UserAddOrUpdateResponse = GenericResponse<User>
export type UserDeletionResponse = Pick<
  GenericResponse<unknown>,
  'status' | 'message'
>

export type GenerateSignOutResponse = GenericResponse<Record<string, never>>
