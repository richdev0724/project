import { authService } from '@/api'
import { toast } from '@/components/ui/use-toast'
import { EmailValueAtom, showStepTwoAtom } from '@/store/store'
import {
  EmailRegisterRequest,
  SignupRequest,
  emailRegisterSchema,
} from '@/validations/auth.validation'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useAtom } from 'jotai'
import { useState } from 'react'
import { useForm } from 'react-hook-form'

type UserRegisterReturnType = {
  isLoading: boolean
  form: any
  onSubmit: (data: SignupRequest) => void
  isFormValid: boolean
  handleResendEmailVerificationLink: () => void
}

// Define type for API request
interface EmailRegisterApiRequest {
  email: string
}

/**
 *
 * useRegisterForm hook is used to handle register form and its validation.
 * @returns
 */
export const useEmailRegisterForm = (): UserRegisterReturnType => {
  const [isLoading, setIsLoading] = useState(false)
  const [, setShowStepTwo] = useAtom<boolean>(showStepTwoAtom)
  const [emailValue, setEmailValue] = useAtom(EmailValueAtom)
  const form = useForm<EmailRegisterRequest>({
    resolver: zodResolver(emailRegisterSchema),
    defaultValues: {
      email: '',
    },
    mode: 'onChange',
  })
  const isFormValid = form.formState.isValid

  const mutation = useMutation({
    mutationFn: (formData: EmailRegisterRequest) => {
      const apiData: EmailRegisterApiRequest = {
        email: formData.email,
      }
      return authService.emailRegister(apiData)
    },
    onSuccess: () => {
      setShowStepTwo(true)
      setIsLoading(false)
    },
    onError: (error) => {
      setIsLoading(false)
      toast({
        title: error?.message,
      })
    },
  })

  const onSubmit = async (data: EmailRegisterRequest) => {
    setEmailValue(form.getValues('email'))
    setIsLoading(true)
    mutation.mutateAsync(data)
  }

  const handleResendEmailVerificationLink = async () => {
    mutation.mutateAsync({
      email: emailValue,
    })
  }

  return {
    isLoading,
    form,
    onSubmit,
    isFormValid,
    handleResendEmailVerificationLink,
  }
}
