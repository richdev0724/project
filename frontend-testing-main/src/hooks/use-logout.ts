import { authService } from '@/api'
import { toast } from '@/components/ui/use-toast'
import { Routes } from '@/utilities/routes'
import { SignOutRequest } from '@/validations/auth.validation'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './use-auth'
import { APIError } from '@/api/api.error'

type UseLogoutReturnType = {
  handleLogout: () => void
  isLoading: boolean
}

export const useLogout = (): UseLogoutReturnType => {
  const [isLoading, setIsLoading] = useState<boolean>(false)
  const navigate = useNavigate()
  const { logout } = useAuth()

  const mutation = useMutation({
    mutationFn: async (values: SignOutRequest) => {
      return authService.signOut(values)
    },
    onSuccess: async () => {
      logout()
      navigate(Routes.SIGN_IN)
      setIsLoading(false)
    },
    onError: (error) => {
      if (error instanceof APIError) {
        toast({
          title: error?.message,
          variant: 'destructive',
        })
      } else {
        toast({
          title: 'Something went wrong',
          variant: 'destructive',
        })
      }
    },
  })

  const handleLogout = () => {
    const refreshToken = localStorage.getItem('refreshToken')

    if (!refreshToken) return

    mutation.mutateAsync({
      refreshToken: JSON.parse(refreshToken),
    })
  }

  return {
    handleLogout,
    isLoading,
  }
}
