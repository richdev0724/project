import { authService } from '@/api'
import { Button } from '@/components/custom/button'
import { useAuth } from '@/hooks/use-auth'
import { useGoogleLogin } from '@react-oauth/google'
import { IconBrandGoogleFilled } from '@tabler/icons-react'
// import { useNavigate } from 'react-router-dom'

interface GoogleAuthButtonProps {
  isSignUp?: boolean
}

/**
 * A button for logging in with Google.
 *
 * Handles the Google login flow, redirects to the login page on error.
 *
 * @returns A button element
 */
export default function GoogleAuthButton({
  isSignUp = false,
}: GoogleAuthButtonProps) {
  // const navigate = useNavigate()
  const { login } = useAuth()
  const googleLogin = useGoogleLogin({
    onSuccess: async (codeResponse) => {
      const response = await authService.googleLogin({
        access_token: codeResponse.access_token,
        type: 'google',
      })
      login(response)
    },
    onError: () => {
      // Do nothing on error
    },
  })

  const handleGoogleLoginClick = () => {
    googleLogin()
  }

  return (
    <Button
      type='button'
      size={'lg'}
      className='h-10 w-[311px] rounded-lg bg-[linear-gradient(135deg,_#32343A_0%,_#2F3137_100%)] shadow-lg transition-all hover:scale-105 hover:shadow-xl active:scale-95'
      onClick={handleGoogleLoginClick}
    >
      <div className='flex items-center justify-center gap-2'>
        <IconBrandGoogleFilled className='h-4 w-4 text-[#F5F7F7]' />
        <span className='text-xs font-medium text-[#F5F7F7]'>
          {isSignUp ? 'Sign Up with Google' : 'Continue with Google'}
        </span>
      </div>
    </Button>
  )
}
