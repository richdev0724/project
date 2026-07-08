import LogoText from '@/components/logo-text'
import { Link } from 'react-router-dom'

export default function PrivacyPolicy() {
  return (
    <div className='min-h-screen overflow-y-auto bg-gradientRadial bg-cover bg-center bg-no-repeat'>
      <div className='flex min-h-screen items-center justify-center p-4'>
        <div
          className='w-full max-w-2xl rounded-xl border border-white/10 bg-[#1F2026]/40 shadow-2xl backdrop-blur-xl'
          style={{
            background:
              'linear-gradient(135deg, rgba(31, 32, 38, 0.4) 0%, rgba(31, 32, 38, 0.2) 100%)',
            boxShadow:
              '0 20px 50px -12px rgba(0, 0, 0, 0.5), 0 8px 32px 0 rgba(0, 0, 0, 0.37), inset 0 1px 0 0 rgba(255, 255, 255, 0.05)',
          }}
        >
          <div className='p-6 text-[#F5F7F7]/80'>
            <div className='mb-5 flex items-center space-x-3'>
              <Link to='/#'>
                <LogoText size='sm' />
              </Link>
              <div>
                <h1 className='text-2xl font-bold text-[#F5F7F7]'>
                  Privacy Policy
                </h1>
                <p className='text-xs text-[#F5F7F7]/70'>
                  Last updated: 06/10/2024
                </p>
              </div>
            </div>

            <div className='mb-5 h-px w-full bg-sky-500/50'></div>

            <div className='mt-3'>
              <p className='mb-3 text-sm font-normal'>
                At MarketMono LLC ("we", "us", or "MarketMono"), we take your
                privacy very seriously. This Privacy Policy explains how we
                collect, use, disclose, and safeguard your personal information
                when you visit our website at marketmono.app and use our
                services. Please read this Privacy Policy carefully.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Information We Collect
              </h1>
              <p className='mb-3 text-sm font-normal'>
                We may collect personal information that you provide directly to
                us, such as:
              </p>
              <ul className='list-inside list-disc text-sm'>
                <li>
                  User account data (name, email, password) when creating an
                  account
                </li>
                <li>Payment and billing information</li>
                <li>
                  Additional data provided through customer support, surveys,
                  etc.
                </li>
              </ul>
              <p className='mb-3 mt-3 text-sm font-normal'>
                We may also automatically collect certain information when you
                use our website and services, such as:
              </p>
              <ul className='list-inside list-disc text-sm'>
                <li>
                  Device information (IP address, browser type, operating
                  system, etc.)
                </li>
                <li>
                  Usage data (pages visited, features used, actions taken,
                  dates/times, etc.)
                </li>
              </ul>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                How We Use Your Information
              </h1>
              <p className='mb-3 text-sm font-normal'>
                We use the information we collect to:
              </p>
              <ul className='list-inside list-disc text-sm'>
                <li>
                  Provide, maintain, and improve our website and services.
                </li>
                <li>Process transactions and subscriptions.</li>
                <li>
                  Send updates, promotional emails, and other communications.
                </li>
                <li>Comply with legal obligations.</li>
              </ul>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Sharing Your Information
              </h1>
              <p className='mb-3 text-sm font-normal'>
                We do not sell your personal information. We may share it with:
              </p>
              <ul className='list-inside list-disc text-sm'>
                <li>
                  Service providers that assist in operating our website and
                  services.
                </li>
                <li>
                  Compliance with laws, regulations, court orders, or other
                  legal processes.
                </li>
                <li>Protect against fraud or illegal activities.</li>
                <li>Corporate transactions (mergers, acquisitions, etc.).</li>
              </ul>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Data Security
              </h1>
              <p className='mb-3 text-sm font-normal'>
                We implement reasonable security measures to protect your
                personal information from unauthorized access or disclosure.
                However, no method of transmission over the Internet or storage
                is 100% secure.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>Your Rights</h1>
              <p className='mb-3 text-sm font-normal'>
                You may have certain rights under applicable data protection
                laws regarding your personal information, such as rights to
                access, correction, deletion, objection, restriction, and data
                portability. Please contact us at support@marketmono.com to
                exercise these rights.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Children's Privacy
              </h1>
              <p className='mb-3 text-sm font-normal'>
                Our services are not intended for children under 13 years old.
                We do not knowingly collect personal information from children.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>
                Changes to this Policy
              </h1>
              <p className='mb-3 text-sm font-normal'>
                We may update this Privacy Policy from time to time by posting
                the revised version on our website. Please review periodically
                for any changes.
              </p>
            </div>
            <div className='mt-3'>
              <h1 className='text-lg font-bold text-[#F5F7F7]'>Contact Us</h1>
              <p className='mb-3 text-sm font-normal'>
                If you have any questions about this Privacy Policy or our data
                practices, please contact us at support@marketmono.com.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
