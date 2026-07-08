/* eslint-disable no-shadow */
import React, { PureComponent } from 'react'
import { PieChart, Pie, Cell } from 'recharts'

interface DataItem {
  name: string
  value: number
  color: string
}

interface GreedMeterProps {
  pcrValue?: number
}

const RADIAN = Math.PI / 180
const data: DataItem[] = [
  { name: 'gauge', value: 100, color: 'url(#continuousGradient)' }, // Single continuous gradient
]
const cx = 70
const cy = 70
const iR = 35
const oR = 50

// Function to convert PCR value to gauge position (0-100)
// PCR ranges: 0-2.0, Gauge ranges: 0-100
// Lower PCR (bullish) = lower gauge position (left side)
// Higher PCR (bearish) = higher gauge position (right side)
const pcrToGaugePosition = (pcr: number): number => {
  // Clamp PCR between 0 and 2.0 for safety
  const clampedPcr = Math.max(0, Math.min(2.0, pcr))
  // Convert to 0-100 scale where:
  // PCR 0.0 = 0% (far left - most bullish)
  // PCR 1.0 = 50% (middle - neutral)
  // PCR 2.0 = 100% (far right - most bearish)
  return (clampedPcr / 2.0) * 100
}

const needle = (
  value: number,
  data: DataItem[],
  cx: number,
  cy: number,
  iR: number,
  oR: number,
  color: string
): React.ReactNode[] => {
  let total = 0
  data.forEach((v) => {
    total += v.value
  })
  const ang = 180.0 * (1 - value / total)
  const length = (iR + 1.2 * oR) / 3
  const sin = Math.sin(-RADIAN * ang)
  const cos = Math.cos(-RADIAN * ang)
  const r = 4
  const x0 = cx + 5
  const y0 = cy + 5
  const xba = x0 + r * sin
  const yba = y0 - r * cos
  const xbb = x0 - r * sin
  const ybb = y0 + r * cos
  const xp = x0 + length * cos
  const yp = y0 + length * sin

  return [
    <circle key='circle' cx={x0} cy={y0} r={r} fill={color} stroke='none' />,
    <path
      key='path'
      d={`M${xba} ${yba}L${xbb} ${ybb} L${xp} ${yp} L${xba} ${yba}`}
      stroke='none'
      fill={color}
    />,
  ]
}

export default class GreedMeter extends PureComponent<GreedMeterProps> {
  render(): JSX.Element {
    const { pcrValue = 0.85 } = this.props
    const gaugeValue = pcrToGaugePosition(pcrValue)

    return (
      <PieChart className='-mt-5' width={140} height={110}>
        {/* Define the continuous gradient */}
        <defs>
          <linearGradient
            id='continuousGradient'
            x1='0%'
            y1='0%'
            x2='100%'
            y2='0%'
          >
            <stop offset='0%' stopColor='#24D438' /> {/* Green - Bullish */}
            <stop offset='100%' stopColor='#DC4034' /> {/* Red - Bearish */}
          </linearGradient>
        </defs>

        <Pie
          dataKey='value'
          startAngle={225}
          endAngle={-45}
          data={data}
          cx={cx}
          cy={cy}
          innerRadius={iR}
          outerRadius={oR}
          stroke='none'
          paddingAngle={0}
          cornerRadius={2}
        >
          {data.map((entry, index) => (
            <Cell key={`cell-${index}`} fill={entry.color} />
          ))}
        </Pie>
        {needle(gaugeValue, data, cx, cy, iR, oR, '#FFFFFF')}
      </PieChart>
    )
  }
}
