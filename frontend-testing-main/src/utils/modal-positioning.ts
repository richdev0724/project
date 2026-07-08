// Modal Positioning Utilities
// Use these classes for consistent positioning across all modals

export const MODAL_POSITIONING = {
  // Standard 20px margins (recommended)
  standard: 'absolute top-[15px] left-[-1px]',

  // 16px margins (matches sweep/block orders)
  compact: 'absolute top-[11px] left-[-5px]',

  // 24px margins (larger spacing)
  spacious: 'absolute top-[19px] left-[3px]',

  // 12px margins (tight spacing)
  tight: 'absolute top-[7px] left-[-9px]',

  // 32px margins (very spacious)
  verySpacious: 'absolute top-[27px] left-[7px]',
} as const

// Usage example:
// <div className={MODAL_POSITIONING.standard}>
//   <LogoWithFallback symbol={symbol} />
// </div>

// Quick reference for manual positioning:
// Desired Margin | Top Value | Left Value
// 12px          | top-[7px] | left-[-9px]
// 16px          | top-[11px]| left-[-5px]
// 20px          | top-[15px]| left-[-1px]  ← STANDARD
// 24px          | top-[19px]| left-[3px]
// 32px          | top-[27px]| left-[7px]

// Math explanation:
// Top: [value] + ChartLayout padding (5px) = total margin
// Left: [value] + ChartLayout padding (5px) + LogoWithFallback margin (16px) = total margin
