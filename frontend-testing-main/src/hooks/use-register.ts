import { authService } from '@/api'
import { toast } from '@/components/ui/use-toast'
import { SignupRequest, signupSchema } from '@/validations/auth.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useAuth } from './use-auth'

type UserRegisterReturnType = {
  isLoading: boolean
  form: any
  onSubmit: (data: SignupRequest) => void
  isFormValid: boolean
}

// Define type for API request
interface SignupApiRequest {
  first_name: string
  last_name: string
  email: string
  password: string
  token: string
}

interface params {
  token: string
}

/**
 * useRegisterForm hook is used to handle register form and its validation.
 * @returns
 */
export const useRegisterForm = ({ token }: params): UserRegisterReturnType => {
  const [isLoading, setIsLoading] = useState(false)
  const { login } = useAuth()
  const form = useForm<SignupRequest>({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      first_name: '',
      last_name: '',
      email: '',
      password: '',
      confirmPassword: '',
      nasdaqAgreement: false,
      opraAgreement: false,
      privacyPolicy: false,
      userAgreement: false,
    },
    mode: 'onChange',
  })
  const isFormValid = form.formState.isValid

  const mutation = useMutation({
    mutationFn: (formData: SignupRequest) => {
      const apiData: SignupApiRequest = {
        first_name: formData.first_name,
        last_name: formData.last_name,
        email: formData.email,
        password: formData.password,
        token,
      }

      return authService.signup(apiData)
    },
    onSuccess: (response) => {
      login(response)
      setIsLoading(false)
      toast({
        title: response.message,
      })
    },
    onError: (error) => {
      setIsLoading(false)
      toast({
        title: error?.message,
      })
    },
  })

  const onSubmit = async (data: SignupRequest) => {
    setIsLoading(true)
    mutation.mutateAsync(data)
  }

  return {
    isLoading,
    form,
    onSubmit,
    isFormValid,
  }
}
