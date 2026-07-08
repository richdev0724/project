import LogoIcon from '@/icons/LogoIcon'
import { Link } from 'react-router-dom'

export default function RefundPolicy() {
  return (
    <div className='min-h-screen overflow-y-auto bg-gradientRadial bg-cover bg-center bg-no-repeat'>
      <div className='flex min-h-screen items-center justify-center p-4'>
        <div className='w-full max-w-2xl rounded-xl bg-slate-900/70 shadow-2xl backdrop-blur-md'>
          <div className='p-8 text-gray-200'>
            <div className='mb-6 flex items-center space-x-4'>
              <Link to='/#'>
                <LogoIcon className='h-28 w-28 fill-primary' />
              </Link>
              <div>
                <h1 className='text-4xl font-bold text-white'>Refund Policy</h1>
                <p className='text-sm text-gray-300'>
                  Last updated: 06/10/2024
                </p>
              </div>
            </div>

            <div className='mb-6 h-0.5 w-full bg-sky-500'></div>

            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>
                General Policy
              </h1>
              <p className='mb-4 text-lg font-normal'>
                MarketMono LLC provides non-tangible, irrevocable digital goods
                and services. As such, we do not issue refunds once the order is
                completed and the product is delivered. By purchasing a
                subscription or any other service from MarketMono LLC, you
                acknowledge and agree to this refund policy.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>
                Subscription Payments
              </h1>
              <p className='mb-4 text-lg font-normal'>
                All subscription payments are final and non-refundable. This
                includes, but is not limited to, monthly, annual, and lifetime
                subscriptions. If you do not wish to renew your subscription,
                you must cancel it at least 24 hours prior to the renewal date
                to avoid being charged for the next billing cycle.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>Trial Periods</h1>
              <p className='mb-4 text-lg font-normal'>
                If you are under a trial period, you will be charged at the end
                of your trial unless you cancel before the trial period ends.
                Once the trial payment has been charged, it is non-refundable.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>Auto-Renewal</h1>
              <p className='mb-4 text-lg font-normal'>
                All subscription plans are set to auto-renew by default. If you
                do not wish to continue your subscription, you must cancel the
                auto-renewal at least 24 hours before the renewal date.
                Instructions for canceling auto-renewal can be found on the
                billing page of your account settings.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>
                Prorated Refunds
              </h1>
              <p className='mb-4 text-lg font-normal'>
                In the event of a subscription upgrade, a prorated amount based
                on the unused portion of the initial subscription will be issued
                as a credit towards the next renewal. This prorated amount can
                instead be refunded to your card upon request.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>
                Duplicate Subscriptions
              </h1>
              <p className='mb-4 text-lg font-normal'>
                Please ensure that you do not click the subscription button more
                than once to avoid multiple subscriptions and payments.
                MarketMono LLC will not be held liable for duplicate
                subscriptions caused by user error. It is the responsibility of
                the user to manage their subscription accordingly.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>
                Abuse of Payment Systems
              </h1>
              <p className='mb-4 text-lg font-normal'>
                You are entitled to one free trial period. If it is found that
                you are abusing the free trial system, your card will be charged
                immediately, and you will be converted to a full subscription.
                Any abuse of the payment system will result in immediate
                termination of your account without a refund.
              </p>
            </div>
            <div className='mt-4'>
              <h1 className='text-2xl font-bold text-sky-500'>
                Contact Information
              </h1>
              <p className='mb-4 text-lg font-normal'>
                For any billing and subscription inquiries, please contact our
                support team at support@marketmono.com. Please provide as much
                information as possible so that we can promptly assist you.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
