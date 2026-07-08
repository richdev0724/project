import { authService } from '@/api'
import { APIError } from '@/api/api.error'
import { useAuth } from '@/hooks/use-auth'
import { Routes } from '@/utilities/routes'
import { LoginRequest, loginSchema } from '@/validations/auth.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'

/**
 * use login form hook is used to handle login form and its validation.
 * @returns
 */

type UseLoginFormReturnType = {
  form: any
  isLoading: boolean
  onSubmit: (data: LoginRequest) => void
  isFormValid: boolean
  passwordRetriesCount: number
}

export const useLoginForm = (): UseLoginFormReturnType => {
  const [isLoading, setIsLoading] = useState(false)
  const [passwordRetriesCount, setPasswordRetriesCount] = useState(0)

  const { login } = useAuth()
  const navigate = useNavigate()

  const form = useForm<LoginRequest>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
    mode: 'onChange',
  })
  const isFormValid = form.formState.isValid

  const mutation = useMutation({
    mutationFn: async (data: LoginRequest) => authService.login(data),
    onSuccess: (response) => {
      if (response.status === 404) {
        navigate(Routes.SIGN_UP, { state: { fromLogin: true } })
      } else {
        setIsLoading(false)
        login(response)
        navigate(Routes.DASHBOARD)
      }
      form.reset()
    },
    onError: (error: APIError) => {
      setPasswordRetriesCount(error.data.failed_login_attempts)
      setIsLoading(false)
    },
  })

  const onSubmit = async (data: LoginRequest) => {
    setIsLoading(true)
    mutation.mutateAsync(data)
  }

  return {
    form,
    isLoading,
    onSubmit,
    isFormValid,
    passwordRetriesCount,
  }
}
