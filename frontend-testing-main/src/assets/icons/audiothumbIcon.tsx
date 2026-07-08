import { SVGProps } from 'react'

const AudioThumbIcon = (props: SVGProps<SVGSVGElement>) => {
  return (
    <svg
      width='10'
      height='8'
      viewBox='0 0 11 8'
      fill='none'
      xmlns='http://www.w3.org/2000/svg'
      {...props}
    >
      <g filter='url(#filter0_d_1232_168)'>
        <rect x='3' y='2' width='7' height='4' rx='1' fill='#AB9E9E' />

        {/* First dot */}
        <rect x='4.5' y='3' width='1' height='2' rx='0.5' fill='#1A1B20' />

        {/* Second dot */}
        <rect x='6' y='3' width='1' height='2' rx='0.5' fill='#1A1B20' />

        {/* Third dot */}
        <rect x='7.5' y='3' width='1' height='2' rx='0.5' fill='#1A1B20' />
      </g>
      <defs>
        <filter
          id='filter0_d_1232_168'
          x='0'
          y='0'
          width='11'
          height='8'
          filterUnits='userSpaceOnUse'
          colorInterpolationFilters='sRGB'
        >
          <feFlood floodOpacity='0' result='BackgroundImageFix' />
          <feColorMatrix
            in='SourceAlpha'
            type='matrix'
            values='0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0'
            result='hardAlpha'
          />
          <feOffset dx='-1' />
          <feGaussianBlur stdDeviation='1' />
          <feComposite in2='hardAlpha' operator='out' />
          <feColorMatrix
            type='matrix'
            values='0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.54 0'
          />
          <feBlend
            mode='normal'
            in2='BackgroundImageFix'
            result='effect1_dropShadow_1232_168'
          />
          <feBlend
            mode='normal'
            in='SourceGraphic'
            in2='effect1_dropShadow_1232_168'
            result='shape'
          />
        </filter>
      </defs>
    </svg>
  )
}

export default AudioThumbIcon
