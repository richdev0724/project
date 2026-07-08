import { authService } from '@/api'
import { useMutation } from '@tanstack/react-query'
import { useAuth } from './use-auth'
import { useNavigate } from 'react-router-dom'
import { Routes } from '@/utilities/routes'

export const useLoginUsingTwitter = () => {
  const { login } = useAuth()
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: async (token: string) => authService.loginWithToken({ token }),
    onSuccess: (response) => {
      login(response)
      navigate(Routes.DASHBOARD)
    },
  })
  return {
    mutation,
  }
}
