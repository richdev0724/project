export default function AuthLeftPart() {
  return (
    <div className='flex w-1/2 items-center justify-center p-10'>
      <div className='space-y-3'>
        <h1
          className='font-bungeeInline text-[2.7rem] tracking-tight text-[#F5F7F7]'
          style={{
            textShadow:
              '0 0 2px rgba(0, 0, 0, 0.9), 0 0 4px rgba(0, 0, 0, 0.7), inset 0 0 2px rgba(0, 0, 0, 0.8)',
            WebkitTextStroke: '1px rgba(0, 0, 0, 0.5)',
          }}
        >
          Market Mono
        </h1>
        <h2 className='font-courierNew text-lg font-light text-[#F5F7F7]/80'>
          Data-Driven Trading Decisions <br /> Made Simple
        </h2>
      </div>
    </div>
  )
}
